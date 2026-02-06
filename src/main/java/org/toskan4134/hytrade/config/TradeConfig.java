package org.toskan4134.hytrade.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import static org.toskan4134.hytrade.constants.TradeConstants.*;

/**
 * Configuration for the HyTrade plugin.
 * Uses Hytale's Codec system for JSON serialization.
 */
public class TradeConfig {

    // Codec definition for serialization/deserialization
    public static final BuilderCodec<TradeConfig> CODEC = BuilderCodec.builder(TradeConfig.class, TradeConfig::new)
            .append(new KeyedCodec<>("CountdownDuration", Codec.INTEGER),
                    (config, value, info) -> config.countdownDuration = value,
                    (config, info) -> config.countdownDuration)
            .add()
            .append(new KeyedCodec<>("RequestTimeout", Codec.INTEGER),
                    (config, value, info) -> config.requestTimeout = value,
                    (config, info) -> config.requestTimeout)
            .add()
            .append(new KeyedCodec<>("CheckForUpdates", Codec.BOOLEAN),
                    (config, value, info) -> config.checkForUpdates = value,
                    (config, info) -> config.checkForUpdates)
            .add()
            .append(new KeyedCodec<>("Debug", Codec.BOOLEAN),
                    (config, value, info) -> config.debug = value,
                    (config, info) -> config.debug)
            .add()

            .build();

    private int countdownDuration = COUNTDOWN_DURATION_MS; // in milliseconds
    private int requestTimeout = REQUEST_TIMEOUT_MS; // in milliseconds
    private boolean checkForUpdates = CHECK_FOR_UPDATES;
    private boolean debug = DEBUG;

    public TradeConfig() {
    }

    public int getCountdownDuration() {
        return countdownDuration;
    }

    public long getCountdownDurationMs() {
        return (long) countdownDuration;
    }

    public int getRequestTimeout() {
        return requestTimeout;
    }

    public long getRequestTimeoutMs() {
        return (long) requestTimeout;
    }

    public void setCountdownDuration(int countdownDuration) {
        this.countdownDuration = Math.max(1000, Math.min(60000, countdownDuration)); // 1s to 60s range
    }

    public void setRequestTimeout(int requestTimeout) {
        this.requestTimeout = Math.max(5000, Math.min(300000, requestTimeout)); // 5s to 5min range
    }

    public boolean isCheckForUpdates() {
        return checkForUpdates;
    }

    public void setCheckForUpdates(boolean checkForUpdates) {
        this.checkForUpdates = checkForUpdates;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }
}
