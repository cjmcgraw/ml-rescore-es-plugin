package com.accretivetg.ml.esplugin.models.ranking;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import com.accretivetg.ml.esplugin.InvalidModelInputException;
import com.accretivetg.ml.esplugin.MLRescoreContext;
import com.accretivetg.ml.esplugin.StatsD;
import com.accretivetg.ml.esplugin.TestUtils;
import com.accretivetg.ml.esplugin.models.MLModel;
import org.elasticsearch.rest.RestStatus;
import org.junit.jupiter.api.*;

public class InvalidContextCacheWrappedRankerTests {
    private MLModel mockRanker;
    private MLModel cachedRanker;

    @BeforeEach
    public void setupWrappedRanker() {
       mockRanker = mock(MLModel.class);
       var statsD = mock(StatsD.class);
       doReturn(statsD).when(mockRanker).getStatsd();
       cachedRanker = new InvalidContextCacheWrappedRanker(mockRanker);
    }

    @Test
    public void passesThroughWhenThereAreNoErrors() {
        for (String hash : TestUtils.generateRandomStringList(25)) {
            var ctx = mock(MLRescoreContext.class);
            doReturn(hash).when(mockRanker).getContextHash(ctx);
            var expected = TestUtils.generateRandomRankerScores(10);
            var itemIds = TestUtils.generateRandomStringList(25);
            doReturn(expected).when(mockRanker).getScores(ctx, itemIds);
            var actual = cachedRanker.getScores(ctx, itemIds);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void onlyCallsRankerFirstTimeOnFailure() {
        var successCtx = mock(MLRescoreContext.class);
        doReturn("hash_success").when(mockRanker).getContextHash(successCtx);
        var successExpected = TestUtils.generateRandomRankerScores(10);
        var successItemIds = TestUtils.generateRandomStringList(25);
        doReturn(successExpected)
                .when(mockRanker)
                .getScores(successCtx, successItemIds);
        var successActual = cachedRanker.getScores(successCtx, successItemIds);
        assertEquals(successExpected, successActual);

        var failureCtx = mock(MLRescoreContext.class);
        doReturn("hash_failure").when(mockRanker).getContextHash(failureCtx);
        var failureItemIds = TestUtils.generateRandomStringList(25);
        var expectedException = new InvalidModelInputException("failure!", RestStatus.BAD_REQUEST);
        doThrow(expectedException)
                .when(mockRanker)
                .getScores(failureCtx, failureItemIds);
        assertThrows(
                InvalidModelInputException.class,
                () -> cachedRanker.getScores(failureCtx, failureItemIds)
        );

        var secondSuccessCtx = mock(MLRescoreContext.class);
        doReturn("hash_success_2").when(mockRanker).getContextHash(secondSuccessCtx);
        var secondSuccessExpected = TestUtils.generateRandomRankerScores(10);
        var secondSuccessItemIds = TestUtils.generateRandomStringList(25);
        doReturn(secondSuccessExpected)
                .when(mockRanker)
                .getScores(secondSuccessCtx, secondSuccessItemIds);
        var secondSuccessActual = cachedRanker.getScores(secondSuccessCtx, secondSuccessItemIds);
        assertEquals(secondSuccessExpected, secondSuccessActual);

        doReturn("hash_failure").when(mockRanker).getContextHash(failureCtx);
        doThrow(new RuntimeException("random exception")).when(mockRanker).getScores(any(), any());
        assertThrows(
                InvalidModelInputException.class,
                () -> cachedRanker.getScores(failureCtx, failureItemIds)
        );
    }
}
