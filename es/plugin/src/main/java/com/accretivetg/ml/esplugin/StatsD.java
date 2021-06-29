package com.accretivetg.ml.esplugin;

import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Optional;

public class StatsD {
    private static final Logger log = LogManager.getLogger(StatsD.class);
    private static Optional<StatsDClient> statsd = buildStatsDClient();

    private String prefix;

    public StatsD(String ...prefixes) {
        prefix = String.join(".", prefixes);
    }

    public void appendPrefix(String ...prefixes) {
        prefix += "." + String.join(".", prefixes);
    }

    public void gauge(String aspect, double value) {
        statsd.ifPresent(s -> s.gauge(prefix + "." + aspect, value));
    }

    public void increment(String aspect) {
        statsd.ifPresent(s -> s.increment(prefix + "." + aspect));
    }

    public void count(String aspect, long delta) {
        statsd.ifPresent(s -> s.count(prefix + "." + aspect, delta));

    }

    public void time(String aspect, long value) {
        statsd.ifPresent(s -> s.time(prefix + "." + aspect, value));
    }

    public void recordExecutionTime(String aspect, long timeInMs) {
        statsd.ifPresent(s -> s.recordExecutionTime(prefix + "." + aspect, timeInMs));
    }

    private static Optional<StatsDClient> buildStatsDClient() {
        try {
            log.warn("setting up StatsdClient!");
            StatsDClient statsdClient = AccessController.doPrivileged(
                    (PrivilegedAction<StatsDClient>) () ->
                new NonBlockingStatsDClient("ml.mlrescore-plugin.v1", "localhost", 8125)
            );
            return Optional.of(statsdClient);
        } catch (Exception error) {
            String msg = "failed to connect to statsd: cause" + error.getCause().getMessage();
            log.error(msg, error);
            return Optional.empty();
        }
    }

}
