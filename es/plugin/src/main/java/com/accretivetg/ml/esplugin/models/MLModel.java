package com.accretivetg.ml.esplugin.models;

import com.accretivetg.ml.esplugin.MLRescoreContext;
import com.accretivetg.ml.esplugin.StatsD;
import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public interface MLModel {
    public StatsD getStatsd();
    public Map<String, Float> getScores(MLRescoreContext context, List<String> itemIds);
    public boolean modelIsActive();

    default String getContextHash(MLRescoreContext context) {
        return Hashing.sha256()
                .hashString(context.getModelContextAsJson(), StandardCharsets.UTF_8)
                .toString();
    }
}
