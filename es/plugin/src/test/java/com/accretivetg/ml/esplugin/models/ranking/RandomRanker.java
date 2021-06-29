package com.accretivetg.ml.esplugin.models.ranking;

import com.accretivetg.ml.esplugin.MLRescoreContext;
import com.accretivetg.ml.esplugin.StatsD;
import com.accretivetg.ml.esplugin.models.MLModel;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class RandomRanker implements MLModel {
    public Random r;
    public int numberOfCalls = 0;
    public Set<String> uniqueContextsSeen = Sets.newHashSet();
    public Set<String> uniqueItemIdsSeen = Sets.newHashSet();

    public RandomRanker(long seed) {
        r = new Random(seed);
    }
    @Override
    public StatsD getStatsd() {
        return new StatsD();
    }

    @Override
    public boolean modelIsActive() {
        return true;
    }

    @Override
    public Map<String, Float> getScores(MLRescoreContext context, List<String> itemIds) {
        uniqueContextsSeen.add(context.getModelContextAsJson());
        numberOfCalls += 1;
        Map<String, Float> scores = Maps.newHashMapWithExpectedSize(itemIds.size());
        for (String item : itemIds) {
            uniqueItemIdsSeen.add(item);
            scores.put(item, r.nextFloat());
        }
        return scores;
    }
}
