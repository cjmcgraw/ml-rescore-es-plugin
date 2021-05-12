package carlm.esplugin.models;

import carlm.esplugin.MLRescoreContext;
import carlm.esplugin.StatsD;
import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public interface MLModel {
    public StatsD getStatsd();
    public Map<Long, Float> getScores(MLRescoreContext context, List<Long> itemIds);
    public boolean modelIsActive();

    default String getContextHash(MLRescoreContext context) {
        return Hashing.sha256()
                .hashString(context.getModelContextAsJson(), StandardCharsets.UTF_8)
                .toString();
    }
}
