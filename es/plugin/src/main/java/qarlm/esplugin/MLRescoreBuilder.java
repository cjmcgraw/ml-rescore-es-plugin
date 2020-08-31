package qarlm.esplugin;

import com.google.common.collect.Sets;
import com.google.common.primitives.Floats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.QueryRewriteContext;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.search.rescore.RescoreContext;
import org.elasticsearch.search.rescore.Rescorer;
import org.elasticsearch.search.rescore.RescorerBuilder;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;

import static org.elasticsearch.common.xcontent.ConstructingObjectParser.constructorArg;
import static org.elasticsearch.common.xcontent.ConstructingObjectParser.optionalConstructorArg;

public class MLRescoreBuilder extends RescorerBuilder<MLRescoreBuilder> {
    private static final Logger log = LogManager.getLogger(MLRescoreBuilder.class);
    public static final String NAME = "mlrescore";

    private final String modelName;
    private final String modelContext;
    private MLRescoreMode scoreMode = MLRescoreMode.Total;
    @Nullable
    private String fallbackScoreField = null;

    public MLRescoreBuilder(String modelName, String modelContext, @Nullable String scoreMode, @Nullable String fallbackScoreField) {
        this.modelName = modelName;
        this.modelContext = modelContext;
        if (scoreMode != null) {
            this.scoreMode = MLRescoreMode.fromString(scoreMode);
        }
        this.fallbackScoreField = fallbackScoreField;
    }


    public MLRescoreBuilder(StreamInput in) throws IOException {
        super(in);
        modelName = in.readString();
        modelContext = in.readString();
        scoreMode = MLRescoreMode.readFromStream(in);
        fallbackScoreField = in.readOptionalString();
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeString(modelName);
        out.writeString(modelContext);
        scoreMode.writeTo(out);
        out.writeOptionalString(fallbackScoreField);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    public RescorerBuilder<MLRescoreBuilder> rewrite(QueryRewriteContext ctx) {
        return this;
    }

    private static final ParseField MODEL_NAME = new ParseField("model_name");
    private static final ParseField CONTEXT = new ParseField("context");
    private static final ParseField FALLBACK_SCORE_FIELD = new ParseField("fallback_score_field");
    private static final ParseField SCORE_MODE = new ParseField("score_mode");
    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.field(MODEL_NAME.getPreferredName(), modelName);
        builder.field(CONTEXT.getPreferredName(), modelContext);
        builder.field(SCORE_MODE.getPreferredName(), scoreMode.name().toLowerCase(Locale.ROOT));
        if (fallbackScoreField != null) {
            builder.field(FALLBACK_SCORE_FIELD.getPreferredName(), fallbackScoreField);
        }
    }

    private static final ConstructingObjectParser<MLRescoreBuilder, Void> PARSER = new ConstructingObjectParser<>(
            NAME, args ->  new MLRescoreBuilder(
                (String) args[0],
                (String) args[1],
                (args.length > 2) ? (String) args[2] : null,
                (args.length > 3) ? (String) args[3] : null
    )); static {
        PARSER.declareString(constructorArg(), MODEL_NAME);
        PARSER.declareString(constructorArg(), CONTEXT);
        PARSER.declareString(optionalConstructorArg(), SCORE_MODE);
        PARSER.declareString(optionalConstructorArg(), FALLBACK_SCORE_FIELD);
    }

    public static MLRescoreBuilder fromXContent(XContentParser parser) {
        return PARSER.apply(parser, null);
    }

    @Override
    public RescoreContext innerBuildContext(int windowSize, QueryShardContext context) throws IOException {
        return new MLRescoreContext(windowSize, modelName, modelContext, scoreMode, fallbackScoreField);
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        MLRescoreBuilder other = (MLRescoreBuilder) obj;
        return (
            Objects.equals(modelName, other.modelName)
            && Objects.equals(modelContext, other.modelContext)
            && Objects.equals(fallbackScoreField, other.fallbackScoreField)
            && Objects.equals(scoreMode, other.scoreMode)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            super.hashCode(),
            modelName,
            modelContext,
            scoreMode,
            fallbackScoreField
        );
    }

    public MLRescoreBuilder setScoreMode(MLRescoreMode scoreMode) {
        this.scoreMode = scoreMode;
        return this;
    }

    public MLRescoreBuilder setScoreMode(String scoreMode) {
        if (scoreMode != null) {
            this.scoreMode = MLRescoreMode.fromString(scoreMode);
        }
        return this;
    }

    String modelName() {
        return modelName;
    }

    String modelContext() {
        return modelContext;
    }

    @Nullable
    String getFallbackScoreField() { return fallbackScoreField; }

    MLRescoreMode getScoreMode() { return scoreMode; }

    private static class MLRescorer implements Rescorer {
        private static final MLRescorer INSTANCE = new MLRescorer();
        private static final Map<String, RecommenderClient> recommenderLookup = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, RecommenderClient> eldest) {
                boolean shouldRemove = size() > 20;
                if (shouldRemove) {
                    log.warn("evicting recommender client because we exceeded 20! key={}", eldest.getKey());
                }
                return shouldRemove;
            }
        };

        @Override
        public TopDocs rescore(TopDocs topDocs, IndexSearcher searcher, RescoreContext rescoreContext) throws IOException{
            MLRescoreContext mlContext = (MLRescoreContext) rescoreContext;
            RecommenderClient recommender = recommenderLookup
                    .computeIfAbsent(mlContext.modelName, RecommenderClient::buildRecommenderClient);

            long[] exampleIds = Arrays.stream(topDocs.scoreDocs)
                    .mapToLong(x -> x.doc)
                    .toArray();

            float[] scores;
            try {
                scores = recommender.score(exampleIds, mlContext.modelContext);
            } catch (Exception e) {
                if (mlContext.fallbackScoreField == null) {
                    throw e;
                }
                scores = new float[exampleIds.length];
                for (int i = 0; i < scores.length; i++) {
                    scores[i] = scoreDocumentWithFallbackScore(mlContext, (int) exampleIds[i], searcher);
                }
            }

            for (int i = 0; i < topDocs.scoreDocs.length; i++) {
                topDocs.scoreDocs[i].score = mlContext.scoreMode.combine(
                        scores[i],
                        topDocs.scoreDocs[i].score
                );
            }

            Arrays.sort(topDocs.scoreDocs, Collections.reverseOrder((a, b) -> Floats.compare(a.score, b.score)));
            return topDocs;
        }

        @Override
        public Explanation explain(int topLevelDocId, IndexSearcher searcher, RescoreContext rescoreContext, Explanation sourceExplanation) {
            return sourceExplanation;
        }

        private float scoreDocumentWithFallbackScore(MLRescoreContext context, int docId, IndexSearcher searcher) {
            try {
                return searcher
                    .doc(docId, Sets.newHashSet(context.fallbackScoreField))
                    .getField(context.fallbackScoreField)
                    .numericValue()
                    .floatValue();
            } catch (IOException e) {
                log.warn("failed to find document for id={}", docId);
                log.error(e);
                return (float) 0.0;
            }
        }
    }

    private static class MLRescoreContext extends RescoreContext {
        private final String modelName;
        private final String modelContext;
        private final MLRescoreMode scoreMode;

        @Nullable
        private final String fallbackScoreField;


        MLRescoreContext(int windowSize, String modelName, String modelContext, MLRescoreMode scoreMode, @Nullable String fallbackScoreField) {
            super(windowSize, MLRescorer.INSTANCE);
            this.modelName = modelName;
            this.modelContext = modelContext;
            this.scoreMode = scoreMode;
            this.fallbackScoreField = fallbackScoreField;
        }
    }
}
