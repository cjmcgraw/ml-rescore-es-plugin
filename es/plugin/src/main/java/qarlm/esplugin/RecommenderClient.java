package qarlm.esplugin;

import com.google.common.collect.Lists;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.cache.Cache;
import org.elasticsearch.common.cache.CacheBuilder;
import org.elasticsearch.common.unit.TimeValue;
import qarlm.RecommenderGrpc;
import qarlm.RecommenderRequest;
import qarlm.RecommenderResponse;


import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;

public class RecommenderClient {
  private static final Logger log = LogManager.getLogger(RecommenderClient.class);
  private static final Executor channelThreadPool = Executors.newCachedThreadPool();
  private static final Executor rpcThreadPool = Executors.newCachedThreadPool();
  private static final Cache<String, Float> scoreCache = CacheBuilder
          .<String, Float>builder()
          .setMaximumWeight(2_000_000)
          .setExpireAfterAccess(TimeValue.timeValueSeconds(10))
          .build();

  public static RecommenderClient buildRecommenderClient(String host) {
      log.warn("establishing new rcp client with host: {}", host);
      ManagedChannel channel = ManagedChannelBuilder
              .forTarget(host)
              .usePlaintext()
              .executor(channelThreadPool)
              .build();


      RecommenderGrpc.RecommenderBlockingStub stub = RecommenderGrpc
              .newBlockingStub(channel)
              .withExecutor(rpcThreadPool);

      log.warn("warming up recommender with simple request!");
      long startTime = System.nanoTime();
      AccessController.doPrivileged(
              (PrivilegedAction<Void>) () -> {
                  stub.recommend(RecommenderRequest
                          .newBuilder()
                          .setContext("test_request")
                          .build()
                  );
                  return null;
              }
      );
      long endTime = System.nanoTime();
      log.info("recommender initial gRPC reques took {} ms", (endTime - startTime) / 1e6);

    return new RecommenderClient(stub);
  }

  private final RecommenderGrpc.RecommenderBlockingStub stub;
  private final ManagedChannel channel;

  private RecommenderClient(RecommenderGrpc.RecommenderBlockingStub stub) {
      this.channel = (ManagedChannel) stub.getChannel();
      this.stub = stub;
  }

  private boolean isInvalid() {
      return stub == null || channel == null || channel.isShutdown() || channel.isTerminated();
  }

  private void shutdown() {
      channel.shutdown();
  }

  public float[] score(long[] exampleIds, String context) {
      long startTime = System.nanoTime();
      List<Long> needsScoring = Lists.newArrayListWithCapacity(exampleIds.length);
      List<Integer> indexesThatNeedScoring = Lists.newArrayListWithCapacity(exampleIds.length);

      float[] scores = new float[exampleIds.length];
      for (int i = 0; i < exampleIds.length; i++) {
          String key = exampleIds[i] + context;
          Float score = scoreCache.get(key);
          if(score == null) {
              needsScoring.add(exampleIds[i]);
              indexesThatNeedScoring.add(i);
          } else {
              scores[i] = score;
          }
      }

      log.info("cache hit percentage: " + (1.0 - (needsScoring.size() / exampleIds.length)));
      log.info("found cached scores " + (exampleIds.length - needsScoring.size()));

      if (needsScoring.size() > 0) {
          long grpcCallStartTime = System.nanoTime();
          log.info("running recommendation with " + needsScoring.size() + " exampleids");
          RecommenderRequest request = RecommenderRequest
                  .newBuilder()
                  .setContext(context)
                  .addAllExampleids(needsScoring)
                  .build();

          RecommenderResponse response;
          try {
              response = AccessController.doPrivileged(
                      (PrivilegedAction<RecommenderResponse>) () -> stub
                              .withDeadlineAfter(50, TimeUnit.MILLISECONDS)
                              .recommend(request)
              );
          } catch (Exception e) {
              log.error(e);
              throw new ElasticsearchException(e);
          }

          for (int i = 0; i < needsScoring.size(); i++) {
              int originalIndex = indexesThatNeedScoring.get(i);
              String key = exampleIds[originalIndex] + context;
              float newScore = response.getOutputs(i);
              scoreCache.put(key, newScore);
              scores[originalIndex] = newScore;
          }

          long grpcCallEndTime = System.nanoTime();
          log.info("grpc call finished in {} ms", (grpcCallEndTime - grpcCallStartTime) / 1e6);
      }
      long endTime = System.nanoTime();
      log.info("scored {} examples in {} ms", exampleIds.length, (endTime - startTime) / 1e6);
      return scores;
  }
}
