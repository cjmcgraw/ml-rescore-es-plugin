package com.accretivetg;

import com.google.common.collect.Lists;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.cache.Cache;
import org.elasticsearch.common.cache.CacheBuilder;
import org.elasticsearch.common.unit.TimeValue;


import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.List;
import java.util.concurrent.*;

public class RecommenderClient {
  private static final Logger log = LogManager.getLogger(RecommenderClient.class);
  private static final Cache<String, Float> scoreCache = CacheBuilder
          .<String, Float>builder()
          .setMaximumWeight(2_000_000)
          .build();

  private static RecommenderGrpc.RecommenderBlockingStub recommender;
  private static ManagedChannel channel;

  public static RecommenderClient buildRecommenderClient(String host) {
      boolean needsToCreateGrpcConnection = (
              channel == null ||
              recommender == null ||
              channel.isShutdown() ||
              channel.isTerminated()
      );

      if (needsToCreateGrpcConnection) {
          log.info("setting up grpc connection host=" + host);
          channel = ManagedChannelBuilder
                  .forTarget(host)
                  .usePlaintext()
                  .executor(Executors.newCachedThreadPool())
                  .build();
          recommender = RecommenderGrpc
                  .newBlockingStub(channel)
                  .withWaitForReady()
                  .withExecutor(Executors.newCachedThreadPool());
      } else {
          log.info("using previously configured grpc connection to host=" + host);
      }
    return new RecommenderClient();
  }
  private RecommenderClient() {}

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
                      (PrivilegedAction<RecommenderResponse>) () -> recommender
                              .withDeadlineAfter(250, TimeUnit.MILLISECONDS)
                              .recommend(request)
              );
          } catch (Exception e) {
              log.error(e);
              channel.shutdown();
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
