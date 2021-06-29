package com.accretivetg.ml.esplugin.models;

import com.accretivetg.ml.esplugin.MLRescoreContext;
import com.accretivetg.ml.esplugin.StatsD;
import com.accretivetg.ml.esplugin.models.ranking.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.rest.RestStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public class MLModelFactory {
    private static final Logger log = LogManager.getLogger(MLModelFactory.class);
    private static final StatsD statsd = new StatsD("model-factory");
    public static MLModel getModel(MLRescoreContext context) {
        statsd.increment(
                "requested.type." + context.getType() +
                ".name." + context.getName() +
                ".domain." + context.getDomain().replace('.','-').replace(':', '-') +
                ".model_name." + context.getModelName()
        );
        switch (context.getType()) {
            case "retrieval":
                return getRetriever(context);
            case "ranking":
                return getRanker(context);
            default:
                throw new ElasticsearchStatusException("Unknown model type given = " + context.getType(), RestStatus.BAD_REQUEST);
        }
    }


    private static final Map<String, BiFunction<String, String, MLModel>> rankerModelBuilders = Map.of(
            ItemIdInverseTestRanker.EXTERNAL_FACING_NAME, ItemIdInverseTestRanker::build,
            ContextualItemRanker.EXTERNAL_FACING_NAME, ContextualItemRanker::build,
            ItemRanker.EXTERNAL_FACING_NAME, ItemRanker::build
    );

    private static final Map<String, MLModel> cachedRankerModels = new ConcurrentHashMap<>();

    private static MLModel getRanker(MLRescoreContext context) {
        String name = context.getName();
        if (!rankerModelBuilders.containsKey(name)) {
            log.error("Invalid ranker name given! name = " + name);
            throw new ElasticsearchStatusException("Unknown name given = " + name, RestStatus.BAD_REQUEST);
        }

        String modelName = context.getModelName();
        String domain = context.getDomain();
        String statsDBaseKey = "cache.type.ranker.name." + name +
                ".domain." + domain.replace(".", "-").replace(':', '-') +
                ".model_name." + modelName;
        String key = name + "/" + domain + "/" + modelName;
        if (!cachedRankerModels.containsKey(key)) {
            statsd.increment(statsDBaseKey + ".cache-miss");
            log.warn("No cached ranker for name={} domain={} model_name={}", name, domain, modelName);
            MLModel model = rankerModelBuilders.get(name).apply(domain, modelName);
            StatsD modelStatsd = model.getStatsd();
            modelStatsd.increment("setup-new-model");

            if (!model.modelIsActive()) {
                log.error("Inbound request asked for inactive model! type={} name={} domain={} model_name={}",
                        context.getType(), name, domain, modelName
                );
                throw new ElasticsearchStatusException(
                        "Inactive or Unknown machine learning model given!" +
                                " domain=" + domain +
                                " model_name=" + modelName,
                        RestStatus.BAD_REQUEST
                );
            }
            cachedRankerModels.put(key, model);
        } else {
            statsd.increment(statsDBaseKey + ".cache-hit");
        }
        return cachedRankerModels.get(key);
    }

    private static final Map<String, Function<String, MLModel>> retrievalModelBuilders = Map.of();
    private static final Map<String, MLModel> cachedRetrievalModels = new ConcurrentHashMap<>();
    private static MLModel getRetriever(MLRescoreContext context) {
        throw new ElasticsearchStatusException("Retrievers are not yet supported!!", RestStatus.BAD_REQUEST);
    }
}
