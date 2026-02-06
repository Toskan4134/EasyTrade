package org.toskan4134.hytrade.events;

import com.hypixel.hytale.event.IEventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.toskan4134.hytrade.trade.TradeManager;
import org.toskan4134.hytrade.util.Common;
import org.toskan4134.hytrade.trade.TradeSession;
import org.toskan4134.hytrade.trade.TradeState;

import java.util.Optional;

/**
 * Listens for inventory change events to detect when players
 * modify their inventory while in a trade.
 */
public class InventoryChangeListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TradeManager tradeManager;

    public InventoryChangeListener(TradeManager tradeManager) {
        this.tradeManager = tradeManager;
    }

    /**
     * Register this listener with the event registry.
     */
    public void register(IEventRegistry registry) {
        // Use registerGlobal to receive all inventory change events (for keyed events)
        registry.registerGlobal(LivingEntityInventoryChangeEvent.class, this::onInventoryChange);
        Common.logDebug(LOGGER, "InventoryChangeListener registered");
    }

    /**
     * Handle inventory change - notify trading page if player is in a trade.
     */
    private void onInventoryChange(LivingEntityInventoryChangeEvent event) {
        LivingEntity entity = event.getEntity();

        // Only process player inventory changes
        if (!(entity instanceof Player player)) {
            return;
        }

        PlayerRef playerRef = player.getPlayerRef();

        if (playerRef == null) {
            return;
        }

        // Check if player is in a trade
        if (!tradeManager.isInTrade(playerRef)) {
            return;
        }

        // Skip processing during trade execution to avoid infinite recursion
        Optional<TradeSession> optSession = tradeManager.getSession(playerRef);
        if (optSession.isPresent()) {
            TradeState state = optSession.get().getState();
            if (state == TradeState.EXECUTING || state == TradeState.COMPLETED ||
                state == TradeState.FAILED || state == TradeState.CANCELLED) {
                // Trade is in final phase, ignore inventory changes
                return;
            }
        }

        tradeManager.onPlayerInventoryChanged(playerRef);
    }
}
