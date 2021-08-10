package com.accretivetg.ml.esplugin.models.ranking;

import com.accretivetg.ml.esplugin.models.MLModel;
import com.accretivetg.ml.esplugin.MLRescoreContext;
import com.accretivetg.ml.esplugin.StatsD;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.elasticsearch.common.cache.Cache;
import org.elasticsearch.common.cache.CacheBuilder;
import org.elasticsearch.common.unit.TimeValue;

import java.util.List;
import java.util.Map;

public class LRUCacheWrappedRanker implements MLModel {
    private final MLModel ranker;
    private final Cache<String, Float> cache;

    public LRUCacheWrappedRanker(MLModel ranker) {
        this(
                ranker,
                CacheBuilder
                        .<String, Float>builder()
                        .setMaximumWeight(25_000_000)
                        .setExpireAfterWrite(TimeValue.timeValueHours(6))
                        .setExpireAfterAccess(TimeValue.timeValueHours(2))
        );
    }

    LRUCacheWrappedRanker(MLModel ranker, CacheBuilder<String, Float> cacheBuilder) {
        this.ranker = ranker;
        this.cache = cacheBuilder.build();
    }

    @Override
    public StatsD getStatsd() {
        return ranker.getStatsd();
    }

    @Override
    public String getContextHash(MLRescoreContext context) {
        return ranker.getContextHash(context);
    }

    @Override
    public boolean modelIsActive() {
        return this.ranker.modelIsActive();
    }

    @Override
    public Map<String, Float> getScores(MLRescoreContext context, List<String> itemIds) {
        StatsD statsd = getStatsd();
        String contextHash = getContextHash(context);

        Map<String, Float> scores = Maps.newHashMapWithExpectedSize(itemIds.size());
        List<String> itemsThatNeedScoring = Lists.newArrayListWithCapacity(itemIds.size());

        for (String item : itemIds) {
            String cacheKey = contextHash + "/" + item;
            Float cachedScore = cache.get(cacheKey);
            if (cachedScore == null) {
                itemsThatNeedScoring.add(item);
            } else {
                scores.put(item, cachedScore);
            }

        }
        statsd.count("cache.hits", scores.size());
        statsd.count("cache.misses", itemsThatNeedScoring.size());

        if (itemsThatNeedScoring.size() > 0) {
            Map<String, Float> newScores = ranker.getScores(context, itemsThatNeedScoring);

            for (Map.Entry<String, Float> entry : newScores.entrySet()) {
                String newCacheKey = contextHash + "/" + entry.getKey();
                cache.put(newCacheKey, entry.getValue());
                scores.put(entry.getKey(), entry.getValue());
            }
        }

        statsd.gauge("cache.size", cache.count());
        return scores;
    }
}
