package carlm.esplugin;

import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.SearchPlugin;

import java.util.List;

import static java.util.Collections.singletonList;

public class MLRescoringPlugin extends Plugin implements SearchPlugin {
    @Override
    public List<RescorerSpec<?>> getRescorers() {
        return singletonList(
                new RescorerSpec<>(
                        MLRescorerBuilder.NAME,
                        MLRescorerBuilder::new,
                        MLRescorerBuilder::fromXContent
                )
        );
    }
}
