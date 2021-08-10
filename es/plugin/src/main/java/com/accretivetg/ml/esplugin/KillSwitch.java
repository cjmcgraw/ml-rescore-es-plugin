package com.accretivetg.ml.esplugin;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public class KillSwitch {
    private final static Logger log = LogManager.getLogger(KillSwitch.class);
    private final StatsD statsd;
    private final AtomicLongArray errorCounters;
    private final AtomicLongArray successCounters;
    private final AtomicLong errorOccurredAt;
    private final float maximumErrorRate;
    private final int timeWindowInSeconds;
    private final int timeCooldownInSeconds;

    public KillSwitch(StatsD statsd, float maximumErrorRate) {
        this.statsd = statsd;
        this.maximumErrorRate = maximumErrorRate;
        this.timeWindowInSeconds = 10;
        this.timeCooldownInSeconds = 60;
        this.errorOccurredAt = new AtomicLong();
        successCounters = new AtomicLongArray(10);
        errorCounters = new AtomicLongArray(10);
    }

    public void incrementSuccess() {
        int currentBucket = getCurrentBucket();
        int nextBucket = getNextBucket();
        // always clear out our next bucket, otherwise things will increase forever
        successCounters.lazySet(nextBucket, 0);
        successCounters.incrementAndGet(currentBucket);
        statsd.increment("killswitch.success");
    }

    public void incrementError() {
        int currentBucket = getCurrentBucket();
        int nextBucket = getNextBucket();
        // always clear out our next bucket. otherwise things will increase forever
        errorCounters.lazySet(nextBucket, 0);
        errorCounters.incrementAndGet(currentBucket);
        statsd.increment("killswitch.error");
    }

    public boolean isActive() {
        long currentTimeSeconds = (long) (System.currentTimeMillis() / 1000.0);

        long lastErrorOccuredAt = errorOccurredAt.get();
        if (currentTimeSeconds - lastErrorOccuredAt <= timeCooldownInSeconds) {
            statsd.increment("killswitch.on");
            long secondsRemaining = lastErrorOccuredAt + timeCooldownInSeconds - currentTimeSeconds;
            log.error("Killswitch active for next " + secondsRemaining + " seconds");
            return true;
        }

        long sumSuccesses = 0;
        long sumErrors = 0;

        for (int i = 0; i < successCounters.length(); i++) {
            sumSuccesses += successCounters.get(i);
        }

        for (int i = 0; i < errorCounters.length(); i++) {
            sumErrors += errorCounters.get(i);
        }

        // we always expect the next period to be zero and thus cleared out!
        double avgSuccessesInPeriod = sumSuccesses / (successCounters.length() - 1.0);
        double avgErrorsInPeriod = sumErrors / (errorCounters.length() - 1.0);

        boolean killswitchIsActive = (
                (sumSuccesses + sumErrors > 25) &&
                (avgErrorsInPeriod / (avgSuccessesInPeriod + avgErrorsInPeriod) >= maximumErrorRate)
        );

        if (killswitchIsActive) {
            errorOccurredAt.set(currentTimeSeconds);
            statsd.increment("killswitch.activate");
            log.error("Killswitch triggered! Entering cooldown period!");
            for (int i = 0; i < successCounters.length(); i++) {
                this.successCounters.lazySet(i, 0);
            }

            for (int i = 0; i < errorCounters.length(); i++) {
                this.errorCounters.lazySet(i, 0);
            }
            return true;
        }

        return false;
    }

    private int getCurrentBucket() {
        double currentTimeInterval = System.currentTimeMillis() / (1000.0 * timeWindowInSeconds);
        return (int) currentTimeInterval % errorCounters.length();
    }

    private int getNextBucket() {
        return (getCurrentBucket() + 1) % errorCounters.length();
    }
}
