package org.toskan4134.hytrade.events;

import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.toskan4134.hytrade.trade.TradeManager;
import org.toskan4134.hytrade.util.Common;

/**
 * Listens for player disconnect events to properly cancel trades
 * and prevent item duplication or loss.
 */
public class PlayerDisconnectListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TradeManager tradeManager;

    public PlayerDisconnectListener(TradeManager tradeManager) {
        this.tradeManager = tradeManager;
    }

    /**
     * Register this listener with the event registry.
     */
    public void register(IEventRegistry registry) {
        registry.register(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        Common.logDebug(LOGGER, "PlayerDisconnectListener registered");
    }

    /**
     * Handle player disconnect - cancel any active trades.
     */
    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef player = event.getPlayerRef();
        if (player != null) {
            Common.logDebug(LOGGER, "Player disconnected, checking for active trades");
            tradeManager.onPlayerDisconnect(player);
        }
    }
}
