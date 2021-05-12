package carlm.esplugin.models.ranking;

import carlm.esplugin.MLRescoreContext;
import carlm.esplugin.models.MLModel;
import carlm.esplugin.models.TensorflowServingGrpcClient;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tensorflow.serving.GetModelStatus;
import tensorflow.serving.Predict;

import java.util.List;
import java.util.Map;

abstract public class RankingModel implements MLModel {
    private static final Logger log = LogManager.getLogger(LRUCacheWrappedRanker.class);
    private final TensorflowServingGrpcClient client;
    public RankingModel(TensorflowServingGrpcClient client) {
        this.client = client;
    }

    @Override
    public Map<Long, Float> getScores(MLRescoreContext context, List<Long> itemIds) {
        Predict.PredictRequest prediction = createRequest(context, itemIds);
        Predict.PredictResponse response = this.client.runPrediction(prediction);
        List<Float> itemScores = processResponse(response);
        if (itemScores.size() != itemIds.size()) {
            String msg = String.format(
                    "Response had a difference in requested items and received scores! requested=%s, received=%s",
                    itemIds.size(), itemScores.size()
            );
            throw new IllegalArgumentException(msg);
        }

        Map<Long, Float> scores = Maps.newHashMapWithExpectedSize(itemScores.size());
        for (int i = 0; i < itemScores.size(); i++) {
            scores.put(itemIds.get(i), itemScores.get(i));
        }
        return scores;
    }

    public boolean modelIsActive() {
        GetModelStatus.GetModelStatusRequest modelStatusRequest = createModelStatusRequest();
        return this.client.modelIsActive(modelStatusRequest);
    }

    protected abstract GetModelStatus.GetModelStatusRequest createModelStatusRequest();
    protected abstract Predict.PredictRequest createRequest(MLRescoreContext context, List<Long> ids);
    protected abstract List<Float> processResponse(Predict.PredictResponse response);
}
