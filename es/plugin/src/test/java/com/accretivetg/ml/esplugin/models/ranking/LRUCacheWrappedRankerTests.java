package com.accretivetg.ml.esplugin.models.ranking;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import com.accretivetg.ml.esplugin.MLRescoreContext;
import com.accretivetg.ml.esplugin.TestUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class LRUCacheWrappedRankerTests {
    private RandomRanker ranker;
    private LRUCacheWrappedRanker cachedRanker;

    @BeforeEach
    public void setupWrappedRanker() {
        ranker = new RandomRanker(1L);
        cachedRanker = new LRUCacheWrappedRanker(ranker);
    }

    @Test
    public void testCacheDoesntStopFirstCall() {
        Random r = new Random(2L);
        MLRescoreContext ctx = mock(MLRescoreContext.class);
        List<String> itemIds = TestUtils.generateRandomStringList(25);
        doReturn("hash1").when(ctx).getModelContextAsJson();
        Map<String, Float> scores = cachedRanker.getScores(ctx, itemIds);
        assertEquals(Sets.newHashSet("hash1"), ranker.uniqueContextsSeen);
        assertEquals(Sets.newHashSet(itemIds), ranker.uniqueItemIdsSeen);
        assertEquals(1, ranker.numberOfCalls);
        for(String id : itemIds) {
            assertTrue(scores.containsKey(id));
        }
    }

    @Test
    public void testCacheStopsMultipleCallsWithSameContext() {
        Random r = new Random(3L);
        MLRescoreContext ctx = mock(MLRescoreContext.class);
        List<String> itemIds = TestUtils.generateRandomStringList(25);
        doReturn("hash1").when(ctx).getModelContextAsJson();

        Map<String, Float> oldScores = cachedRanker.getScores(ctx, itemIds);
        Map<String, Float> newScores;
        for (int i = 0; i < 30; i++) {
           newScores = cachedRanker.getScores(ctx, itemIds);
           assertEquals(oldScores, newScores);
           assertEquals(1, ranker.numberOfCalls);
        }
        assertEquals(1, ranker.numberOfCalls);
        assertEquals(Sets.newHashSet("hash1"), ranker.uniqueContextsSeen);
        assertEquals(Sets.newHashSet(itemIds), ranker.uniqueItemIdsSeen);
    }

    @Test
    public void testCacheFallsThroughWhenDifferentContextsUsed() {
        Random r = new Random(4L);
        MLRescoreContext ctx = mock(MLRescoreContext.class);
        List<String> itemIds = TestUtils.generateRandomStringList(25);

        List<Map<String, Float>> scores = Lists.newArrayList();
        List<String> contextHashes = TestUtils.generateRandomStringList(100);

        for(String hash : contextHashes) {
            doReturn(hash).when(ctx).getModelContextAsJson();
            scores.add(cachedRanker.getScores(ctx, itemIds));
        }

        assertEquals(contextHashes.size(), ranker.numberOfCalls);
        assertEquals(Sets.newHashSet(itemIds), ranker.uniqueItemIdsSeen);
        assertEquals(Sets.newHashSet(contextHashes), ranker.uniqueContextsSeen);

        ranker.numberOfCalls = 0;
        List<Map<String, Float>> secondRoundScores = Lists.newArrayList();
        for(String hash : contextHashes) {
            doReturn(hash).when(ctx).getModelContextAsJson();
            secondRoundScores.add(cachedRanker.getScores(ctx, itemIds));
        }

        assertEquals(0, ranker.numberOfCalls);
        assertEquals(scores, secondRoundScores);
    }

    @Test
    public void testOnlyScoresItemIdsThatAreMissing() {
        Random r = new Random(5L);
        MLRescoreContext ctx = mock(MLRescoreContext.class);
        doReturn("hash1").when(ctx).getModelContextAsJson();

        List<String> itemIds = TestUtils.generateRandomStringList(25);
        Map<String, Float> originalScores = cachedRanker.getScores(ctx, itemIds);
        assertEquals(1, ranker.numberOfCalls);
        assertEquals(Sets.newHashSet(itemIds), ranker.uniqueItemIdsSeen);

        ranker.uniqueItemIdsSeen = Sets.newHashSet();
        List<String> additionalItemIds = TestUtils.generateRandomStringList(25);
        List<String> moreItemIds = Lists.newArrayList(itemIds);
        moreItemIds.addAll(additionalItemIds);
        Map<String, Float> newScores = cachedRanker.getScores(ctx, moreItemIds);
        assertEquals(Sets.newHashSet(additionalItemIds), ranker.uniqueItemIdsSeen);

        for (Map.Entry<String, Float> entry : originalScores.entrySet()) {
            String originalId = entry.getKey();
            float originalScore = entry.getValue();
            float newScore = newScores.get(originalId);
            assertEquals(originalScore, newScore);
        }
    }
}
