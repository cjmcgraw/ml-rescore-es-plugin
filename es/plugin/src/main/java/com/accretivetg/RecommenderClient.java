package com.accretivetg;

import com.google.common.primitives.Floats;
import com.google.common.primitives.Longs;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.logging.Logger;

public class RecommenderClient {
  private static final Logger log = Logger.getLogger(RecommenderClient.class.getName());
  private static ManagedChannel channel;
  private static RecommenderGrpc.RecommenderBlockingStub recommender;
  private static RecommenderClient instance;

  public static RecommenderClient buildRecommenderClient(String host) {
    if (channel == null) {
      log.info("setting up channel for target: " + host);
      channel = ManagedChannelBuilder
              .forTarget(host)
              .usePlaintext()
              .build();
    }

    if (recommender == null) {
      log.info("creating recommender connection");
      recommender = RecommenderGrpc.newBlockingStub(channel);
    }

    if (instance == null) {
      instance = new RecommenderClient();
    }
    return instance;
  }

  private RecommenderClient() {}

  public float[] score(long[] exampleIds, String context) {
    RecommenderRequest request = RecommenderRequest
            .newBuilder()
            .setContext(context)
            .addAllExampleids(Longs.asList(exampleIds))
            .build();
    log.info("running recommendation with request: " + request);
    RecommenderResponse response = recommender.recommend(request);
    log.info("received response: " + response);
    return Floats.toArray(response.getOutputsList());
  }
}
