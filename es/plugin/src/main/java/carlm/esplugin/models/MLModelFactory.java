package carlm.esplugin.models;

import carlm.esplugin.MLRescoreContext;
import carlm.esplugin.models.ranking.ItemIdHalfPlusThreeRanker;
import carlm.esplugin.models.ranking.ItemIdHalfPlusTwoRanker;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.rest.RestStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class MLModelFactory {
    private static final Logger log = LogManager.getLogger(MLModelFactory.class);
    public static MLModel getModel(MLRescoreContext context) {
        switch (context.getType()) {
            case "retrieval":
                return getRetriever(context);
            case "ranking":
                return getRanker(context);
            default:
                throw new ElasticsearchStatusException("Unknown model type given = " + context.getType(), RestStatus.BAD_REQUEST);
        }
    }


    private static final Map<String, Function<String, MLModel>> rankerModelBuilders = Map.of(
            ItemIdHalfPlusTwoRanker.getName(), ItemIdHalfPlusTwoRanker::build,
            ItemIdHalfPlusThreeRanker.getName(), ItemIdHalfPlusThreeRanker::build
    );

    private static final Map<String, MLModel> cachedRankerModels = new ConcurrentHashMap<>();

    private static MLModel getRanker(MLRescoreContext context) {
        String name = context.getName();
        if (!rankerModelBuilders.containsKey(name)) {
            log.error("Invalid ranker model name given! name = " + name);
            throw new ElasticsearchStatusException("Unknown model name given = " + name, RestStatus.BAD_REQUEST);
        }

        String domain = context.getDomain();
        String key = name + "/" + domain;
        if (!cachedRankerModels.containsKey(key)) {
            log.warn("No cached ranker for name={} domain={}", name, domain);
            MLModel model = rankerModelBuilders.get(name).apply(domain);

            if (!model.modelIsActive()) {
                log.error("Inbound request asked for inactive model! type={} name={} domain={}",
                        context.getType(), name, domain);
                throw new ElasticsearchStatusException(
                        "Inactive or Unknown machine learning model given! domain=" + domain,
                        RestStatus.BAD_REQUEST
                );
            }
            cachedRankerModels.put(key, model);
        }
        return cachedRankerModels.get(key);
    }

    private static final Map<String, Function<String, MLModel>> retrievalModelBuilders = Map.of();
    private static final Map<String, MLModel> cachedRetrievalModels = new ConcurrentHashMap<>();
    private static MLModel getRetriever(MLRescoreContext context) {
        throw new ElasticsearchStatusException("Retrievers are not yet supported!!", RestStatus.BAD_REQUEST);
    }
}
