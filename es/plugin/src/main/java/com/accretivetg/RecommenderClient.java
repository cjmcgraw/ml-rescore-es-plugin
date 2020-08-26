package com.accretivetg;

import com.google.common.primitives.Floats;
import com.google.common.primitives.Longs;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.SpecialPermission;


import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;

public class RecommenderClient {
  private static final SecurityManager securityManager = RecommenderClient.getSecurityManager();
  private static final Logger log = LogManager.getLogger(RecommenderClient.class);

  private static final Map<String, RecommenderGrpc.RecommenderBlockingStub> clientMap = new HashMap<>();

  private static SecurityManager getSecurityManager() {
    SecurityManager sm = System.getSecurityManager();
    sm.checkPermission(new SpecialPermission());
    return sm;
  }

  public static RecommenderClient buildRecommenderClient(String host) {
    boolean shouldBuildConnection = !clientMap.containsKey(host);
    if (!shouldBuildConnection) {
      log.info("found existing connection for host: " + host);
      RecommenderGrpc.RecommenderBlockingStub client = clientMap.get(host);
      ManagedChannel channel = (ManagedChannel) client.getChannel();
    } else {
      log.info("Starting new connection with host: " + host);
      ManagedChannel channel = ManagedChannelBuilder
              .forTarget(host)
              .usePlaintext()
              .build();

      RecommenderGrpc.RecommenderBlockingStub stub = RecommenderGrpc.newBlockingStub(channel);
      log.info("finished setup for grpc client with host: " + host);
      clientMap.put(host, stub);
    }
    log.info("found total hosts setup: " + clientMap.size());
    return new RecommenderClient(clientMap.get(host));
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
    log.info("grpc call finished in {}ns", finishedTime - startTime);
    return Floats.toArray(response.getOutputsList());
  }
}
