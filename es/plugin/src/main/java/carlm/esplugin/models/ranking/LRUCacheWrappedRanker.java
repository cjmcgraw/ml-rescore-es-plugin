package carlm.esplugin.models.ranking;

import carlm.esplugin.models.MLModel;
import carlm.esplugin.MLRescoreContext;
import carlm.esplugin.StatsD;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Longs;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.cache.Cache;
import org.elasticsearch.common.cache.CacheBuilder;
import org.elasticsearch.common.unit.TimeValue;

import java.util.List;
import java.util.Map;

public class LRUCacheWrappedRanker implements MLModel {
    private static final Logger log = LogManager.getLogger(LRUCacheWrappedRanker.class);
    private final MLModel ranker;
    private final Cache<String, Float> cache;

    public LRUCacheWrappedRanker(MLModel ranker) {
        this(
                ranker,
                CacheBuilder
                        .<String, Float>builder()
                        .setMaximumWeight(10_000_000)
                        .setExpireAfterAccess(TimeValue.timeValueMinutes(20))
        );
    }

    LRUCacheWrappedRanker(MLModel ranker, CacheBuilder<String, Float> cacheBuilder) {
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
    public Map<Long, Float> getScores(MLRescoreContext context, List<Long> itemIds) {
        StatsD statsd = getStatsd();
        String contextHash = getContextHash(context);
        log.debug("using session hash = {}", contextHash);

        Map<Long, Float> scores = Maps.newHashMapWithExpectedSize(itemIds.size());
        List<Long> itemsThatNeedScoring = Lists.newArrayListWithCapacity(itemIds.size());

        for (long itemId : itemIds) {
            String cacheKey = contextHash + "/" + itemId;
            Float cachedScore = cache.get(cacheKey);
            if (cachedScore == null) {
                itemsThatNeedScoring.add(itemId);
            } else {
                scores.put(itemId, cachedScore);
            }

        }
        statsd.count("cache.hits", scores.size());
        statsd.count("cache.misses", itemsThatNeedScoring.size());

        if (itemsThatNeedScoring.size() > 0) {
            Map<Long, Float> newScores = ranker.getScores(context, itemsThatNeedScoring);

            for (Map.Entry<Long, Float> entry : newScores.entrySet()) {
                String newCacheKey = contextHash + "/" + entry.getKey();
                cache.put(newCacheKey, entry.getValue());
                scores.put(entry.getKey(), entry.getValue());
            }
        }

        statsd.gauge("cache.size", cache.count());
        return scores;
    }
}
