package com.accretivetg.ml.esplugin.models.ranking;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import com.accretivetg.ml.esplugin.ItemIdDataType;
import com.accretivetg.ml.esplugin.MLRescoreContext;
import com.accretivetg.ml.esplugin.MLRescoreMode;
import com.accretivetg.ml.esplugin.StatsD;
import com.accretivetg.ml.esplugin.models.TensorflowServingGrpcClient;
import com.google.common.collect.Maps;
import org.junit.jupiter.api.*;
import org.tensorflow.framework.TensorProto;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

abstract public class TensorflowModelRankerTests {
    private Random random = new Random(1L);

    private StatsD statsd;
    private TensorflowServingGrpcClient client;
    private TensorflowModelRanker ranker;

    @BeforeEach
    public void setupRanker() {
        statsd = mock(StatsD.class);
        client = mock(TensorflowServingGrpcClient.class);
        ranker = getRanker(statsd, client);
    }

    abstract TensorflowModelRanker getRanker(StatsD statsd, TensorflowServingGrpcClient client);

    private MLRescoreContext generateContext() throws Exception {
        return new MLRescoreContext(
                10,
                null,
                "some-type",
                "some-name",
                "model-name",
                "domain",
                ItemIdDataType.STRING,
                null,
                Maps.newHashMap(),
                MLRescoreMode.Replace
        );
    }

    private Map<String, TensorProto> generateOutput(List<Float> scores) {
       return Map.of(
               "scores",
               TensorProto.newBuilder()
               .addAllFloatVal(scores)
               .build()
       );
    }

    @Test
    public void rankerRetrievesExpectedScores() throws Exception {
        var randomItems = IntStream.range(0, 100)
                .mapToObj((x) -> UUID.randomUUID().toString())
                .collect(Collectors.toList());

        List<Float> randomScores = random
                .doubles(100L)
                .mapToObj(x -> (float) x)
                .collect(Collectors.toList());

        when(client.runPrediction(any()))
                .thenReturn(generateOutput(randomScores));

        Map<String, Float> expectedScores = IntStream
                .range(0, 100)
                .boxed()
                .collect(Collectors.toMap(randomItems::get, randomScores::get));

        var context = generateContext();
        Map<String, Float> actualScores = ranker.getScores(
                context,
                randomItems
        );
        assertEquals(expectedScores, actualScores);
    }

    @Test
    public void rankerFailsIfPredictionExplodes() throws Exception {
        var randomItems = IntStream.range(0, 100)
                .mapToObj((x) -> UUID.randomUUID().toString())
                .collect(Collectors.toList());

        when(client.runPrediction(any())).thenThrow(new RuntimeException("kaboom"));
        assertThrows(
                RuntimeException.class,
                () -> ranker.getScores(
                        generateContext(),
                        randomItems
                )
        );
    }
}
