package org.toskan4134.hytrade.util;

import com.hypixel.hytale.logger.HytaleLogger;
import org.toskan4134.hytrade.TradingPlugin;
import org.toskan4134.hytrade.config.ConfigManager;
import org.toskan4134.hytrade.config.TradeConfig;

/**
 * Common utility class for accessing plugin configuration and shared functionality.
 * Provides convenience methods to avoid repetitive code throughout the plugin.
 */
public class Common {

    private static TradingPlugin plugin;

    /**
     * Initialize the Common utility with the plugin instance.
     * Must be called during plugin startup before using any other methods.
     *
     * @param pluginInstance The main plugin instance
     */
    public static void init(TradingPlugin pluginInstance) {
        plugin = pluginInstance;
    }

    /**
     * Get the plugin instance.
     *
     * @return The main plugin instance
     */
    public static TradingPlugin getPlugin() {
        return plugin;
    }

    /**
     * Get the config manager instance.
     *
     * @return The config manager
     */
    public static ConfigManager getConfigManager() {
        return plugin.getConfigManager();
    }

    /**
     * Get the current trade configuration.
     *
     * @return The trade config
     */
    public static TradeConfig getConfig() {
        return plugin.getConfig().get();
    }

    /**
     * Check if debug mode is enabled.
     *
     * @return true if debug mode is enabled
     */
    public static boolean isDebug() {
        return getConfig().isDebug();
    }

    /**
     * Check if update checking is enabled.
     *
     * @return true if update checking is enabled
     */
    public static boolean isCheckForUpdates() {
        return getConfig().isCheckForUpdates();
    }

    /**
     * Get the trade request timeout in milliseconds.
     *
     * @return Request timeout in ms
     */
    public static long getRequestTimeoutMs() {
        return getConfig().getRequestTimeoutMs();
    }

    /**
     * Get the countdown duration in milliseconds.
     *
     * @return Countdown duration in ms
     */
    public static long getCountdownDurationMs() {
        return getConfig().getCountdownDurationMs();
    }

    /**
     * Get the countdown duration in seconds.
     *
     * @return Countdown duration in seconds
     */
    public static long getCountdownDurationSec() {
        return getConfig().getCountdownDurationMs() / 1000;
    }

    /**
     * Check if a string is null or empty.
     *
     * @param str String to check
     * @return true if null or empty
     */
    public static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * Check if a string is not null and not empty.
     *
     * @param str String to check
     * @return true if not null and not empty
     */
    public static boolean isNotEmpty(String str) {
        return str != null && !str.isEmpty();
    }

    // ===== NULL CHECK UTILITIES =====

    /**
     * Check if an object is null.
     *
     * @param obj Object to check
     * @return true if null
     */
    public static <T> boolean isNull(T obj) {
        return obj == null;
    }

    /**
     * Check if an object is not null.
     *
     * @param obj Object to check
     * @return true if not null
     */
    public static <T> boolean isNotNull(T obj) {
        return obj != null;
    }

    // ===== LOGGER UTILITIES =====

    /**
     * Get a logger for the calling class.
     * Convenience method to reduce boilerplate logger initialization.
     *
     * @return HytaleLogger instance for the calling class
     */
    public static HytaleLogger getLogger() {
        return HytaleLogger.forEnclosingClass();
    }

    /**
     * Log a debug message if debug mode is enabled.
     *
     * @param logger The logger to use
     * @param message The message to log
     */
    public static void logDebug(HytaleLogger logger, String message) {
        if (isDebug()) {
            logger.atInfo().log(message);
        }
    }

    // ===== SAFE EXECUTION UTILITIES =====

    /**
     * Safely execute a runnable, catching and logging any exceptions.
     *
     * @param runnable The runnable to execute
     * @param logger The logger to use for error reporting
     * @param errorMessage The error message to log if execution fails
     */
    public static void safeExecute(Runnable runnable, HytaleLogger logger, String errorMessage) {
        if (runnable == null) {
            return;
        }
        try {
            runnable.run();
        } catch (Exception e) {
            logger.atWarning().withCause(e).log(errorMessage);
        }
    }

    /**
     * Safely execute a runnable, catching and ignoring any exceptions.
     *
     * @param runnable The runnable to execute
     */
    public static void safeExecuteQuietly(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        try {
            runnable.run();
        } catch (Exception ignored) {
            // Silently ignore exceptions
        }
    }
}
