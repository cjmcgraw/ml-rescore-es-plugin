package com.accretivetg.ml.esplugin.models.ranking;

import com.accretivetg.ml.esplugin.MLRescoreContext;
import com.accretivetg.ml.esplugin.StatsD;
import com.accretivetg.ml.esplugin.models.MLModel;
import com.accretivetg.ml.esplugin.models.TensorflowServingGrpcClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.common.cache.CacheBuilder;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.rest.RestStatus;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ContextualItemRanker extends TensorflowModelRanker implements MLModel {
    public static final String EXTERNAL_FACING_NAME = "contextual_item_ranker";
    private static final Logger log = LogManager.getLogger(ContextualItemRanker.class);
    private static final String OUTPUT_KEY = "scores";
    private static final String ITEM_ID_KEY = "item_id";
    private static final int VALID_HASH_BUCKETS = 100_000;
    private static final HashFunction HASH_FN = Hashing.farmHashFingerprint64();

    public static MLModel build(String domain, String modelName) {
        String uuid = UUID.randomUUID().toString();
        StatsD statsD = new StatsD(
                "name." + EXTERNAL_FACING_NAME,
                "domain." + domain.replace('.', '-').replace(':','-'),
                "uuid." + uuid
        );
        TensorflowServingGrpcClient client = new TensorflowServingGrpcClient(statsD, domain, modelName);
        MLModel ranker = new ContextualItemRanker(statsD, client);
        MLModel cachedResultsRanker = new LRUCacheWrappedRanker(
                ranker,
                CacheBuilder
                    .<String, Float>builder()
                    .setMaximumWeight(75_000_000)
                    .setExpireAfterWrite(TimeValue.timeValueHours(6))
                    .setExpireAfterAccess(TimeValue.timeValueHours(1))
        );
        MLModel cachedInvalidContextRanker = new InvalidContextCacheWrappedRanker(cachedResultsRanker);
        return cachedInvalidContextRanker;
    }

    ContextualItemRanker(
            StatsD statsd,
            TensorflowServingGrpcClient client
    ) {
        super(statsd, client, ITEM_ID_KEY, OUTPUT_KEY);
    }

    @Override
    public String getContextHash(MLRescoreContext context) {
        Map<String, List<String>> modelContext = Maps.newHashMap(context.getModelContext());
        if (!modelContext.containsKey("session") || modelContext.get("session").isEmpty()) {
            return super.getContextHash(context);
        }
        List<String> sessionData = modelContext.remove("session");
        if(sessionData.size() > 1) {
            log.error("found session data greater than 1 value, that is not valid!");
            throw new ElasticsearchStatusException(
                    "You passed too many values for session! Only 1 supported!",
                    RestStatus.BAD_REQUEST
            );
        }

        int sessionBucket = HASH_FN
                .hashString(sessionData.get(0), StandardCharsets.UTF_8)
                .hashCode() / VALID_HASH_BUCKETS;

        String contextJson;
        try {
            ObjectMapper objMapper = new ObjectMapper();
            contextJson = objMapper.writeValueAsString(modelContext);
        } catch (JsonProcessingException exception) {
            log.catching(exception);
            log.error("error processing model context without session data into json!");
            throw new ElasticsearchStatusException("Failed to process model context!", RestStatus.INTERNAL_SERVER_ERROR);
        }
        return Hashing.sha256()
                .hashString(contextJson + sessionBucket, StandardCharsets.UTF_8)
                .toString();
    }
}
