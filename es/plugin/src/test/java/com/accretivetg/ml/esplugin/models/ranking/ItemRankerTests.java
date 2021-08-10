package com.accretivetg.ml.esplugin.models.ranking;

import com.accretivetg.ml.esplugin.StatsD;
import com.accretivetg.ml.esplugin.models.TensorflowServingGrpcClient;

public class ItemRankerTests extends TensorflowModelRankerTests {
    @Override
    TensorflowModelRanker getRanker(StatsD statsd, TensorflowServingGrpcClient client) {
        return new ItemRanker(statsd, client);
    }
}
