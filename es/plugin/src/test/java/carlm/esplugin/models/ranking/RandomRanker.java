package carlm.esplugin.models.ranking;

import carlm.esplugin.MLRescoreContext;
import carlm.esplugin.StatsD;
import carlm.esplugin.models.MLModel;
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
    public Set<Long> uniqueItemIdsSeen = Sets.newHashSet();

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
    public Map<Long, Float> getScores(MLRescoreContext context, List<Long> itemIds) {
        uniqueContextsSeen.add(context.getModelContextAsJson());
        numberOfCalls += 1;
        Map<Long, Float> scores = Maps.newHashMapWithExpectedSize(itemIds.size());
        for (long itemId : itemIds) {
            uniqueItemIdsSeen.add(itemId);
            scores.put(itemId, r.nextFloat());
        }
        return scores;
    }
}
