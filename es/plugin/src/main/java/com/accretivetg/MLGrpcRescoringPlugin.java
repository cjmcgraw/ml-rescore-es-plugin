package com.accretivetg;

import org.elasticsearch.plugins.Plugin;
import java.util.List;

import static java.util.Collections.singletonList;
import org.elasticsearch.plugins.SearchPlugin;

public class MLGrpcRescoringPlugin extends Plugin implements SearchPlugin {
    @Override
    public List<RescorerSpec<?>> getRescorers() {
        return singletonList(
                new RescorerSpec<>(
                        MLRescoreBuilder.NAME,
                        MLRescoreBuilder::new,
                        MLRescoreBuilder::fromXContent
                )
        );
    }
}
