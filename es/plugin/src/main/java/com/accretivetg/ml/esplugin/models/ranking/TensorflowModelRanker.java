package com.accretivetg.ml.esplugin.models.ranking;

import com.accretivetg.ml.esplugin.*;
import com.accretivetg.ml.esplugin.models.MLModel;
import com.accretivetg.ml.esplugin.models.TensorflowServingGrpcClient;
import com.accretivetg.ml.esplugin.models.Utils;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.cache.Cache;
import org.elasticsearch.common.cache.CacheBuilder;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.rest.RestStatus;
import org.tensorflow.framework.TensorProto;

import java.util.List;
import java.util.Map;

abstract public class TensorflowModelRanker implements MLModel {
    private static final Logger log = LogManager.getLogger(TensorflowModelRanker.class);
    private final TensorflowServingGrpcClient client;
    private final StatsD statsd;
    private final String itemIdKey;
    private final String outputKey;

    public TensorflowModelRanker(
            StatsD statsd,
            TensorflowServingGrpcClient client,
            String itemIdKey,
            String outputKey
    ) {
        this.statsd = statsd;
        this.client = client;
        this.itemIdKey = itemIdKey;
        this.outputKey = outputKey;
    }

    @Override
    public StatsD getStatsd() {
        return statsd;
    }

    @Override
    public Map<String, Float> getScores(MLRescoreContext context, List<String> itemIds) {
        String contextHash = getContextHash(context);
        Map<String, TensorProto> inputs = createInput(context, itemIds);
        Map<String, TensorProto> outputs = this.client.runPrediction(inputs);
        List<Float> itemScores = processOutput(outputs);
        if (itemScores.size() != itemIds.size()) {
            String msg = String.format(
                    "Response had a difference in requested items and received scores! requested=%s, received=%s",
                    itemIds.size(), itemScores.size()
            );
            throw new IllegalArgumentException(msg);
        }

        Map<String, Float> scores = Maps.newHashMapWithExpectedSize(itemScores.size());
        for (int i = 0; i < itemScores.size(); i++) {
            scores.put(itemIds.get(i), itemScores.get(i));
        }
        return scores;
    }

    public boolean modelIsActive() {
        return this.client.modelIsActive();
    }

    protected Map<String, TensorProto> createInput(MLRescoreContext context, List<String> items) {
        ItemIdDataType itemIdDataType = context.getItemIdDataType();
        Map<String, List<String>> modelContextData = context.getModelContext();
        Map<String, TensorProto> inputs = Utils.Protobuf.contextToContextProtos(modelContextData);
        inputs.put(itemIdKey, Utils.Protobuf.listToTensorProto(itemIdDataType, items));
        return inputs;
    }

    protected List<Float> processOutput(Map<String, TensorProto> response) {
        if (!response.containsKey(outputKey)) {
            String modelConnection = client.getHumanReadableIdentifier();
            log.error("Failed to retrieve expected item key in response: {} outputkey={} responseKeys={}",
                    modelConnection,
                    outputKey,
                    response.keySet().toString()
            );
            throw new BadModelResponseException(
                    "Failed to find expected key in model!",
                    RestStatus.BAD_REQUEST
            );
        }
        TensorProto modelOutput = response.get(outputKey);

        if (modelOutput.getFloatValCount() <= 0) {
            String modelConnection = client.getHumanReadableIdentifier();
            log.error("Failed to retrieve expected float values for scores! Please ensure your model returns float32s! " +
                    "{} outputKey={} tensorProto={}",
                    modelConnection,
                    outputKey,
                    modelOutput
            );
            throw new BadModelResponseException(
                    "Failed to find float values in model for scores!",
                    RestStatus.BAD_REQUEST
            );
        }
        return modelOutput.getFloatValList();
    }
}
