package com.accretivetg.ml.esplugin.models.ranking;

import com.accretivetg.ml.esplugin.MLRescoreContext;
import com.accretivetg.ml.esplugin.StatsD;
import com.accretivetg.ml.esplugin.models.MLModel;
import com.accretivetg.ml.esplugin.models.TensorflowServingGrpcClient;
import com.accretivetg.ml.esplugin.models.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.cache.CacheBuilder;
import org.elasticsearch.common.unit.TimeValue;
import org.tensorflow.framework.DataType;
import org.tensorflow.framework.TensorProto;
import org.tensorflow.framework.TensorShapeProto;
import tensorflow.serving.Model;
import tensorflow.serving.Predict;

import java.util.List;
import java.util.UUID;

public class ItemRanker extends ContextualItemRanker {
    public static final String EXTERNAL_FACING_NAME = "item_ranker";

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
        );
    }

    ItemRanker(StatsD statsd, TensorflowServingGrpcClient client) {
        super(statsd, client);
    }

    @Override
    public String getContextHash(MLRescoreContext context) {
        return "all-requests-are-the-same-key";
    }
}
