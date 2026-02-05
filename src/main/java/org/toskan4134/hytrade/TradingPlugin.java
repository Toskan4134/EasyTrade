package org.toskan4134.hytrade;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import org.toskan4134.hytrade.command.TradeCommand;
import org.toskan4134.hytrade.events.InventoryChangeListener;
import org.toskan4134.hytrade.events.PlayerDisconnectListener;
import org.toskan4134.hytrade.trade.TradeManager;

import javax.annotation.Nonnull;

/**
 * Main plugin class for the Trading system.
 * Provides player-to-player item trading with atomic transactions.
 */
public class TradingPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private TradeManager tradeManager;

    public TradingPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Trading Plugin v" + this.getManifest().getVersion().toString() + " loading...");
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up Trading plugin");

        // Initialize trade manager
        this.tradeManager = new TradeManager(this);

        // Register command
        this.getCommandRegistry().registerCommand(
            new TradeCommand(this.getName(), this.getManifest().getVersion().toString(), this, tradeManager)
        );
        LOGGER.atInfo().log("Registered /trade command");

        // Register event listeners
        PlayerDisconnectListener disconnectListener = new PlayerDisconnectListener(tradeManager);
        disconnectListener.register(this.getEventRegistry());

        InventoryChangeListener inventoryListener = new InventoryChangeListener(tradeManager);
        inventoryListener.register(this.getEventRegistry());

        LOGGER.atInfo().log("Trading plugin setup complete!");
    }

    public TradeManager getTradeManager() {
        return tradeManager;
    }
}
