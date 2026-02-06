package org.toskan4134.hytrade;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import org.toskan4134.hytrade.command.TradeCommand;
import org.toskan4134.hytrade.config.ConfigManager;
import org.toskan4134.hytrade.config.TradeConfig;
import org.toskan4134.hytrade.events.InventoryChangeListener;
import org.toskan4134.hytrade.events.PlayerDisconnectListener;
import org.toskan4134.hytrade.events.PlayerJoinListener;
import org.toskan4134.hytrade.messages.TradeMessages;
import org.toskan4134.hytrade.trade.TradeManager;
import org.toskan4134.hytrade.util.Common;
import org.toskan4134.hytrade.util.VersionChecker;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.toskan4134.hytrade.constants.TradeConstants.UPDATE_CHECK_INTERVAL_HOURS;

/**
 * Main plugin class for the Trading system.
 * Provides player-to-player item trading with atomic transactions.
 */
public class TradingPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final Config<TradeConfig> config;
    private ConfigManager configManager;
    @Nullable
    private VersionChecker versionChecker;
    @Nullable
    private PlayerJoinListener playerJoinListener;
    @Nullable
    private ScheduledExecutorService updateCheckScheduler;
    @Nullable
    private ScheduledFuture<?> updateCheckTask;
    private TradeManager tradeManager;

    public TradingPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        this.config = this.withConfig("HyTrade", TradeConfig.CODEC);

        LOGGER.atInfo().log("Trading Plugin v" + this.getManifest().getVersion().toString() + " loading...");
    }

    @Override
    protected void setup() {
        // Initialize config manager and messages
        File dataFolder = new File("plugins/HyTrade");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        this.configManager = new ConfigManager(this.config, dataFolder);
        TradeMessages.init(configManager);

        // Initialize common utilities
        Common.init(this);

        Common.logDebug(LOGGER, "Setting up Trading plugin");

        // Initialize trade manager
        this.tradeManager = new TradeManager(this);

        // Register command
        this.getCommandRegistry().registerCommand(
            new TradeCommand(this.getName(), this.getManifest().getVersion().toString(), this, tradeManager)
        );
        Common.logDebug(LOGGER, "Registered /trade command");

        // Register event listeners
        PlayerDisconnectListener disconnectListener = new PlayerDisconnectListener(tradeManager);
        disconnectListener.register(this.getEventRegistry());

        InventoryChangeListener inventoryListener = new InventoryChangeListener(tradeManager);
        inventoryListener.register(this.getEventRegistry());

        // Save config to create file with defaults if it doesn't exist
        config.save();

        // Check for updates if enabled (initial check + every 12 hours)
        if (Common.isCheckForUpdates()) {
            startUpdateChecker();
        }

        LOGGER.atInfo().log("Trading plugin setup complete!");
    }

    private void startUpdateChecker() {
        // Create scheduler for periodic checks
        updateCheckScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HyTrade-UpdateChecker");
            t.setDaemon(true);
            return t;
        });

        // Run initial check immediately, then every 12 hours
        updateCheckTask = updateCheckScheduler.scheduleAtFixedRate(
                this::checkForUpdates,
                0, // Initial delay
                UPDATE_CHECK_INTERVAL_HOURS,
                TimeUnit.HOURS
        );

        getLogger().atInfo().log("Update checker started (checks every " + UPDATE_CHECK_INTERVAL_HOURS + " hours)");
    }

    /**
     * Checks for plugin updates asynchronously.
     * Logs to console if an update is available and registers
     * a listener to notify operators when they join.
     */
    private void checkForUpdates() {
        String currentVersion = this.getManifest().getVersion().toString();

        versionChecker = new VersionChecker(currentVersion);

        // Check for updates asynchronously
        versionChecker.checkForUpdatesAsync().thenAccept(checker -> {
            if (checker.isUpdateAvailable()) {
                // Log to console
                String consoleMessage = checker.getConsoleMessage();
                if (consoleMessage != null) {
                    getLogger().atWarning().log(consoleMessage);
                }

                // Register player join listener for operator notifications (only once)
                if (playerJoinListener == null) {
                    playerJoinListener = new PlayerJoinListener(checker);
                    playerJoinListener.register(this);
                }
            }
        }).exceptionally(ex -> {
            getLogger().atWarning().log("Failed to check for updates: " + ex.getMessage());
            return null;
        });
    }

    @Override
    protected void shutdown() {
        getLogger().atInfo().log("Shutting down " + this.getName());

        // Stop update checker
        if (updateCheckTask != null) {
            updateCheckTask.cancel(false);
            updateCheckTask = null;
        }
        if (updateCheckScheduler != null) {
            updateCheckScheduler.shutdown();
            updateCheckScheduler = null;
        }

        getLogger().atInfo().log(this.getName() + " shutdown complete!");
    }


    public TradeManager getTradeManager() {
        return tradeManager;
    }

    public Config<TradeConfig> getConfig() {
        return config;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
