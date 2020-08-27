package com.accretivetg;

import com.google.common.primitives.Floats;
import com.google.common.primitives.Longs;
import io.grpc.ManagedChannelBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.SpecialPermission;


import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class RecommenderClient {
  private static final SecurityManager securityManager = RecommenderClient.getSecurityManager();
  private static final Logger log = LogManager.getLogger(RecommenderClient.class);
  private static final Random rand = new Random(1L);

  private static final Map<String, RecommenderGrpc.RecommenderBlockingStub> clientMap = new HashMap<>();

  private static SecurityManager getSecurityManager() {
    SecurityManager sm = System.getSecurityManager();
    sm.checkPermission(new SpecialPermission());
    return sm;
  }

  public static RecommenderClient buildRecommenderClient(String host) {
    String key = Math.abs(rand.nextInt() % 10) + "," + host;
    log.info("searching for grpc client with key={} host={}", key, host);
    RecommenderGrpc.RecommenderBlockingStub stub = clientMap
            .computeIfAbsent(key,
                    (k) -> {
              log.info("unable to find current stub key={}", key);
              log.info("setting up grpc connection");
              return RecommenderGrpc.newBlockingStub(
                      ManagedChannelBuilder
                              .forTarget(host)
                              .usePlaintext()
                              .build()
              );
            }
          );

    return new RecommenderClient(stub);
  }

  private final RecommenderGrpc.RecommenderBlockingStub recommender;
  private RecommenderClient(RecommenderGrpc.RecommenderBlockingStub recommender) {
    this.recommender = recommender;
  }

  public float[] score(long[] exampleIds, String context) {
    log.info("running recommendation with " + exampleIds.length + " exampleids");
    RecommenderRequest request = RecommenderRequest
            .newBuilder()
            .setContext(context)
            .addAllExampleids(Longs.asList(exampleIds))
            .build();

    long startTime = System.nanoTime();
    RecommenderResponse response = AccessController.doPrivileged(
      new PrivilegedAction<RecommenderResponse>() {
        @Override
        public RecommenderResponse run() {
          return recommender.recommend(request);
        }
      }
    );
    long finishedTime = System.nanoTime();
    log.info("grpc call finished in {} ms", (finishedTime - startTime) / 1e6);
    return Floats.toArray(response.getOutputsList());
  }
}
