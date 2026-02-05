package org.toskan4134.hytrade.trade;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.toskan4134.hytrade.TradingPlugin;
import org.toskan4134.hytrade.constants.TradeConstants;
import org.toskan4134.hytrade.ui.TradingPage;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Manages all active trade sessions in the server.
 * Handles trade requests, session lifecycle, and cleanup.
 */
public class TradeManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TradingPlugin plugin;

    // Active trade sessions (sessionId -> session)
    private final Map<UUID, TradeSession> activeSessions;

    // Player UUID to session mapping for quick lookup
    private final Map<UUID, UUID> playerToSession;

    // Pending trade requests (target player UUID -> session)
    private final Map<UUID, TradeSession> pendingRequests;

    // Scheduler for timeouts and countdowns
    private final ScheduledExecutorService scheduler;

    // Active trading pages for UI updates (player UUID -> callback)
    private final Map<UUID, Runnable> activeTradingPages = new ConcurrentHashMap<>();
    // Player UUID -> PlayerRef mapping for logging
    private final Map<UUID, PlayerRef> tradingPagePlayers = new ConcurrentHashMap<>();
    // Player UUID -> EntityRef mapping for trade execution
    private final Map<UUID, Ref<EntityStore>> playerEntityRefs = new ConcurrentHashMap<>();
    // Player UUID -> TradingPage for status updates
    private final Map<UUID, StatusUpdateCallback> tradingPageStatusCallbacks = new ConcurrentHashMap<>();
    // Player UUID -> TradingPage instance for closing both UIs
    private final Map<UUID, org.toskan4134.hytrade.ui.TradingPage> tradingPageInstances = new ConcurrentHashMap<>();

    /**
     * Callback interface for updating trading page status.
     */
    public interface StatusUpdateCallback {
        void setStatus(String message, String color);
    }

    public TradeManager(TradingPlugin plugin) {
        this.plugin = plugin;
        this.activeSessions = new ConcurrentHashMap<>();
        this.playerToSession = new ConcurrentHashMap<>();
        this.pendingRequests = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2);
    }


    /**
     * Register a trading page callback for inventory change notifications.
     * Also stores the entity ref for later trade execution.
     */
    public void registerTradingPage(PlayerRef player, Runnable onInventoryChange, Ref<EntityStore> entityRef) {
        registerTradingPage(player, onInventoryChange, entityRef, null, null);
    }

    /**
     * Register a trading page with status update callback.
     */
    public void registerTradingPage(PlayerRef player, Runnable onInventoryChange,
                                     Ref<EntityStore> entityRef, StatusUpdateCallback statusCallback,
                                     org.toskan4134.hytrade.ui.TradingPage tradingPage) {
        UUID playerId = player.getUuid();
        activeTradingPages.put(playerId, onInventoryChange);
        tradingPagePlayers.put(playerId, player);
        if (entityRef != null) {
            playerEntityRefs.put(playerId, entityRef);
        }
        if (statusCallback != null) {
            tradingPageStatusCallbacks.put(playerId, statusCallback);
        }
        if (tradingPage != null) {
            tradingPageInstances.put(playerId, tradingPage);
        }
        LOGGER.atInfo().log("Registered trading page for " + player.getUsername() + " (UUID: " + playerId + ")");
    }

    /**
     * Unregister a trading page callback
     */
    public void unregisterTradingPage(PlayerRef player) {
        UUID playerId = player.getUuid();
        activeTradingPages.remove(playerId);
        tradingPagePlayers.remove(playerId);
        playerEntityRefs.remove(playerId);
        tradingPageStatusCallbacks.remove(playerId);
        tradingPageInstances.remove(playerId);
        LOGGER.atInfo().log("Unregistered trading page for " + player.getUsername() + " (UUID: " + playerId + ")");
    }

    /**
     * Notify partner's trading page with a status message.
     * @param player The player whose partner should be notified
     * @param message The status message
     * @param color The color (use TradingPage.getColor* methods)
     */
    public void notifyPartnerStatus(PlayerRef player, String message, String color) {
        Optional<TradeSession> optSession = getSession(player);
        if (optSession.isEmpty()) return;

        TradeSession session = optSession.get();
        if (session.isTestMode()) return; // In test mode, same player is both

        PlayerRef partner = session.getOtherPlayer(player);
        if (partner == null) return;

        UUID partnerId = partner.getUuid();
        StatusUpdateCallback callback = tradingPageStatusCallbacks.get(partnerId);
        if (callback != null) {
            try {
                callback.setStatus(message, color);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Error updating partner's status");
            }
        }
    }

    /**
     * Notify both players' trading pages to refresh their UI.
     * Called when trade state changes (offers, accepts, etc.)
     */
    public void notifyBothTradingPages(TradeSession session) {
        if (session == null) return;

        // Notify initiator's UI
        UUID initiatorId = session.getInitiator().getUuid();
        Runnable initiatorCallback = activeTradingPages.get(initiatorId);
        if (initiatorCallback != null) {
            try {
                initiatorCallback.run();
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Error notifying initiator's trading page");
            }
        }

        // Notify target's UI (if different player - not test mode)
        if (!session.isTestMode()) {
            UUID targetId = session.getTarget().getUuid();
            Runnable targetCallback = activeTradingPages.get(targetId);
            if (targetCallback != null) {
                try {
                    targetCallback.run();
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Error notifying target's trading page");
                }
            }
        }
    }

    /**
     * Notify both players' trading pages based on a player in the trade.
     */
    public void notifyBothTradingPages(PlayerRef player) {
        Optional<TradeSession> optSession = getSession(player);
        optSession.ifPresent(this::notifyBothTradingPages);
    }

    /**
     * Close both players' trading UIs when a trade ends.
     * Called when a trade is completed or cancelled.
     */
    public void closeBothTradingPages(TradeSession session) {
        if (session == null) return;

        // Close initiator's UI
        UUID initiatorId = session.getInitiator().getUuid();
        org.toskan4134.hytrade.ui.TradingPage initiatorPage = tradingPageInstances.get(initiatorId);
        if (initiatorPage != null) {
            try {
                initiatorPage.closeUI();
                LOGGER.atInfo().log("Closed trading UI for initiator " + session.getInitiator().getUsername());
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Error closing initiator's trading page");
            }
        }

        // Close target's UI (if different player - not test mode)
        if (!session.isTestMode()) {
            UUID targetId = session.getTarget().getUuid();
            org.toskan4134.hytrade.ui.TradingPage targetPage = tradingPageInstances.get(targetId);
            if (targetPage != null) {
                try {
                    targetPage.closeUI();
                    LOGGER.atInfo().log("Closed trading UI for target " + session.getTarget().getUsername());
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Error closing target's trading page");
                }
            }
        }
    }

    /**
     * Called when a player's inventory changes while in a trade.
     * Handles auto-unaccept and offer validation directly, regardless of UI state.
     */
    public void onPlayerInventoryChanged(PlayerRef player) {
        UUID playerId = player.getUuid();

        // First, handle the trade logic directly (regardless of UI state)
        Optional<TradeSession> optSession = getSession(player);
        if (optSession.isPresent()) {
            TradeSession session = optSession.get();

            // Auto-unaccept if player had accepted
            if (session.hasAccepted(player)) {
                boolean revoked = session.revokeAccept(player);
                if (revoked) {
                    // Use UI status instead of chat
                    StatusUpdateCallback callback = tradingPageStatusCallbacks.get(playerId);
                    if (callback != null) {
                        callback.setStatus("Acceptance revoked - inventory changed", "#ffcc00");
                    }
                    // Notify partner
                    notifyPartnerStatus(player, "Partner's acceptance revoked", "#ffcc00");
                }
            }

            // Notify BOTH players' UIs about the change
            notifyBothTradingPages(session);
        } else {
            // Player not in a trade, but still notify their own UI if they have one open
            Runnable callback = activeTradingPages.get(playerId);
            if (callback != null) {
                try {
                    callback.run();
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Error handling inventory change callback for " + player.getUsername());
                }
            }
        }
    }

    // ===== TEST MODE =====

    /**
     * Start a test trade session for solo development.
     * Creates a simulated trade where the player acts as both parties.
     */
    public TradeRequestResult startTestSession(PlayerRef player) {
        // Check if player is already in a trade
        if (isInTrade(player)) {
            return new TradeRequestResult(false, "You are already in a trade");
        }

        // Create test session (player is both initiator and target)
        TradeSession session = new TradeSession(player, player, true);
        session.acceptRequest(); // Auto-accept in test mode

        // Register as active session
        activeSessions.put(session.getSessionId(), session);
        playerToSession.put(player.getUuid(), session.getSessionId());

        LOGGER.atInfo().log("Test trade session " + session.getSessionId() + " started");

        return new TradeRequestResult(true, "Test session started", session);
    }

    // ===== TRADE REQUEST FLOW =====

    /**
     * Initiate a trade request from one player to another.
     */
    public TradeRequestResult requestTrade(PlayerRef initiator, PlayerRef target) {
        UUID initiatorId = initiator.getUuid();
        UUID targetId = target.getUuid();

        // Check if initiator is already in a trade
        if (isInTrade(initiator)) {
            return new TradeRequestResult(false, "You are already in a trade");
        }

        // Check if target is already in a trade
        if (isInTrade(target)) {
            return new TradeRequestResult(false, "That player is already in a trade");
        }

        // Check if there's already a pending request to this target
        if (pendingRequests.containsKey(targetId)) {
            TradeSession existingRequest = pendingRequests.get(targetId);
            if (existingRequest.getInitiator().getUuid().equals(initiatorId)) {
                return new TradeRequestResult(false, "You already have a pending request to this player");
            }
        }

        // Check if target has sent a request to initiator
        TradeSession reverseRequest = pendingRequests.get(initiatorId);
        if (reverseRequest != null && reverseRequest.getInitiator().getUuid().equals(targetId)) {
            // Auto-accept the reverse request
            return acceptTradeRequest(initiator);
        }

        // Create new trade session
        TradeSession session = new TradeSession(initiator, target);
        pendingRequests.put(targetId, session);

        // Schedule timeout
        scheduler.schedule(() -> {
            TradeSession pending = pendingRequests.remove(targetId);
            if (pending != null && pending.equals(session)) {
                initiator.sendMessage(Message.raw("Trade request to player timed out"));
                target.sendMessage(Message.raw("Trade request from player expired"));
            }
        }, TradeConstants.REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        LOGGER.atInfo().log("Trade request created: " + session.getSessionId());

        return new TradeRequestResult(true, "Trade request sent",session);
    }

    /**
     * Accept a pending trade request.
     */
    public TradeRequestResult acceptTradeRequest(PlayerRef target) {
        UUID targetId = target.getUuid();
        TradeSession session = pendingRequests.remove(targetId);
        if (session == null) {
            return new TradeRequestResult(false, "No pending trade request");
        }

        // Move to active session
        session.acceptRequest();
        activeSessions.put(session.getSessionId(), session);
        playerToSession.put(session.getInitiator().getUuid(), session.getSessionId());
        playerToSession.put(targetId, session.getSessionId());

        LOGGER.atInfo().log("Trade session " + session.getSessionId() + " is now active");

        return new TradeRequestResult(true, "Trade started", session);
    }

    /**
     * Decline a pending trade request.
     */
    public boolean declineTradeRequest(PlayerRef target) {
        TradeSession session = pendingRequests.remove(target.getUuid());
        if (session == null) {
            return false;
        }

        session.getInitiator().sendMessage(Message.raw("Your trade request was declined"));
        target.sendMessage(Message.raw("Trade request declined"));

        LOGGER.atInfo().log("Trade request " + session.getSessionId() + " was declined");
        return true;
    }

    // ===== ACTIVE TRADE MANAGEMENT =====

    /**
     * Get a player's active trade session.
     */
    public Optional<TradeSession> getSession(PlayerRef player) {
        UUID sessionId = playerToSession.get(player.getUuid());
        if (sessionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(activeSessions.get(sessionId));
    }

    /**
     * Get a session by ID.
     */
    public Optional<TradeSession> getSessionById(UUID sessionId) {
        return Optional.ofNullable(activeSessions.get(sessionId));
    }

    /**
     * Check if a player is in an active trade.
     */
    public boolean isInTrade(PlayerRef player) {
        return playerToSession.containsKey(player.getUuid());
    }

    /**
     * Check if player has a pending request.
     */
    public boolean hasPendingRequest(PlayerRef player) {
        return pendingRequests.containsKey(player.getUuid());
    }

    /**
     * Get pending request for a player.
     */
    public Optional<TradeSession> getPendingRequest(PlayerRef target) {
        return Optional.ofNullable(pendingRequests.get(target.getUuid()));
    }

    // ===== TRADE ACTIONS =====

    /**
     * Called when a player's offer changes.
     * Notifies both trading pages to refresh their UI.
     */
    public void onOfferChanged(PlayerRef player) {
        Optional<TradeSession> optSession = getSession(player);
        if (optSession.isPresent()) {
            TradeSession session = optSession.get();
            session.onOfferChanged(player);
            // Notify both UIs about the offer change
            notifyBothTradingPages(session);
        }
    }

    /**
     * Player accepts their current trade offer.
     */
    public boolean acceptTrade(PlayerRef player) {
        Optional<TradeSession> optSession = getSession(player);
        if (optSession.isEmpty()) {
            return false;
        }

        TradeSession session = optSession.get();
        boolean accepted = session.accept(player);

        if (accepted) {
            // Notify both UIs about the state change
            notifyBothTradingPages(session);

            if (session.getState() == TradeState.BOTH_ACCEPTED_COUNTDOWN) {
                // Start countdown
                startCountdown(session);
            }
        }

        return accepted;
    }

    /**
     * Player revokes their acceptance.
     */
    public boolean revokeAccept(PlayerRef player) {
        Optional<TradeSession> optSession = getSession(player);
        if (optSession.isEmpty()) {
            return false;
        }

        TradeSession session = optSession.get();
        boolean revoked = session.revokeAccept(player);

        if (revoked) {
            // Notify both UIs about the state change
            notifyBothTradingPages(session);
        }

        return revoked;
    }

    /**
     * Start the 3-second countdown before trade execution.
     */
    private void startCountdown(TradeSession session) {
        ScheduledFuture<?> countdownTask = scheduler.schedule(() -> {
            // Verify session is still valid and in countdown state
            TradeSession current = activeSessions.get(session.getSessionId());
            if (current != null &&
                current.getState() == TradeState.BOTH_ACCEPTED_COUNTDOWN &&
                current.isCountdownComplete()) {

                // Notify that confirm is now available
                current.broadcastMessage("Both players accepted! Click CONFIRM TRADE to complete.");
            }
        }, TradeConstants.COUNTDOWN_DURATION_MS, TimeUnit.MILLISECONDS);

        session.setCountdownTask(countdownTask);
    }

    /**
     * Execute the trade (called when player clicks confirm after countdown).
     */
    public TradeSession.TradeResult confirmTrade(PlayerRef player,
                                                  Store<EntityStore> store,
                                                  Ref<EntityStore> playerEntityRef) {
        Optional<TradeSession> optSession = getSession(player);
        if (optSession.isEmpty()) {
            return new TradeSession.TradeResult(false, "No active trade session");
        }

        TradeSession session = optSession.get();

        if (!session.isCountdownComplete()) {
            return new TradeSession.TradeResult(false, "Countdown not complete");
        }

        // Get the other player's entity ref (this would need to be stored or retrieved)
        PlayerRef otherPlayer = session.getOtherPlayer(player);
        Ref<EntityStore> otherEntityRef = getPlayerEntityRef(otherPlayer);

        if (otherEntityRef == null) {
            return new TradeSession.TradeResult(false, "Other player not available");
        }

        // Determine which ref is initiator and which is target
        Ref<EntityStore> initiatorRef, targetRef;
        if (player.getUuid().equals(session.getInitiator().getUuid())) {
            initiatorRef = playerEntityRef;
            targetRef = otherEntityRef;
        } else {
            initiatorRef = otherEntityRef;
            targetRef = playerEntityRef;
        }

        // Execute the trade
        TradeSession.TradeResult result = session.execute(store, initiatorRef, targetRef);

        if (result.success) {
            // Clean up session
            endSession(session);
            // Status messages are handled by TradingPage
        } else {
            // Trade failed - notify both trading pages to refresh UI (acceptances were revoked)
            // Status messages are handled by TradingPage based on result.cause
            notifyBothTradingPages(session);
        }

        return result;
    }

    /**
     * Cancel an active trade.
     */
    public boolean cancelTrade(PlayerRef player) {
        Optional<TradeSession> optSession = getSession(player);
        if (optSession.isEmpty()) {
            // Check pending requests
            TradeSession pending = pendingRequests.remove(player.getUuid());
            if (pending != null) {
                pending.cancel(player);
                return true;
            }
            return false;
        }

        TradeSession session = optSession.get();
        session.cancel(player);
        endSession(session);

        // Notify partner's UI about cancellation (their page will close)
        if (!session.isTestMode()) {
            PlayerRef partner = session.getOtherPlayer(player);
            if (partner != null) {
                UUID partnerId = partner.getUuid();
                StatusUpdateCallback callback = tradingPageStatusCallbacks.get(partnerId);
                if (callback != null) {
                    callback.setStatus("Trade cancelled by partner", "#ff4444");
                }
            }
        }

        return true;
    }

    /**
     * Handle player disconnect.
     */
    public void onPlayerDisconnect(PlayerRef player) {
        // Cancel any active trade
        Optional<TradeSession> optSession = getSession(player);
        if (optSession.isPresent()) {
            TradeSession session = optSession.get();
            session.cancel(null);
            endSession(session);

            // Notify the other player
            PlayerRef other = session.getOtherPlayer(player);
            if (other != null) {
                other.sendMessage(Message.raw("Trade cancelled - other player disconnected"));
            }
        }

        // Cancel any pending requests where this player is involved
        UUID playerId = player.getUuid();
        pendingRequests.entrySet().removeIf(entry -> {
            TradeSession session = entry.getValue();
            if (session.isParticipant(player)) {
                PlayerRef other = session.getOtherPlayer(player);
                if (other != null) {
                    other.sendMessage(Message.raw("Trade request cancelled - player disconnected"));
                }
                return true;
            }
            return false;
        });
    }

    /**
     * Clean up a completed/cancelled session.
     */
    private void endSession(TradeSession session) {
        // Close both players' trading UIs
        closeBothTradingPages(session);

        activeSessions.remove(session.getSessionId());
        playerToSession.remove(session.getInitiator().getUuid());
        playerToSession.remove(session.getTarget().getUuid());
        LOGGER.atInfo().log("Trade session " + session.getSessionId() + " ended");
    }

    /**
     * Get the entity ref for a player.
     * Returns the stored entity ref from when the player opened their trading page.
     */
    private Ref<EntityStore> getPlayerEntityRef(PlayerRef player) {
        if (player == null) return null;
        return playerEntityRefs.get(player.getUuid());
    }

    /**
     * Store player entity ref for trade execution.
     */
    public void storePlayerEntityRef(PlayerRef player, Ref<EntityStore> entityRef) {
        if (player != null && entityRef != null) {
            playerEntityRefs.put(player.getUuid(), entityRef);
        }
    }

    /**
     * Shutdown the manager.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }

        // Cancel all active sessions
        for (TradeSession session : activeSessions.values()) {
            session.cancel(null);
        }
        activeSessions.clear();
        playerToSession.clear();
        pendingRequests.clear();
    }

    // ===== RESULT CLASSES =====

    public static class TradeRequestResult {
        public final boolean success;
        public final String message;
        public final TradeSession session;

        public TradeRequestResult(boolean success, String message) {
            this(success, message, null);
        }

        public TradeRequestResult(boolean success, String message, TradeSession session) {
            this.success = success;
            this.message = message;
            this.session = session;
        }
    }

    public void openTradeUI(PlayerRef playerRef,
                             Store<EntityStore> store,
                             Ref<EntityStore> playerEntityRef) {
        try {
            Player player = store.getComponent(playerEntityRef, Player.getComponentType());
            if (player == null) return;

            PageManager pageManager = player.getPageManager();
            if (pageManager == null) return;

            TradingPage tradingPage = new TradingPage(playerRef, this, store, playerEntityRef);
            pageManager.openCustomPage(playerEntityRef, store, tradingPage);

        } catch (Exception ignored) {
        }
    }

}
