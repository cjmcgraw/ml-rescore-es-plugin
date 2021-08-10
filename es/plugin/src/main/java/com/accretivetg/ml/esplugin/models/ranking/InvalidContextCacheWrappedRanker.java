package com.accretivetg.ml.esplugin.models.ranking;

import com.accretivetg.ml.esplugin.InvalidModelInputException;
import com.accretivetg.ml.esplugin.MLRescoreContext;
import com.accretivetg.ml.esplugin.StatsD;
import com.accretivetg.ml.esplugin.models.MLModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.cache.Cache;
import org.elasticsearch.common.cache.CacheBuilder;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.rest.RestStatus;

import java.util.List;
import java.util.Map;

public class InvalidContextCacheWrappedRanker implements MLModel {
    private static final Logger log = LogManager.getLogger(InvalidContextCacheWrappedRanker.class);
    private final MLModel ranker;
    private final Cache<String, Boolean> cache;

    public InvalidContextCacheWrappedRanker(MLModel ranker) {
        this(
                ranker,
                CacheBuilder
                        .<String, Boolean>builder()
                        .setMaximumWeight(25_000_000)
                        .setExpireAfterWrite(TimeValue.timeValueDays(1))
                        .setExpireAfterAccess(TimeValue.timeValueHours(6))
        );
    }

    InvalidContextCacheWrappedRanker(MLModel ranker, CacheBuilder<String, Boolean> cacheBuilder) {
        this.ranker = ranker;
        this.cache = cacheBuilder.build();
    }

    @Override
    public StatsD getStatsd() {
        return ranker.getStatsd();
    }

    @Override
    public String getContextHash(MLRescoreContext context) {
        return ranker.getContextHash(context);
    }

    @Override
    public boolean modelIsActive() {
        return this.ranker.modelIsActive();
    }

    @Override
    public Map<String, Float> getScores(MLRescoreContext context, List<String> itemIds) {
        StatsD statsd = getStatsd();
        String contextHash = getContextHash(context);
        statsd.gauge("invalid-cache.size", cache.count());
        Boolean knownInvalidContext = cache.get(contextHash);
        if (knownInvalidContext != null && knownInvalidContext) {
            statsd.increment("invalid-cache.hit");
            throw new InvalidModelInputException(
                    "Model input context recognized as invalid from previous attempts. Please try again later!\n" +
                            "contextHash=" + contextHash + "\n" +
                            "contextJson=" + context.getModelContextAsJson() + "\n",
                    RestStatus.UNPROCESSABLE_ENTITY
            );
        }
        try {
            return ranker.getScores(context, itemIds);
        } catch (InvalidModelInputException exception) {
            cache.put(contextHash, true);
            statsd.increment("invalid-cache.added");
            throw exception;
        }
    }
}