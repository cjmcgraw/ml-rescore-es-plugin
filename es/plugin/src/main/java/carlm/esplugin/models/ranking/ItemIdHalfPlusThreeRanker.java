package carlm.esplugin.models.ranking;

import carlm.esplugin.models.MLModel;
import carlm.esplugin.MLRescoreContext;
import carlm.esplugin.StatsD;
import carlm.esplugin.models.TensorflowServingGrpcClient;
import org.tensorflow.framework.DataType;
import org.tensorflow.framework.TensorProto;
import org.tensorflow.framework.TensorShapeProto;
import tensorflow.serving.GetModelStatus;
import tensorflow.serving.Model;
import tensorflow.serving.Predict;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ItemIdHalfPlusThreeRanker extends RankingModel {
    public static String getName() {
        return "item_id_half_plus_three";
    }

    public static MLModel build(String domain) {
        String uuid = UUID.randomUUID().toString();
        TensorflowServingGrpcClient client = new TensorflowServingGrpcClient(uuid, getName(), domain);
        return new LRUCacheWrappedRanker(
                new ItemIdHalfPlusThreeRanker(uuid, client)
        );
    }

    private final String clientId;
    private final StatsD statsd;
    private final String tensorflowServingModelName = "half_plus_three";

    ItemIdHalfPlusThreeRanker(String clientId, TensorflowServingGrpcClient client) {
        super(client);
        this.clientId = clientId;
        this.statsd = new StatsD(clientId, getName());
    }

    @Override
    protected GetModelStatus.GetModelStatusRequest createModelStatusRequest() {
        return GetModelStatus.GetModelStatusRequest
                .newBuilder()
                .setModelSpec(Model.ModelSpec
                        .newBuilder()
                        .setName(tensorflowServingModelName)
                        .build()
                )
                .build();
    }

    @Override
    protected Predict.PredictRequest createRequest(MLRescoreContext context, List<Long> ids) {
        List<Float> formattedIds = ids.stream()
                .map(Long::floatValue)
                .collect(Collectors.toList());

        TensorProto predictProtos = TensorProto
                .newBuilder()
                .setDtype(DataType.DT_FLOAT)
                .setTensorShape(TensorShapeProto
                        .newBuilder()
                        .addDim(TensorShapeProto.Dim
                                .newBuilder()
                                .setSize(formattedIds.size()))
                        .addDim(TensorShapeProto.Dim
                                .newBuilder()
                                .setSize(1)))
                .addAllFloatVal(formattedIds)
                .build();

        return Predict.PredictRequest
                .newBuilder()
                .setModelSpec(Model.ModelSpec
                        .newBuilder()
                        .setName(tensorflowServingModelName)
                        .build())
                .putInputs("x", predictProtos)
                .build();
    }

    @Override
    protected List<Float> processResponse(Predict.PredictResponse response) {
        return response.getOutputsOrThrow("y")
                .getFloatValList();
    }

    @Override
    public StatsD getStatsd() {
        return statsd;
    }
}
