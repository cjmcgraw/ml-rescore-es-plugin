package com.accretivetg.ml.esplugin.models.ranking;

import com.accretivetg.ml.esplugin.MLRescoreContext;
import com.accretivetg.ml.esplugin.StatsD;
import com.accretivetg.ml.esplugin.models.MLModel;
import com.accretivetg.ml.esplugin.models.Utils;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ItemIdInverseTestRanker implements MLModel {
    public static final String EXTERNAL_FACING_NAME = "item_id_inverse_test";

    public static MLModel build(String domain, String modelName) {
        String uuid = UUID.randomUUID().toString();
        StatsD statsd = new StatsD(
                "name." + EXTERNAL_FACING_NAME,
                "domain." + Utils.Strings.mungeBadCharactersForStatsD(domain),
                "model_name." + modelName,
                "uuid." + uuid
        );
        return new LRUCacheWrappedRanker(new ItemIdInverseTestRanker(statsd));
    }

    private final StatsD statsd;

    public ItemIdInverseTestRanker(StatsD statsd) {
        this.statsd = statsd;
    }

    @Override
    public StatsD getStatsd() {
        return statsd;
    }

    @Override
    public Map<String, Float> getScores(MLRescoreContext context, List<String> itemIds) {
        Map<String, Float> scores = Maps.newHashMapWithExpectedSize(itemIds.size());
        for (String item : itemIds) {
            Long itemId = Long.parseLong(item);
            scores.put(item, (float) 1.0 / itemId);
        }

        return scores;
    }

    @Override
    public boolean modelIsActive() {
        return true;
    }

    @Override
    public String getContextHash(MLRescoreContext context) {
        return "all-items-have-same-hash";
    }
}
