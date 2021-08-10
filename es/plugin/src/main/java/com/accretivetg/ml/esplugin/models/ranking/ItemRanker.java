package com.accretivetg.ml.esplugin.models.ranking;

import com.accretivetg.ml.esplugin.InvalidModelInputException;
import com.accretivetg.ml.esplugin.MLRescoreContext;
import com.accretivetg.ml.esplugin.StatsD;
import com.accretivetg.ml.esplugin.models.MLModel;
import com.accretivetg.ml.esplugin.models.TensorflowServingGrpcClient;
import com.accretivetg.ml.esplugin.models.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.cache.CacheBuilder;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.rest.RestStatus;
import org.tensorflow.framework.TensorProto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ItemRanker extends TensorflowModelRanker implements MLModel {
    public static final String EXTERNAL_FACING_NAME = "item_ranker";
    private static final String ITEMID_KEY = "item_id";
    private static final String OUTPUT_KEY = "scores";

    public static MLModel build(String domain, String modelName) {
        String uuid = UUID.randomUUID().toString();
        StatsD statsD = new StatsD(
                "name." + EXTERNAL_FACING_NAME,
                "domain." + Utils.Strings.mungeBadCharactersForStatsD(domain),
                "uuid." + uuid
        );
        TensorflowServingGrpcClient client = new TensorflowServingGrpcClient(statsD, domain, modelName);
        return new LRUCacheWrappedRanker(
                new ItemRanker(statsD, client),
                CacheBuilder
                        .<String, Float>builder()
                        .setMaximumWeight(1_000_000)
                        .setExpireAfterWrite(TimeValue.timeValueHours(8))
                        .setExpireAfterAccess(TimeValue.timeValueHours(1))
        );
    }


    ItemRanker(StatsD statsd, TensorflowServingGrpcClient client) {
        super(statsd, client, ITEMID_KEY, OUTPUT_KEY);
    }

    @Override
    public String getContextHash(MLRescoreContext context) {
        return "all-requests-are-the-same-key";
    }

    @Override
    protected Map<String, TensorProto> createInput(MLRescoreContext context, List<String> items) {
        if (!context.getModelContext().isEmpty()) {
            throw new InvalidModelInputException(
                    "Invalid input! ItemRankers are not allowed to contain any context!",
                    RestStatus.BAD_REQUEST
            );
        }
        return super.createInput(context, items);
    }
}
