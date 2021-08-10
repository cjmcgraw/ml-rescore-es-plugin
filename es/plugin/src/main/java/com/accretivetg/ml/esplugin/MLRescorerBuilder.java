package com.accretivetg.ml.esplugin;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.query.QueryRewriteContext;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.rescore.RescoreContext;
import org.elasticsearch.search.rescore.RescorerBuilder;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MLRescorerBuilder extends RescorerBuilder<MLRescorerBuilder>{
    private static final Logger log = LogManager.getLogger(MLRescorerBuilder.class);
    public static final String RESCORE_PLUGIN_NAME = "mlrescore-v2";

    private final String type;
    private final String name;
    private final String modelName;
    private final String domain;
    private ItemIdDataType itemIdDataType;
    private final String itemIdField;
    private final Map<String, List<String>> modelContext;
    private MLRescoreMode scoreMode = MLRescoreMode.Product1p;

    public MLRescorerBuilder(
            String type,
            String name,
            String modelName,
            String domain,
            @Nullable String itemIdDataType,
            String itemIdField,
            Map<String, List<String>> modelContext,
            @Nullable String rescoreMode
    ) {
        this.type = type;
        this.name = name;
        this.domain = domain;
        this.modelName = modelName;
        if (itemIdDataType != null) {
            this.itemIdDataType = ItemIdDataType.fromString(itemIdDataType);
        }
        this.itemIdField = itemIdField;
        this.modelContext = modelContext;
        if (rescoreMode != null) {
            this.scoreMode = MLRescoreMode.fromString(rescoreMode);
        }
    }

    public MLRescorerBuilder(StreamInput in) throws IOException {
        super(in);
        type = in.readString();
        name = in.readString();
        modelName = in.readString();
        domain = in.readString();
        itemIdDataType = ItemIdDataType.readFromStream(in);
        itemIdField = in.readString();
        scoreMode = MLRescoreMode.readFromStream(in);

        int modelContextEntries = in.readInt();
        this.modelContext = Maps.newHashMapWithExpectedSize(modelContextEntries);
        for (int x : IntStream.range(0, modelContextEntries).toArray()) {
            String key = in.readString();
            List<String> value = in.readStringList();
            this.modelContext.put(key, value);

        }
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeString(type);
        out.writeString(name);
        out.writeString(modelName);
        out.writeString(domain);
        itemIdDataType.writeTo(out);
        out.writeString(itemIdField);
        scoreMode.writeTo(out);
        out.writeInt(modelContext.size());
        for (Map.Entry<String, List<String>> entry : modelContext.entrySet()) {
            out.writeString(entry.getKey());
            out.writeStringCollection(entry.getValue());
        }
    }

    @Override
    public String getWriteableName() {
        return RESCORE_PLUGIN_NAME;
    }

    @Override
    public RescorerBuilder<MLRescorerBuilder> rewrite(QueryRewriteContext ctx) {
        return this;
    }

    private static final ParseField TYPE = new ParseField("type");
    private static final ParseField NAME = new ParseField("name");
    private static final ParseField MODEL_NAME = new ParseField("model_name");
    private static final ParseField DOMAIN = new ParseField("domain");
    private static final ParseField ITEMID_DATA_TYPE = new ParseField("itemid_datatype");
    private static final ParseField ITEMID_FIELD = new ParseField("itemid_field");
    private static final ParseField CONTEXT = new ParseField("context");
    private static final ParseField SCORE_MODE = new ParseField("score_mode");
    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.field(TYPE.getPreferredName(), type);
        builder.field(NAME.getPreferredName(), name);
        builder.field(MODEL_NAME.getPreferredName(), modelName);
        builder.field(DOMAIN.getPreferredName(), domain);
        builder.field(ITEMID_DATA_TYPE.getPreferredName(), itemIdDataType.name().toLowerCase(Locale.ROOT));
        builder.field(ITEMID_FIELD.getPreferredName(), itemIdField);
        builder.startObject(CONTEXT.getPreferredName());
        builder.map(modelContext);
        builder.endObject();
        builder.field(SCORE_MODE.getPreferredName(), scoreMode.name().toLowerCase(Locale.ROOT));
    }

    @SuppressWarnings("unchecked")
    public static MLRescorerBuilder fromXContent(XContentParser parser) throws IOException {
        Map<String, Object> data = parser.map();

        List<String> requiredFields = Lists.newArrayList(
                TYPE.getPreferredName(),
                NAME.getPreferredName(),
                MODEL_NAME.getPreferredName(),
                DOMAIN.getPreferredName(),
                ITEMID_FIELD.getPreferredName()
        );

        String missingFieldsCsv = requiredFields.stream()
                .filter((s) -> !data.containsKey(s))
                .collect(Collectors.joining(","));

        if (missingFieldsCsv.length() > 0) {
            log.error("Attempted to rescore. Cannot create rescore with missing fields: {}", missingFieldsCsv);
            throw new ElasticsearchParseException("missing required fields: " + missingFieldsCsv);
        }

        String type = (String) data.get("type");
        String name = (String) data.get("name");
        String modelName = (String) data.get("model_name");
        String domain = (String) data.get("domain");
        String itemIdField = (String) data.get("itemid_field");

        Map<String, List<String>> modelContext;
        if (!data.containsKey("context")) {
            modelContext = Maps.newHashMap();
        } else {
            try {
                modelContext = (Map<String, List<String>>) data.get("context");
            } catch (ClassCastException e) {
                throw new ElasticsearchParseException("Failed to process context. Requires json object string -> list<string>", e);
            }
        }

        String scoreMode = (String) data.getOrDefault("score_mode", MLRescoreMode.Product1p.name());
        String itemIdDataType = (String) data.getOrDefault("itemid_datatype", ItemIdDataType.INT64.name());
        return new MLRescorerBuilder(
                type,
                name,
                modelName,
                domain,
                itemIdDataType,
                itemIdField,
                modelContext,
                scoreMode
        );
    }


    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        MLRescorerBuilder other = (MLRescorerBuilder) obj;
        return (
                Objects.equals(type, other.type)
                        && Objects.equals(name, other.name)
                        && Objects.equals(modelName, other.modelName)
                        && Objects.equals(domain, other.domain)
                        && Objects.equals(itemIdDataType, other.itemIdDataType)
                        && Objects.equals(itemIdField, other.itemIdField)
                        && Objects.equals(modelContext, other.modelContext)
                        && Objects.equals(scoreMode, other.scoreMode)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                super.hashCode(),
                type,
                name,
                modelName,
                domain,
                itemIdDataType,
                itemIdField,
                modelContext,
                scoreMode
        );
    }
    @Override
    public RescoreContext innerBuildContext(int windowSize, QueryShardContext context) throws IOException {
        MappedFieldType fieldType = context.fieldMapper(itemIdField);
        if (fieldType == null) {
            log.error("unknown or invalid itemid field passed={}", itemIdField);
            throw new ElasticsearchStatusException("unknown or invalid itemid_field passed: " + itemIdField, RestStatus.BAD_REQUEST);
        }
        return new MLRescoreContext(
                windowSize,
                MLRescorer.INSTANCE,
                type,
                name,
                modelName,
                domain,
                itemIdDataType,
                context.getForField(fieldType),
                modelContext,
                scoreMode
        );
    }
}
