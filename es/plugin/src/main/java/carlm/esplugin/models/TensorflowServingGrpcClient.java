package carlm.esplugin.models;

import carlm.esplugin.InvalidModelInputException;
import carlm.esplugin.StatsD;
import com.google.protobuf.TextFormat;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.rest.RestStatus;
import tensorflow.serving.GetModelStatus;
import tensorflow.serving.Predict.PredictResponse;
import tensorflow.serving.Predict.PredictRequest;
import tensorflow.serving.PredictionServiceGrpc;
import tensorflow.serving.ModelServiceGrpc;
import tensorflow.serving.GetModelStatus.GetModelStatusRequest;
import tensorflow.serving.GetModelStatus.GetModelStatusResponse;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TensorflowServingGrpcClient {
    private static final Logger log = LogManager.getLogger(TensorflowServingGrpcClient.class);
    private final Executor channelThreadPool = Executors.newCachedThreadPool();
    private final Executor rpcThreadPool = Executors.newCachedThreadPool();
    private StatsD statsd;
    private ManagedChannel channel;
    private PredictionServiceGrpc.PredictionServiceBlockingStub predictStub;
    private ModelServiceGrpc.ModelServiceBlockingStub modelServiceStub;
    private int allKnownFailures = 0;
    private final int maximumFailures = 250;
    private final String target;

    public TensorflowServingGrpcClient(String clientId, String modelName, String target) {
        this.target = target;
        this.statsd = new StatsD(clientId, modelName, target, "grpc");
        setupConnection();
    }

    protected void setupConnection() {
        long setupConnectionStart = System.currentTimeMillis();
        statsd.increment("connection.attempt");
        log.warn("establishing new connection with target={}", target);
        channel = ManagedChannelBuilder
                .forTarget(target)
                .usePlaintext()
                .executor(channelThreadPool)
                .build();

        predictStub = PredictionServiceGrpc
                .newBlockingStub(channel)
                .withExecutor(rpcThreadPool);

        modelServiceStub = ModelServiceGrpc
                .newBlockingStub(channel)
                .withExecutor(rpcThreadPool);

        log.warn("established new connection with target={}", target);
        statsd.increment("connection.successful");
        statsd.recordExecutionTime("connection", System.currentTimeMillis() - setupConnectionStart);
    }

    protected boolean isConnected() {
        return (
                predictStub != null &&
                        channel != null &&
                        !channel.isShutdown() &&
                        !channel.isTerminated()
        );
    }

    protected void closeConnection() {
        statsd.increment("connection.shutdown." + target);
        log.warn("shutting down connection to target={}", target);
        channel.enterIdle();
        channel.shutdown();
    }

    public boolean modelIsActive(GetModelStatusRequest request) {
        if (!isConnected()) {
            setupConnection();
        } else {
            statsd.increment("connection.reuse");
        }
        GetModelStatusResponse response;
        try {
            response = AccessController.doPrivileged(
                    (PrivilegedAction<GetModelStatusResponse>) () -> modelServiceStub
                            .withDeadlineAfter(500, TimeUnit.MILLISECONDS)
                            .getModelStatus(request)

            );
        } catch (StatusRuntimeException e) {
            Status grpcStatus = e.getStatus();
            log.catching(e);
            if (grpcStatus.getCode() == Status.UNAVAILABLE.getCode()) {
                log.error(e);
                log.error("Failed to find tensorflow-serving grpc server at target={}", target);
                throw new ElasticsearchStatusException(
                        "Failed to find tensorflow-serving grpc model at target!",
                        RestStatus.BAD_REQUEST
                );
            }
            if (grpcStatus.getCode() == Status.NOT_FOUND.getCode()) {
                log.error(e);
                log.error("Failed to find tensorflow-serving grpc model assocaited with request: {}", request);
                throw new ElasticsearchStatusException(
                        "Failed to find expected model inside of tensorflow-serving grpc!",
                        RestStatus.BAD_REQUEST
                );
            }
            throw e;
        }
        List<GetModelStatus.ModelVersionStatus> statuses = response.getModelVersionStatusList();
        GetModelStatus.ModelVersionStatus status = statuses.get(statuses.size() - 1);
        GetModelStatus.ModelVersionStatus.State state = status.getState();
        if (state != GetModelStatus.ModelVersionStatus.State.AVAILABLE) {
            log.warn("model requested in non-available state! target={} state={}", target, state.getNumber());
            return false;
        }
        return true;
    }

    public PredictResponse runPrediction(PredictRequest request) {
        statsd.increment("request.attempt");

        if (!isConnected()) {
            setupConnection();
        } else {
            statsd.increment("connection.reuse");
        }

        long grpcCallTimeStart = System.currentTimeMillis();
        PredictResponse response;
        try {
            response = AccessController.doPrivileged(
                    (PrivilegedAction<PredictResponse>) () -> predictStub
                            .withDeadlineAfter(600, TimeUnit.MILLISECONDS)
                            .predict(request)
            );
        } catch (StatusRuntimeException e) {
            Status grpcStatus = e.getStatus();
            if (grpcStatus.getCode() == Status.INVALID_ARGUMENT.getCode()) {
                statsd.increment("request.invalid");
                String msg = String.format("unknown argument given to target=%s", target);
                log.info("invalid grpc request given! {}", e.getMessage());
                throw new InvalidModelInputException(msg, RestStatus.BAD_REQUEST, e);
            }
            log.catching(e);
            statsd.increment("request.failure");
            allKnownFailures += 1;
            if (allKnownFailures >= maximumFailures) {
                log.error("maximum failure kill swith triggered! Disconnecting from gRPC! target={}", target);
                closeConnection();
            }
            log.error("grpc error {} target={}", e, target);
            throw e;
        } catch (Exception e) {
            log.catching(e);
            log.error("error sending protobuf target={} request={}", target, TextFormat.printer().printToString(request));
            statsd.increment("request.failure");
            allKnownFailures += 1;
            if (allKnownFailures >= maximumFailures) {
                log.error("maximum failure kill switch triggered! Disconnecting from gRPC! target={}", target);
                closeConnection();
            }
            throw e;
        }
        statsd.increment("request.success");
        statsd.recordExecutionTime("request", System.currentTimeMillis() - grpcCallTimeStart);
        return response;
    }
}
