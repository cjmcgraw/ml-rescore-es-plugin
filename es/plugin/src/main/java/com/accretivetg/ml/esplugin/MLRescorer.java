package com.accretivetg.ml.esplugin;

import com.accretivetg.ml.esplugin.models.MLModel;
import com.accretivetg.ml.esplugin.models.MLModelFactory;
import com.google.common.collect.Lists;
import com.google.common.primitives.Floats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.index.fielddata.SortedBinaryDocValues;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.rescore.RescoreContext;
import org.elasticsearch.search.rescore.Rescorer;

import java.io.IOException;
import java.util.*;

class MLRescorer implements Rescorer {
    private static final Logger log = LogManager.getLogger(MLRescorer.class);
    private static final KillSwitch killswitch = new KillSwitch(new StatsD(), 0.1f);
    static final MLRescorer INSTANCE = new MLRescorer();

    @Override
    public TopDocs rescore(TopDocs topDocs, IndexSearcher searcher, RescoreContext rescoreContext) throws IOException, ElasticsearchParseException {
        if (killswitch.isActive()) {
            log.error("killswitch is currently active!");
            ElasticsearchStatusException exception = new ElasticsearchStatusException(
                    "Error rate exceeded acceptable limit. Killswitch active on all rescore attempts!",
                    RestStatus.TOO_MANY_REQUESTS
            );
            log.error(exception);
            throw exception;
        }

        TopDocs rescoredDocs;
        try {
            rescoredDocs = innerRescore(topDocs, searcher, rescoreContext);
        } catch (InvalidModelInputException e) {
            throw e;
        } catch (Exception e) {
            killswitch.incrementError();
            log.catching(e);
            throw e;
        }

        killswitch.incrementSuccess();
        return rescoredDocs;
    }

    private TopDocs innerRescore(TopDocs topDocs, IndexSearcher searcher, RescoreContext rescoreContext) throws IOException, ElasticsearchParseException {
        long startRescore = System.currentTimeMillis();
        MLRescoreContext mlContext = (MLRescoreContext) rescoreContext;
        MLModel model = MLModelFactory.getModel(mlContext);
        StatsD s = model.getStatsd();
        s.increment("rescore.attempt");
        s.recordExecutionTime("rescore.get-recommender", System.currentTimeMillis() - startRescore);

        s.count("rescore.window-size", rescoreContext.getWindowSize());

        long startProcess = System.currentTimeMillis();
        List<String> itemIds = retrieveItemIds(mlContext, topDocs, searcher);
        s.recordExecutionTime("rescore.retrieve-items", System.currentTimeMillis() - startProcess);
        s.count("rescore.items", itemIds.size());

        long startScore = System.currentTimeMillis();
        Map<String, Float> scores;
        try {
            scores = model.getScores(mlContext, itemIds);
        } catch (InvalidModelInputException e) {
            s.increment("rescore.invalid");
            throw e;
        } catch (Exception e) {
            s.increment("rescore.failure");
            throw e;
        }

        s.count("rescore.scored-items", scores.size());

        for (int i = 0; i  < itemIds.size(); i++) {
            String itemId = itemIds.get(i);
            if (scores.containsKey(itemId)) {
                float itemScore = scores.get(itemId);
                float mergedItemScore = mlContext.getScoreMode().combine(
                        itemScore,
                        topDocs.scoreDocs[i].score
                );
                topDocs.scoreDocs[i].score = mergedItemScore;
            }
        }
        Arrays.sort(topDocs.scoreDocs, Collections.reverseOrder((a, b) -> Floats.compare(a.score, b.score)));
        s.recordExecutionTime("rescore.score-items", System.currentTimeMillis() - startScore);
        s.recordExecutionTime("rescore.total-time", System.currentTimeMillis() - startRescore);
        s.increment("rescore.success");
        return topDocs;
    }

    @Override
    /** Note that to avoid adding some unnecessary processing in rescore, we cannot use RescoreContext.isRescored
     *  to check if a documentation actually has rescore applied, it will assume every doc has been rescored
     *  regardless of windowSize.  We will want to use a large windowSize with a explain query.
     */
    public Explanation explain(int topLevelDocId, IndexSearcher searcher, RescoreContext rescoreContext, Explanation sourceExplanation) {
        return sourceExplanation;
    }

    private List<String> retrieveItemIds(MLRescoreContext mlContext, TopDocs topDocs, IndexSearcher searcher) throws IOException {
        ScoreDoc[] hits = topDocs.scoreDocs;
        Arrays.sort(hits, Comparator.comparingInt((d) -> d.doc));

        List<LeafReaderContext> readerContexts = searcher.getIndexReader().leaves();
        int currentReaderIx = -1;
        int currentReaderEndDoc = 0;
        LeafReaderContext currentReaderContext = null;

        int windowSize = Math.min(mlContext.getWindowSize(), hits.length);
        if (windowSize <= 0) {
            throw new ElasticsearchException("Cannot score with 0 hits!");
        }
        List<String> itemIds = Lists.newArrayListWithCapacity(windowSize);
        for (int i = 0; i < windowSize; i++) {
            ScoreDoc hit = hits[i];

            // find segment that contains current document
            while (hit.doc >= currentReaderEndDoc) {
                currentReaderIx++;
                currentReaderContext = readerContexts.get(currentReaderIx);
                currentReaderEndDoc = currentReaderContext.docBase + currentReaderContext.reader().maxDoc();
            }

            int docId = hit.doc - currentReaderContext.docBase;
            SortedBinaryDocValues values = mlContext
                    .getItemIdField()
                    .load(currentReaderContext)
                    .getBytesValues();
            values.advanceExact(docId);
            String itemId = values.nextValue().utf8ToString();
            itemIds.add(itemId);
        }
        return itemIds;
    }
}
