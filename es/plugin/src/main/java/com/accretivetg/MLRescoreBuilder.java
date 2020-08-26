package com.accretivetg;

import com.google.common.primitives.Floats;
import com.google.protobuf.InvalidProtocolBufferException;
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

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

import static java.util.Collections.singletonList;
import static org.elasticsearch.common.xcontent.ConstructingObjectParser.constructorArg;

public class MLRescoreBuilder extends RescorerBuilder<MLRescoreBuilder> {
    public static final String NAME = "mlrescore";

    private final String modelName;
    private final String modelContext;

    public MLRescoreBuilder(String modelName, String modelContext) {
        this.modelName = modelName;
        this.modelContext = modelContext;
    }

    public MLRescoreBuilder(StreamInput in) throws IOException {
        super(in);
        modelName = in.readString();
        modelContext = in.readString();
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeString(modelName);
        out.writeString(modelContext);
    }

    @Override
    public String getWriteableName() {
        return "mlrescore";
    }

    @Override
    public RescorerBuilder<MLRescoreBuilder> rewrite(QueryRewriteContext ctx) throws IOException {
        return this;
    }

    private static final ParseField MODEL_NAME = new ParseField("model_name");
    private static final ParseField CONTEXT = new ParseField("context");
    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.field(MODEL_NAME.getPreferredName(), modelName);
        builder.field(CONTEXT.getPreferredName(), modelContext);
    }

    private static final ConstructingObjectParser<MLRescoreBuilder, Void> PARSER =
            new ConstructingObjectParser<>(NAME, args -> new MLRescoreBuilder((String) args[0], (String) args[1]));
    static {
        PARSER.declareString(constructorArg(), MODEL_NAME);
        PARSER.declareString(constructorArg(), CONTEXT);
    }

    public static MLRescoreBuilder fromXContent(XContentParser parser) {
        return PARSER.apply(parser, null);
    }


    @Override
    public RescoreContext innerBuildContext(int windowSize, QueryShardContext context) throws IOException {
        return new MLRescoreContext(windowSize, modelName, modelContext);
    }

    @Override
    public boolean equals(Object obj) {
        if (false == super.equals(obj)) {
            return false;
        }
        MLRescoreBuilder other = (MLRescoreBuilder) obj;
        return modelName.equals(other.modelName)
                && Objects.equals(modelContext, other.modelContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), modelName, modelContext);
    }

    String modelName() {
        return modelName;
    }

    String modelContext() {
        return modelContext;
    }

    private static class MLRescorer implements Rescorer {
        private static final MLRescorer INSTANCE = new MLRescorer();

        @Override
        public TopDocs rescore(TopDocs topDocs, IndexSearcher searcher, RescoreContext rescoreContext) throws InvalidProtocolBufferException {
            MLRescoreContext mlContext = (MLRescoreContext) rescoreContext;

            long[] exampleIds = Arrays.stream(topDocs.scoreDocs)
                    .mapToLong(x -> x.doc)
                    .toArray();

            float[] scores = getRecommenderScores(exampleIds, mlContext);

            for (int i = 0; i < topDocs.scoreDocs.length; i++) {
                topDocs.scoreDocs[i].score = scores[i];
            }

            Arrays.sort(topDocs.scoreDocs, (a, b) -> Floats.compare(a.score, b.score));
            return topDocs;
        }

        @Override
        public Explanation explain(int topLevelDocId, IndexSearcher searcher, RescoreContext rescoreContext, Explanation sourceExplanation){
            return sourceExplanation;
        }

        private float[] getRecommenderScores(long[] exampleIds, MLRescoreContext mlContext) throws InvalidProtocolBufferException {
            return mlContext.recommender.score(
                    exampleIds,
                    mlContext.modelContext
            );
        }
    }

    private static class MLRescoreContext extends RescoreContext {
        private final RecommenderClient recommender;
        private final String modelName;
        private final String modelContext;

        MLRescoreContext(int windowSize, String modelName, String modelContext) {
            super(windowSize, MLRescorer.INSTANCE);
            this.modelName = modelName;
            this.recommender = RecommenderClient.buildRecommenderClient(modelName);
            this.modelContext = modelContext;
        }
    }

}
