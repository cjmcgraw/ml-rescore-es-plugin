package com.accretivetg.ml.esplugin;

import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TestUtils {
    public static List<Integer> generateRandomIntegerList(int size) {
        Random rand = new Random();
        return IntStream
                .range(0, size)
                .map(x -> rand.nextInt())
                .boxed()
                .collect(Collectors.toUnmodifiableList());
    }

    public static List<Float> generateRandomFloatList(int size) {
        Random rand = new Random();
        return IntStream
                .range(0, size)
                .mapToObj(x -> rand.nextFloat())
                .collect(Collectors.toUnmodifiableList());
    }

    public static List<String> generateRandomStringList(int size) {
        return IntStream
                .range(0, size)
                .mapToObj((x) -> UUID.randomUUID().toString())
                .collect(Collectors.toUnmodifiableList());
    }

    public static Map<String, Float> generateRandomRankerScores(int size) {
        var randomStrings = generateRandomStringList(size);
        var randomScores = generateRandomFloatList(size);
        return IntStream
                .range(0, size)
                .boxed()
                .collect(Collectors.toMap(randomStrings::get, randomScores::get));
    }
}
