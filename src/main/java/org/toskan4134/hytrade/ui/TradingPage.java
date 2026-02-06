package org.toskan4134.hytrade.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.toskan4134.hytrade.TradingPlugin;
import org.toskan4134.hytrade.constants.TradeConstants;
import org.toskan4134.hytrade.messages.TradeMessages;
import org.toskan4134.hytrade.trade.TradeManager;
import org.toskan4134.hytrade.trade.TradeOffer;
import org.toskan4134.hytrade.trade.TradeSession;
import org.toskan4134.hytrade.trade.TradeState;
import org.toskan4134.hytrade.util.Common;
import org.toskan4134.hytrade.util.InventoryHelper;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.toskan4134.hytrade.constants.TradeConstants.*;
import static org.toskan4134.hytrade.util.Common.isDebug;

/**
 * UI Controller for the trading interface.
 * Displays consolidated inventories and handles trade interactions with quantity controls.
 */
public class TradingPage extends InteractiveCustomUIPage<TradingPageData> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    // Default max stack size when we can't determine it - use constant from TradeConstants

    // Status message colors - using constants from TradeConstants
    private static final String COLOR_NORMAL = TradeConstants.COLOR_NORMAL;
    private static final String COLOR_WARNING = TradeConstants.COLOR_WARNING;
    private static final String COLOR_ERROR = TradeConstants.COLOR_ERROR;
    private static final String COLOR_SUCCESS = TradeConstants.COLOR_SUCCESS;

    private final TradeManager tradeManager;
    private final PlayerRef playerRef;
    private final Store<EntityStore> store;
    private final Ref<EntityStore> entityRef;

    // Consolidated inventory: itemId -> ConsolidatedItem
    private final Map<String, ConsolidatedItem> consolidatedInventory = new LinkedHashMap<>();
    // My offer: itemId -> quantity
    private final Map<String, Integer> myOfferItems = new LinkedHashMap<>();
    // Previous inventory snapshot for change detection: itemId -> quantity
    private final Map<String, Integer> previousInventorySnapshot = new LinkedHashMap<>();

    // Countdown timer for UI updates
    private final ScheduledExecutorService countdownScheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> countdownUpdateTask;
    private long lastCountdownValue = -1;

    // Temporary status message reset
    private ScheduledFuture<?> statusResetTask;

    // Flag to request UI close on next update (for thread-safe closing)
    private volatile boolean closeRequested = false;

    /**
     * Check if a temporary status message is currently being displayed.
     * Used to prevent updateStatusUI from overwriting warning/error messages.
     */
    private boolean isTemporaryStatusActive() {
        return statusResetTask != null && !statusResetTask.isDone();
    }

    /**
     * Request this trading page to close on the next UI update.
     * Used to safely close the UI from threads that can't directly close it.
     */
    public void requestClose() {
        this.closeRequested = true;
    }

    public TradingPage(TradingPlugin plugin, PlayerRef playerRef, TradeManager tradeManager,
                       Store<EntityStore> store, Ref<EntityStore> entityRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, org.toskan4134.hytrade.ui.TradingPageData.CODEC);
        this.playerRef = playerRef;
        this.tradeManager = tradeManager;
        this.store = store;
        this.entityRef = entityRef;
    }

    // ===== STATUS MESSAGE HELPERS =====

    /**
     * Cancel any pending status reset task.
     */
    private void cancelStatusReset() {
        if (statusResetTask != null && !statusResetTask.isDone()) {
            statusResetTask.cancel(false);
            statusResetTask = null;
        }
    }

    /**
     * Schedule a status reset after the delay period.
     * After the delay, refreshStatusUI() will be called to restore the normal status.
     */
    private void scheduleStatusReset() {
        // Don't schedule if scheduler is shut down (UI is closing)
        if (countdownScheduler.isShutdown() || countdownScheduler.isTerminated()) {
            return;
        }

        cancelStatusReset();
        try {
            statusResetTask = countdownScheduler.schedule(() -> {
                try {
                    refreshStatusUI();
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("Error resetting status message");
                }
            }, STATUS_RESET_DELAY_MS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // Scheduler was shut down between the check and the schedule call
            Common.logDebug(LOGGER, "Status reset task rejected - scheduler shut down");
        }
    }

    /**
     * Update status message with normal (white) color.
     * Normal messages auto-reset to state-based status after 5 seconds.
     */
    private void setStatusNormal(String message) {
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        commands.set("#StatusMessage.Text", message);
        commands.set("#StatusMessage.Style.TextColor", COLOR_NORMAL);
        sendUpdate(commands, events, false);
        scheduleStatusReset();
    }

    /**
     * Update status message with warning (yellow) color.
     * Warning messages auto-reset to normal status after 5 seconds.
     */
    private void setStatusWarning(String message) {
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        commands.set("#StatusMessage.Text", message);
        commands.set("#StatusMessage.Style.TextColor", COLOR_WARNING);
        sendUpdate(commands, events, false);
        scheduleStatusReset();
    }

    /**
     * Update status message with error (red) color.
     * Error messages auto-reset to normal status after 5 seconds.
     */
    private void setStatusError(String message) {
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        commands.set("#StatusMessage.Text", message);
        commands.set("#StatusMessage.Style.TextColor", COLOR_ERROR);
        sendUpdate(commands, events, false);
        scheduleStatusReset();
    }

    /**
     * Update status message with success (green) color.
     * Success messages auto-reset to normal status after 5 seconds.
     */
    private void setStatusSuccess(String message) {
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        commands.set("#StatusMessage.Text", message);
        commands.set("#StatusMessage.Style.TextColor", COLOR_SUCCESS);
        sendUpdate(commands, events, false);
        scheduleStatusReset();
    }

    /**
     * Public method to set status message from external callers (e.g., TradeManager).
     * Warning and error colors will auto-reset to normal status after 5 seconds.
     * @param message The message to display
     * @param color The color (use COLOR_* constants)
     */
    public void setStatus(String message, String color) {
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        commands.set("#StatusMessage.Text", message);
        commands.set("#StatusMessage.Style.TextColor", color);
        sendUpdate(commands, events, false);

        // Auto-reset for warning and error colors
        if (COLOR_WARNING.equals(color) || COLOR_ERROR.equals(color)) {
            scheduleStatusReset();
        } else {
            cancelStatusReset();
        }
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> entityRef,
                      @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        // Load the UI template
        commands.append(TradeConstants.TRADING_PAGE_LAYOUT);

        // Bind button events
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#AcceptButton",
            EventData.of(KEY_ACTION, ACTION_ACCEPT),
            false
        );

        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ConfirmButton",
            EventData.of(KEY_ACTION, ACTION_CONFIRM),
            false
        );

        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#CancelButton",
            EventData.of(KEY_ACTION, ACTION_CANCEL),
            false
        );

        // Get trade session
        Optional<TradeSession> optSession = tradeManager.getSession(playerRef);
        if (optSession.isEmpty()) {
            commands.set("#StatusMessage.Text", TradeMessages.uiNoActiveSession());
            commands.set("#StatusMessage.Style.TextColor", TradeConstants.COLOR_ERROR);
            commands.set("#DebugInfo.Text", "Use /trade test to start");
            return;
        }

        TradeSession session = optSession.get();

        // Set partner name
        PlayerRef partner = session.getOtherPlayer(playerRef);
        String partnerName = session.isTestMode() ? TradeMessages.uiLabelTestPartner() :
                (partner != null ? partner.getUsername() : TradeMessages.uiLabelUnknown());
        commands.set("#PartnerName.Text", TradeMessages.uiLabelTradingWith() + " " + partnerName);

        // Initialize consolidated inventory
        initializeConsolidatedInventory(store, entityRef);

        // Initialize inventory snapshot for change detection
        previousInventorySnapshot.clear();
        previousInventorySnapshot.putAll(getCurrentInventorySnapshot(store, entityRef));

        // Build dynamic UI elements
        buildInventorySlots(commands, events);
        buildMyOfferSlots(commands, events, session);
        buildPartnerOfferSlots(commands, session);

        // Translate UI Constants
        translateUIConstants(commands);

        // Hide debug info if debug mode is OFF
        if (!isDebug()) {
            commands.set("#DebugInfo.Visible", false);
        }

        // Update status
        updateStatusUI(commands, session);

        // Register for inventory change events (also stores entityRef for trade execution and this page instance)
        tradeManager.registerTradingPage(playerRef, this::onInventoryChangedEvent, entityRef, this::setStatus, this);
    }

    private void translateUIConstants(UICommandBuilder commands){
        commands.set("#TitleLabel.Text", TradeMessages.uiTitleTrade());
        commands.set("#YourOfferLabel.Text", TradeMessages.uiLabelYourOffer());
        commands.set("#PartnerOfferLabel.Text", TradeMessages.uiLabelPartnerOffer());
        commands.set("#YourInventoryLabel.Text", TradeMessages.uiLabelYourInventory());
        commands.set("#YourStatusLabel.Text", TradeMessages.uiLabelYourStatus());
        commands.set("#PartnerStatusLabel.Text", TradeMessages.uiLabelPartnerStatus());
        commands.set("#AcceptButton.Text", TradeMessages.uiButtonAccept());
        commands.set("#ConfirmButton.Text", TradeMessages.uiButtonConfirm());
        commands.set("#CancelButton.Text", TradeMessages.uiButtonCancel());
    }

    /**
     * Called when player's inventory changes (from event listener)
     * This runs on the correct thread since it's triggered by the event system
     */
    private void onInventoryChangedEvent() {
        // Check if a close was requested (e.g., from disconnect handler on wrong thread)
        if (closeRequested) {
            this.close();
            return;
        }

        // Refresh the UI
        Optional<TradeSession> optSession = tradeManager.getSession(playerRef);
        if (optSession.isEmpty()) {
            // Session no longer exists - partner disconnected or trade ended
            this.close();
            return;
        }
        TradeSession session = optSession.get();

        // Also close if trade is in a terminal state
        if (session.getState() == TradeState.CANCELLED ||
            session.getState() == TradeState.COMPLETED ||
            session.getState() == TradeState.FAILED) {
            this.close();
            return;
        }

        // Check and handle inventory changes
        checkAndHandleInventoryChanges(store, entityRef);

        // Re-initialize inventory
        initializeConsolidatedInventory(store, entityRef);

        // Update previous snapshot
        previousInventorySnapshot.clear();
        previousInventorySnapshot.putAll(getCurrentInventorySnapshot(store, entityRef));

        // Create update builders
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();

        // Clear and rebuild slots
        commands.clear("#InventorySlotsContainer");
        commands.clear("#MyOfferSlotsContainer");
        commands.clear("#PartnerOfferSlotsContainer");

        buildInventorySlots(commands, events);
        buildMyOfferSlots(commands, events, session);
        buildPartnerOfferSlots(commands, session);

        // Update status UI, but skip if a temporary status (warning/error) is being displayed
        if (!isTemporaryStatusActive()) {
            updateStatusUI(commands, session);
        } else {
            // Still update accept statuses even when skipping status message
            boolean iAmInitiator = playerRef.getUuid().equals(session.getInitiator().getUuid());
            boolean myAccepted = iAmInitiator ? session.isInitiatorAccepted() : session.isTargetAccepted();
            boolean partnerAccepted = iAmInitiator ? session.isTargetAccepted() : session.isInitiatorAccepted();
            commands.set("#MyAcceptStatus.Text", myAccepted ? TradeMessages.uiStatusAccepted() : TradeMessages.uiStatusNotAccepted());
            commands.set("#MyAcceptStatus.Style.TextColor", myAccepted ? COLOR_SUCCESS : COLOR_ERROR);
            commands.set("#PartnerAcceptStatus.Text", partnerAccepted ? TradeMessages.uiStatusAccepted() : TradeMessages.uiStatusNotAccepted());
            commands.set("#PartnerAcceptStatus.Style.TextColor", partnerAccepted ? COLOR_SUCCESS : COLOR_ERROR);

        }

        // Send update
        sendUpdate(commands, events, false);
    }

    private void initializeConsolidatedInventory(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        consolidatedInventory.clear();

        try {
            Player player = store.getComponent(entityRef, Player.getComponentType());
            if (player == null) return;

            Inventory inventory = player.getInventory();

            // Load items from hotbar
            ItemContainer hotbar = inventory.getHotbar();
            if (hotbar != null && hotbar.getCapacity() > 0) {
                processContainer(hotbar, "Hotbar");
            }

            // Load items from backpack
            ItemContainer backpack = inventory.getBackpack();
            if (backpack != null && backpack.getCapacity() > 0) {
                processContainer(backpack, "Backpack");
            }

            // Load items from storage (if available)
            ItemContainer storage = inventory.getStorage();
            if (storage != null && storage.getCapacity() > 0) {
                processContainer(storage, "Storage");
            }

            // Account for items already in the offer
            Optional<TradeSession> optSession = tradeManager.getSession(playerRef);
            if (optSession.isPresent()) {
                TradeOffer myOffer = optSession.get().getOfferFor(playerRef);
                if (myOffer != null) {
                    for (ItemStack offerItem : myOffer.getItems()) {
                        if (offerItem != null && !offerItem.isEmpty()) {
                            String itemId = offerItem.getItem().getId();
                            int offeredQty = offerItem.getQuantity();
                            ConsolidatedItem consolidated = consolidatedInventory.get(itemId);
                            if (consolidated != null) {
                                consolidated.offeredQuantity += offeredQty;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to initialize consolidated inventory");
        }
    }

    /**
     * Process items from a container and add them to consolidated inventory
     */
    private void processContainer(ItemContainer container, String containerName) {
        for (int i = 0; i < container.getCapacity(); i++) {
            ItemStack itemStack = container.getItemStack((short) i);
            if (itemStack != null && !itemStack.isEmpty()) {
                Item item = itemStack.getItem();
                String itemId = item.getId();
                int quantity = itemStack.getQuantity();
                // Get max stack from item, or estimate if not set
                int maxStack = item.getMaxStack() > 0 ? item.getMaxStack() : estimateMaxStackSize(itemId, quantity);

                ConsolidatedItem consolidated = consolidatedInventory.computeIfAbsent(
                    itemId, id -> new ConsolidatedItem(id, item, maxStack)
                );
                consolidated.totalQuantity += quantity;
                // Update max stack if we find a larger stack
                if (quantity > consolidated.maxStackSize) {
                    consolidated.maxStackSize = quantity;
                }
                // Update item reference if not set
                if (consolidated.item == null) {
                    consolidated.item = item;
                }
            }
        }
    }

    /**
     * Estimate max stack size based on item ID patterns or current quantity
     */
    private int estimateMaxStackSize(String itemId, int currentQuantity) {
        // Tools, weapons, armor typically stack to 1
        if (itemId.contains("Sword") || itemId.contains("Axe") || itemId.contains("Pickaxe") ||
            itemId.contains("Helmet") || itemId.contains("Chestplate") || itemId.contains("Leggings") ||
            itemId.contains("Boots") || itemId.contains("Shield") || itemId.contains("Tool")) {
            return 1;
        }
        // Materials often stack to 100
        if (itemId.contains("Ingredient") || itemId.contains("Bar") || itemId.contains("Ore")) {
            return 100;
        }
        // Use current quantity as hint, minimum TradeConstants.DEFAULT_MAX_STACK
        return Math.max(TradeConstants.DEFAULT_MAX_STACK, currentQuantity);
    }


    /**
     * Get current raw inventory quantities (without accounting for offers)
     */
    private Map<String, Integer> getCurrentInventorySnapshot(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        Map<String, Integer> snapshot = new LinkedHashMap<>();
        try {
            Player player = store.getComponent(entityRef, Player.getComponentType());
            if (player == null) return snapshot;

            Inventory inventory = player.getInventory();

            // Process all containers
            processContainerForSnapshot(inventory.getHotbar(), snapshot);
            processContainerForSnapshot(inventory.getBackpack(), snapshot);
            processContainerForSnapshot(inventory.getStorage(), snapshot);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to get inventory snapshot");
        }
        return snapshot;
    }

    private void processContainerForSnapshot(ItemContainer container, Map<String, Integer> snapshot) {
        if (container == null || container.getCapacity() <= 0) return;
        for (int i = 0; i < container.getCapacity(); i++) {
            ItemStack itemStack = container.getItemStack((short) i);
            if (itemStack != null && !itemStack.isEmpty()) {
                String itemId = itemStack.getItem().getId();
                int quantity = itemStack.getQuantity();
                snapshot.merge(itemId, quantity, Integer::sum);
            }
        }
    }

    /**
     * Validate that all items in the offer are still available in inventory
     * @return true if all offered items are available, false otherwise
     */
    private boolean validateOfferAgainstInventory(TradeSession session) {
        Map<String, Integer> currentInventory = getCurrentInventorySnapshot(store, entityRef);
        TradeOffer myOffer = session.getOfferFor(playerRef);

        if (myOffer == null) return true;

        for (ItemStack offerItem : myOffer.getItems()) {
            if (offerItem == null || offerItem.isEmpty()) continue;

            String itemId = offerItem.getItem().getId();
            int offeredQty = offerItem.getQuantity();
            int availableQty = currentInventory.getOrDefault(itemId, 0);

            if (availableQty < offeredQty) {
                LOGGER.atWarning().log("Offer validation failed: " + itemId +
                " offered=" + offeredQty + " available=" + availableQty);
                return false;
            }
        }
        return true;
    }

    /**
     * Validate that the player has enough inventory space to receive partner's items.
     * Intelligently checks if items can stack with existing items.
     * @return true if player has enough space, false otherwise
     */
    private boolean validateInventorySpace(TradeSession session) {
        try {
            Player player = store.getComponent(entityRef, Player.getComponentType());
            if (player == null) return false;

            Inventory inventory = player.getInventory();

            // Get partner's offer
            PlayerRef partner = session.getOtherPlayer(playerRef);
            TradeOffer partnerOffer = session.getOfferFor(partner);

            if (partnerOffer == null || partnerOffer.getItems().isEmpty()) {
                return true; // No items to receive, so space is fine
            }

            // Count total empty slots
            int totalEmptySlots = 0;
            ItemContainer hotbar = inventory.getHotbar();
            ItemContainer backpack = inventory.getBackpack();
            ItemContainer storage = inventory.getStorage();

            if (hotbar != null) totalEmptySlots += InventoryHelper.countEmptySlots(hotbar);
            if (backpack != null) totalEmptySlots += InventoryHelper.countEmptySlots(backpack);
            if (storage != null) totalEmptySlots += InventoryHelper.countEmptySlots(storage);

            // Build a map of current inventory: itemId -> total quantity and available stack space
            Map<String, ItemStackInfo> currentInventory = new HashMap<>();

            for (ItemContainer container : new ItemContainer[]{hotbar, backpack, storage}) {
                if (container == null) continue;

                for (int i = 0; i < container.getCapacity(); i++) {
                    ItemStack stack = container.getItemStack((short) i);
                    if (stack == null || stack.isEmpty()) continue;

                    String itemId = stack.getItem().getId();
                    int quantity = stack.getQuantity();
                    int maxStackTemp = stack.getItem().getMaxStack();
                    final int maxStack = maxStackTemp > 0 ? maxStackTemp : DEFAULT_MAX_STACK;

                    ItemStackInfo info = currentInventory.computeIfAbsent(itemId,
                        k -> new ItemStackInfo(maxStack));
                    info.currentQuantity += quantity;
                    info.availableSpace += (maxStack - quantity);
                }
            }

            // Calculate how many new slots are needed for partner's items
            int newSlotsNeeded = 0;

            for (ItemStack offeredStack : partnerOffer.getItems()) {
                if (offeredStack == null || offeredStack.isEmpty()) continue;

                String itemId = offeredStack.getItem().getId();
                int offeredQuantity = offeredStack.getQuantity();
                int maxStack = offeredStack.getItem().getMaxStack();
                if (maxStack <= 0) maxStack = DEFAULT_MAX_STACK;

                ItemStackInfo info = currentInventory.get(itemId);

                if (info != null && info.availableSpace > 0) {
                    // We have existing stacks with available space
                    int canFitInExisting = Math.min(offeredQuantity, info.availableSpace);
                    int remaining = offeredQuantity - canFitInExisting;

                    if (remaining > 0) {
                        // Need new slots for the remaining items
                        newSlotsNeeded += (int) Math.ceil((double) remaining / maxStack);
                    }
                    // else: all items fit in existing stacks, no new slots needed
                } else {
                    // No existing stacks or they're all full - need new slots
                    newSlotsNeeded += (int) Math.ceil((double) offeredQuantity / maxStack);
                }
            }

            Common.logDebug(LOGGER, "Smart space check: empty=" + totalEmptySlots +
                " needed=" + newSlotsNeeded);

            return totalEmptySlots >= newSlotsNeeded;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Error validating inventory space");
            return false;
        }
    }

    /**
     * Helper class to track inventory stack information for space calculation.
     */
    private static class ItemStackInfo {
        int maxStackSize;
        int currentQuantity;
        int availableSpace;

        ItemStackInfo(int maxStackSize) {
            this.maxStackSize = maxStackSize;
            this.currentQuantity = 0;
            this.availableSpace = 0;
        }
    }

    /**
     * Check for inventory changes and handle them appropriately.
     * - Auto-unaccept if player had accepted and inventory changed
     * - Remove items from offer if no longer available in inventory
     * - New items will be added automatically when inventory is rebuilt
     */
    private void checkAndHandleInventoryChanges(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        // Skip processing during trade execution to avoid infinite recursion
        Optional<TradeSession> checkSession = tradeManager.getSession(playerRef);
        if (checkSession.isPresent()) {
            TradeState currentState = checkSession.get().getState();
            if (currentState == TradeState.EXECUTING || currentState == TradeState.COMPLETED ||
                currentState == TradeState.FAILED || currentState == TradeState.CANCELLED) {
                Common.logDebug(LOGGER, "Skipping inventory change handling - trade state: " + currentState);
                return;
            }
        }

        Map<String, Integer> currentSnapshot = getCurrentInventorySnapshot(store, entityRef);

        // First time - just save snapshot
        if (previousInventorySnapshot.isEmpty()) {
            previousInventorySnapshot.putAll(currentSnapshot);
            return;
        }

        // Check for changes
        boolean hasChanges = false;
        Map<String, Integer> decreasedItems = new LinkedHashMap<>();

        // Check for items that decreased or were removed
        for (Map.Entry<String, Integer> prev : previousInventorySnapshot.entrySet()) {
            String itemId = prev.getKey();
            int prevQty = prev.getValue();
            int currentQty = currentSnapshot.getOrDefault(itemId, 0);

            Common.logDebug(LOGGER, "Comparing " + itemId + ": prev=" + prevQty + " current=" + currentQty);

            if (currentQty < prevQty) {
                hasChanges = true;
                decreasedItems.put(itemId, prevQty - currentQty);
                Common.logDebug(LOGGER, "DETECTED DECREASE: " + itemId + " from " + prevQty + " to " + currentQty);
            }
        }

        // Check for new items
        for (Map.Entry<String, Integer> curr : currentSnapshot.entrySet()) {
            String itemId = curr.getKey();
            if (!previousInventorySnapshot.containsKey(itemId)) {
                hasChanges = true;
            } else if (curr.getValue() > previousInventorySnapshot.get(itemId)) {
                hasChanges = true;
            }
        }

        if (hasChanges) {
            Optional<TradeSession> optSession = tradeManager.getSession(playerRef);

            if (optSession.isPresent()) {
                TradeSession session = optSession.get();
                boolean hasAccepted = session.hasAccepted(playerRef);

                // Auto-unaccept if player had accepted
                if (hasAccepted) {
                    boolean revoked = tradeManager.revokeAccept(playerRef);
                    setStatusWarning(TradeMessages.uiAcceptRevoked());
                }

                // Remove items from offer if they're no longer available
                TradeOffer myOffer = session.getOfferFor(playerRef);
                Common.logDebug(LOGGER, "My offer: " + (myOffer != null ? myOffer.getItems().size() + " items" : "null"));
                Common.logDebug(LOGGER, "decreasedItems: " + decreasedItems);

                if (myOffer != null && !decreasedItems.isEmpty()) {
                    Common.logDebug(LOGGER, "Calling handleDecreasedItemsInOffer...");
                    handleDecreasedItemsInOffer(myOffer, decreasedItems, currentSnapshot, session);
                } else {
                    Common.logDebug(LOGGER, "NOT calling handleDecreasedItemsInOffer - myOffer=" + myOffer + " decreasedItems.isEmpty=" + decreasedItems.isEmpty());
                }
            }
        } else {
            Common.logDebug(LOGGER, "No changes detected - snapshots appear identical");
        }

        // Update snapshot
        previousInventorySnapshot.clear();
        previousInventorySnapshot.putAll(currentSnapshot);
    }

    /**
     * Handle items that decreased in inventory - remove from offer if necessary
     */
    private void handleDecreasedItemsInOffer(TradeOffer myOffer, Map<String, Integer> decreasedItems,
                                              Map<String, Integer> currentInventory, TradeSession session) {
        Common.logDebug(LOGGER, "handleDecreasedItemsInOffer called - decreasedItems: " + decreasedItems);
        Common.logDebug(LOGGER, "currentInventory: " + currentInventory);
        Common.logDebug(LOGGER, "Offer locked: " + myOffer.isLocked());

        List<ItemStack> offerItems = myOffer.getItems();
        Common.logDebug(LOGGER, "Offer items count: " + offerItems.size());
        boolean offerChanged = false;

        for (int i = offerItems.size() - 1; i >= 0; i--) {
            ItemStack offerItem = offerItems.get(i);
            if (offerItem == null || offerItem.isEmpty()) continue;

            String itemId = offerItem.getItem().getId();
            int offeredQty = offerItem.getQuantity();
            int availableQty = currentInventory.getOrDefault(itemId, 0);

            Common.logDebug(LOGGER, "Checking offer item: " + itemId + " offered=" + offeredQty + " available=" + availableQty);

            if (availableQty < offeredQty) {
                // Need to reduce or remove from offer
                if (availableQty <= 0) {
                    // Remove entirely
                    ItemStack removed = myOffer.removeItem(i);
                    Common.logDebug(LOGGER, "Removed item from offer: " + (removed != null ? "success" : "FAILED"));
                    setStatusWarning(TradeMessages.actionRemovedFromOffer(itemId));
                } else {
                    // Reduce quantity
                    boolean success = myOffer.setItemQuantity(i, availableQty);
                    Common.logDebug(LOGGER, "Set item quantity to " + availableQty + ": " + (success ? "success" : "FAILED"));
                    setStatusWarning(TradeMessages.actionReducedInOffer(itemId, availableQty));
                }
                offerChanged = true;
            }
        }

        if (offerChanged) {
            Common.logDebug(LOGGER, "Offer changed, notifying tradeManager");
            tradeManager.onOfferChanged(playerRef);
        } else {
            Common.logDebug(LOGGER, "No offer changes needed");
        }
    }

    private void buildInventorySlots(UICommandBuilder commands, UIEventBuilder events) {
        int index = 0;
        int currentRowNum = -1;

        for (ConsolidatedItem item : consolidatedInventory.values()) {
            // Create new row if needed (rows are created as children of InventorySlotsContainer)
            int rowNum = index / TradeConstants.SLOTS_PER_ROW;
            if (rowNum != currentRowNum) {
                currentRowNum = rowNum;
                // Create row using appendInline - use Center layout for centering items
                commands.appendInline("#InventorySlotsContainer",
                    "Group #InvRow" + rowNum + " { LayoutMode: Center; Anchor: (Height: 140); }");
            }

            // Append slot component to the current row
            commands.append("#InvRow" + currentRowNum, INVENTORY_SLOT_UI);

            // Calculate slot index within the row
            int slotInRow = index % TradeConstants.SLOTS_PER_ROW;
            String slotSelector = "#InvRow" + currentRowNum + "[" + slotInRow + "]";

            commands.set(slotSelector + " #SlotItem.ItemId", item.itemId);
            commands.set(slotSelector + " #SlotItem.Visible", true);
            commands.set(slotSelector + " #SlotQty.Text", "x" + item.getAvailable());

            // Bind events - use itemId in action for identification
            String safeItemId = item.itemId.replace("_", "_US_").replace("-", "_DA_");

            // Click on icon = transfer 1 stack
            events.addEventBinding(
                CustomUIEventBindingType.Activating,
                slotSelector + " #SlotButton",
                EventData.of(KEY_ACTION, ACTION_INV_PREFIX + safeItemId + "_stack"),
                false
            );

            // +10 button
            events.addEventBinding(
                CustomUIEventBindingType.Activating,
                slotSelector + " #SlotQty10",
                EventData.of(KEY_ACTION, ACTION_INV_PREFIX + safeItemId + "_10"),
                false
            );

            // +1 button
            events.addEventBinding(
                CustomUIEventBindingType.Activating,
                slotSelector + " #SlotQty1",
                EventData.of(KEY_ACTION, ACTION_INV_PREFIX + safeItemId + "_1"),
                false
            );

            index++;
        }
    }

    private void buildMyOfferSlots(UICommandBuilder commands, UIEventBuilder events, TradeSession session) {
        TradeOffer myOffer = session.getOfferFor(playerRef);
        if (myOffer == null) return;

        // Collect offer items into map
        myOfferItems.clear();
        List<ItemStack> offerItems = myOffer.getItems();
        for (ItemStack item : offerItems) {
            if (item != null && !item.isEmpty()) {
                String itemId = item.getItem().getId();
                int qty = item.getQuantity();
                myOfferItems.merge(itemId, qty, Integer::sum);
            }
        }

        int index = 0;
        int currentRowNum = -1;
        int slotsPerOfferRow = SLOTS_PER_OFFER_ROW; // Wider container fits ~4 slots

        for (Map.Entry<String, Integer> entry : myOfferItems.entrySet()) {
            String itemId = entry.getKey();
            int quantity = entry.getValue();

            // Create new row if needed - use Center layout
            int rowNum = index / slotsPerOfferRow;
            if (rowNum != currentRowNum) {
                currentRowNum = rowNum;
                commands.appendInline("#MyOfferSlotsContainer",
                    "Group #MyOfferRow" + rowNum + " { LayoutMode: Center; Anchor: (Height: 140); }");
            }

            // Append slot component to the current row
            commands.append("#MyOfferRow" + currentRowNum, OFFER_SLOT_UI);

            // Calculate slot index within the row
            int slotInRow = index % slotsPerOfferRow;
            String slotSelector = "#MyOfferRow" + currentRowNum + "[" + slotInRow + "]";

            commands.set(slotSelector + " #SlotItem.ItemId", itemId);
            commands.set(slotSelector + " #SlotItem.Visible", true);
            commands.set(slotSelector + " #SlotQty.Text", "x" + quantity);

            // Bind events
            String safeItemId = itemId.replace("_", "_US_").replace("-", "_DA_");

            // Click on icon = return 1 stack
            events.addEventBinding(
                CustomUIEventBindingType.Activating,
                slotSelector + " #SlotButton",
                EventData.of(KEY_ACTION, ACTION_OFFER_PREFIX + safeItemId + "_stack"),
                false
            );

            // -10 button
            events.addEventBinding(
                CustomUIEventBindingType.Activating,
                slotSelector + " #SlotQty10",
                EventData.of(KEY_ACTION, ACTION_OFFER_PREFIX + safeItemId + "_10"),
                false
            );

            // -1 button
            events.addEventBinding(
                CustomUIEventBindingType.Activating,
                slotSelector + " #SlotQty1",
                EventData.of(KEY_ACTION, ACTION_OFFER_PREFIX + safeItemId + "_1"),
                false
            );

            index++;
        }
    }

    private void buildPartnerOfferSlots(UICommandBuilder commands, TradeSession session) {
        PlayerRef partner = session.getOtherPlayer(playerRef);
        TradeOffer partnerOffer = session.getOfferFor(partner);
        if (partnerOffer == null) return;

        // Consolidate partner offer items
        Map<String, Integer> partnerItems = new LinkedHashMap<>();
        List<ItemStack> offerItems = partnerOffer.getItems();
        for (ItemStack item : offerItems) {
            if (item != null && !item.isEmpty()) {
                String itemId = item.getItem().getId();
                int qty = item.getQuantity();
                partnerItems.merge(itemId, qty, Integer::sum);
            }
        }

        int index = 0;
        int currentRowNum = -1;
        int slotsPerOfferRow = SLOTS_PER_PARTNER_ROW; // Partner slots are smaller (80px), fit ~5 per row

        for (Map.Entry<String, Integer> entry : partnerItems.entrySet()) {
            String itemId = entry.getKey();
            int quantity = entry.getValue();

            // Create new row if needed - use Center layout
            int rowNum = index / slotsPerOfferRow;
            if (rowNum != currentRowNum) {
                currentRowNum = rowNum;
                commands.appendInline("#PartnerOfferSlotsContainer",
                    "Group #PartnerOfferRow" + rowNum + " { LayoutMode: Center; Anchor: (Height: 100); }");
            }

            // Append slot component to the current row
            commands.append("#PartnerOfferRow" + currentRowNum, PARTNER_SLOT_UI);

            // Calculate slot index within the row
            int slotInRow = index % slotsPerOfferRow;
            String slotSelector = "#PartnerOfferRow" + currentRowNum + "[" + slotInRow + "]";

            commands.set(slotSelector + " #SlotItem.ItemId", itemId);
            commands.set(slotSelector + " #SlotItem.Visible", true);
            commands.set(slotSelector + " #SlotQty.Text", "x" + quantity);

            index++;
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> entityRef,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull TradingPageData data) {
        super.handleDataEvent(entityRef, store, data);

        // Check if session is still valid, close UI if not
        Optional<TradeSession> optSession = tradeManager.getSession(playerRef);
        if (optSession.isEmpty() || closeRequested) {
            this.close();
            return;
        }

        TradeSession session = optSession.get();
        if (session.getState() == TradeState.CANCELLED ||
            session.getState() == TradeState.COMPLETED ||
            session.getState() == TradeState.FAILED) {
            this.close();
            return;
        }

        String action = data.getAction();

        if (action == null || action.isEmpty()) {
            return;
        }

        // Check for inventory changes before processing any action
        // This will auto-unaccept if inventory changed while accepted
        checkAndHandleInventoryChanges(store, entityRef);

        // Handle inventory slot actions (inv_[itemId]_[amount])
        if (action.startsWith(ACTION_INV_PREFIX)) {
            handleInventoryAction(action, store, entityRef);
            return;
        }

        // Handle offer slot actions (offer_[itemId]_[amount])
        if (action.startsWith(ACTION_OFFER_PREFIX)) {
            handleOfferAction(action, store, entityRef);
            return;
        }

        // Handle button actions
        switch (action) {
            case ACTION_ACCEPT:
                handleAccept();
                sendPageUpdate(entityRef, store, false);
                break;
            case ACTION_CONFIRM:
                handleConfirm(entityRef, store);
                break;
            case ACTION_CANCEL:
                handleCancel();
                break;
            default:
                LOGGER.atWarning().log("Unknown action: " + action);
        }
    }

    private void handleInventoryAction(String action, Store<EntityStore> store, Ref<EntityStore> entityRef) {
        // Parse action: inv_[safeItemId]_[amount]
        String remainder = action.substring(ACTION_INV_PREFIX.length());
        int lastUnderscore = remainder.lastIndexOf('_');
        if (lastUnderscore == -1) {
            LOGGER.atWarning().log("Invalid inventory action format: " + action);
            return;
        }

        String safeItemId = remainder.substring(0, lastUnderscore);
        String amountStr = remainder.substring(lastUnderscore + 1);

        // Convert safe ID back to original
        String itemId = safeItemId.replace("_US_", "_").replace("_DA_", "-");

        ConsolidatedItem item = consolidatedInventory.get(itemId);
        if (item == null) {
            LOGGER.atWarning().log("Item not found in inventory: " + itemId);
            return;
        }

        int amount;
        if (amountStr.equals("stack")) {
            // Transfer 1 stack (max stack size)
            amount = item.maxStackSize;
        } else {
            try {
                amount = Integer.parseInt(amountStr);
            } catch (NumberFormatException e) {
                LOGGER.atWarning().log("Invalid amount: " + amountStr);
                return;
            }
        }

        boolean needsRebuild = transferToOffer(itemId, amount, store, entityRef);
        sendPageUpdate(entityRef, store, needsRebuild);
    }

    private void handleOfferAction(String action, Store<EntityStore> store, Ref<EntityStore> entityRef) {
        // Parse action: offer_[safeItemId]_[amount]
        String remainder = action.substring(ACTION_OFFER_PREFIX.length());
        int lastUnderscore = remainder.lastIndexOf('_');
        if (lastUnderscore == -1) {
            LOGGER.atWarning().log("Invalid offer action format: " + action);
            return;
        }

        String safeItemId = remainder.substring(0, lastUnderscore);
        String amountStr = remainder.substring(lastUnderscore + 1);

        // Convert safe ID back to original
        String itemId = safeItemId.replace("_US_", "_").replace("_DA_", "-");

        ConsolidatedItem item = consolidatedInventory.get(itemId);
        int maxStackSize = item != null ? item.maxStackSize : TradeConstants.DEFAULT_MAX_STACK;

        int amount;
        if (amountStr.equals("stack")) {
            // Return 1 stack
            amount = maxStackSize;
        } else {
            try {
                amount = Integer.parseInt(amountStr);
            } catch (NumberFormatException e) {
                LOGGER.atWarning().log("Invalid amount: " + amountStr);
                return;
            }
        }

        boolean needsRebuild = returnFromOffer(itemId, amount, store, entityRef);
        sendPageUpdate(entityRef, store, needsRebuild);
    }

    /**
     * Transfer items from inventory to offer.
     * @return true if a new slot was created (requires rebuild), false if just quantity changed
     */
    private boolean transferToOffer(String itemId, int requestedAmount, Store<EntityStore> store, Ref<EntityStore> entityRef) {
        Optional<TradeSession> optSession = tradeManager.getSession(playerRef);
        if (optSession.isEmpty()) {
            setStatusError(TradeMessages.uiNoActiveSession());
            return false;
        }

        TradeSession session = optSession.get();

        // Auto-unaccept both players if trying to modify while accepted
        if (session.getState() == TradeState.ONE_ACCEPTED || session.getState() == TradeState.BOTH_ACCEPTED_COUNTDOWN) {
            session.revokeAllAcceptances();
            setStatusWarning(TradeMessages.uiAcceptRevoked());
            // Notify partner via their trading page
            tradeManager.notifyPartnerStatus(playerRef, TradeMessages.uiPartnerModified(), COLOR_WARNING);
        }

        ConsolidatedItem item = consolidatedInventory.get(itemId);
        if (item == null) {
            setStatusError(TradeMessages.uiItemNotFound());
            return false;
        }

        int available = item.getAvailable();
        if (available <= 0) {
            setStatusWarning(TradeMessages.uiNoItemsAvailable());
            return false;
        }

        // Cap the amount to what's available
        int actualAmount = Math.min(requestedAmount, available);

        TradeOffer myOffer = session.getOfferFor(playerRef);

        // Try to add to existing stack in offer first
        boolean addedToExisting = false;
        List<ItemStack> offerItems = myOffer.getItems();
        for (int i = 0; i < offerItems.size(); i++) {
            ItemStack offerItem = offerItems.get(i);
            if (offerItem != null && !offerItem.isEmpty() && offerItem.getItem().getId().equals(itemId)) {
                int newQty = offerItem.getQuantity() + actualAmount;
                myOffer.setItemQuantity(i, newQty);
                addedToExisting = true;
                break;
            }
        }

        // If not added to existing, add new slot (requires rebuild)
        boolean createdNewSlot = false;
        if (!addedToExisting) {
            // Use the actual Item object to create a proper ItemStack
            ItemStack newItem = new ItemStack(item.itemId, actualAmount);
            if (!myOffer.addItem(newItem)) {
                setStatusError(TradeMessages.uiFailedToAdd());
                return false;
            }
            createdNewSlot = true;
        }

        // Update tracking
        item.offeredQuantity += actualAmount;
        tradeManager.onOfferChanged(playerRef);

        // Clear any previous error/warning - show normal status
        setStatusNormal(TradeMessages.actionAddedToOffer(actualAmount));
        return createdNewSlot;
    }

    /**
     * Return items from offer back to inventory.
     * @return true if a slot was removed (requires rebuild), false if just quantity changed
     */
    private boolean returnFromOffer(String itemId, int requestedAmount, Store<EntityStore> store, Ref<EntityStore> entityRef) {
        Optional<TradeSession> optSession = tradeManager.getSession(playerRef);
        if (optSession.isEmpty()) {
            setStatusError(TradeMessages.uiNoActiveSession());
            return false;
        }

        TradeSession session = optSession.get();

        // Auto-unaccept both players if trying to modify while accepted
        if (session.getState() == TradeState.ONE_ACCEPTED || session.getState() == TradeState.BOTH_ACCEPTED_COUNTDOWN) {
            session.revokeAllAcceptances();
            setStatusWarning(TradeMessages.uiAcceptRevoked());
            // Notify partner via their trading page
            tradeManager.notifyPartnerStatus(playerRef, TradeMessages.uiPartnerModified(), COLOR_WARNING);
        }

        TradeOffer myOffer = session.getOfferFor(playerRef);

        // Find the item in offer
        int offerSlot = -1;
        int currentQty = 0;
        List<ItemStack> offerItems = myOffer.getItems();
        for (int i = 0; i < offerItems.size(); i++) {
            ItemStack offerItem = offerItems.get(i);
            if (offerItem != null && !offerItem.isEmpty() && offerItem.getItem().getId().equals(itemId)) {
                offerSlot = i;
                currentQty = offerItem.getQuantity();
                break;
            }
        }

        if (offerSlot == -1) {
            setStatusError(TradeMessages.uiItemNotFound());
            return false;
        }

        int actualAmount = Math.min(requestedAmount, currentQty);

        // Update offer - track if we removed a slot
        boolean removedSlot = false;
        if (actualAmount >= currentQty) {
            myOffer.removeItem(offerSlot);
            removedSlot = true;
        } else {
            myOffer.setItemQuantity(offerSlot, currentQty - actualAmount);
        }

        // Update tracking
        ConsolidatedItem item = consolidatedInventory.get(itemId);
        if (item != null) {
            item.offeredQuantity = Math.max(0, item.offeredQuantity - actualAmount);
        }

        tradeManager.onOfferChanged(playerRef);
        setStatusNormal(TradeMessages.actionReturnedFromOffer(actualAmount));
        return removedSlot;
    }

    private void handleAccept() {
        Optional<TradeSession> optSession = tradeManager.getSession(playerRef);
        if (optSession.isEmpty()) {
            setStatusError(TradeMessages.uiNoActiveSession());
            return;
        }

        TradeSession session = optSession.get();

        if (session.hasAccepted(playerRef)) {
            if (tradeManager.revokeAccept(playerRef)) {
                setStatusWarning(TradeMessages.uiAcceptRevokedManual());
                stopCountdownTimer();
            }
        } else {
            // Validate inventory before accepting
            if (!validateOfferAgainstInventory(session)) {
                setStatusError(TradeMessages.uiFailedValidation());
                return;
            }

            // Validate inventory space
            if (!validateInventorySpace(session)) {
                setStatusError(TradeMessages.uiNotEnoughSpace());
                return;
            }

            if (tradeManager.acceptTrade(playerRef)) {
                if (session.getState() == TradeState.BOTH_ACCEPTED_COUNTDOWN) {
                    setStatusSuccess(TradeMessages.uiBothAccepted());
                    startCountdownTimer();
                } else {
                    setStatusNormal(TradeMessages.uiStatusAcceptedWaiting());
                }
            } else {
                setStatusError(TradeMessages.uiCannotAcceptState());
            }
        }
    }

    private void handleConfirm(Ref<EntityStore> entityRef, Store<EntityStore> store) {
        Optional<TradeSession> optSession = tradeManager.getSession(playerRef);
        if (optSession.isEmpty()) {
            setStatusError(TradeMessages.uiNoActiveSession());
            return;
        }

        TradeSession session = optSession.get();

        if (session.getState() != TradeState.BOTH_ACCEPTED_COUNTDOWN) {
            setStatusWarning(TradeMessages.errorAcceptFirst().getAnsiMessage());
            return;
        }

        if (!session.isCountdownComplete()) {
            long remaining = session.getRemainingCountdownMs();
            setStatusWarning(TradeMessages.uiWaitMoreSeconds(remaining / 1000));
            return;
        }

        TradeSession.TradeResult result = tradeManager.confirmTrade(playerRef, store, entityRef);

        if (result.success) {
            setStatusSuccess(TradeMessages.uiTradeCompleted());
            this.close();
        } else {
            // Trade failed - show appropriate message based on who caused it
            boolean iAmInitiator = playerRef.getUuid().equals(session.getInitiator().getUuid());
            String myMessage;
            String otherMessage;

            if (result.cause == TradeSession.TradeResult.FailureCause.INITIATOR) {
                myMessage = iAmInitiator ? result.message : result.messageForOther;
                otherMessage = iAmInitiator ? result.messageForOther : result.message;
            } else if (result.cause == TradeSession.TradeResult.FailureCause.TARGET) {
                myMessage = iAmInitiator ? result.messageForOther : result.message;
                otherMessage = iAmInitiator ? result.message : result.messageForOther;
            } else {
                // System error - same message for both
                myMessage = result.message;
                otherMessage = result.message;
            }

            // Set error status first (this schedules a 5-second reset and activates temporary status)
            setStatusError(myMessage);

            // Then refresh UI - status update will be skipped since temporary status is active
            sendPageUpdate(entityRef, store, true);

            // Notify partner's UI with their message (via tradeManager)
            if (otherMessage != null) {
                tradeManager.notifyPartnerStatus(playerRef, otherMessage, COLOR_ERROR);
            }
        }
    }

    private void handleCancel() {
        if (tradeManager.cancelTrade(playerRef)) {
            this.close();
        } else {
            setStatusError(TradeMessages.cancelFailed().getAnsiMessage());
        }
    }

    private void sendPageUpdate(Ref<EntityStore> entityRef, Store<EntityStore> store, boolean needsRebuild) {
        // Check for inventory changes (auto-unaccept, remove invalid offers)
        checkAndHandleInventoryChanges(store, entityRef);

        // Use sendUpdate to update UI without resetting scroll position
        // Re-initialize consolidated inventory to reflect current state
        initializeConsolidatedInventory(store, entityRef);

        // Get trade session for offer data
        Optional<TradeSession> optSession = tradeManager.getSession(playerRef);
        if (optSession.isEmpty()) {
            return;
        }
        TradeSession session = optSession.get();

        // Create new builders for incremental update
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();

        // Clear existing slot containers
        commands.clear("#InventorySlotsContainer");
        commands.clear("#MyOfferSlotsContainer");
        commands.clear("#PartnerOfferSlotsContainer");

        // Rebuild slots
        buildInventorySlots(commands, events);
        buildMyOfferSlots(commands, events, session);
        buildPartnerOfferSlots(commands, session);

        // Update status UI, but skip if a temporary status (warning/error) is being displayed
        // This prevents overwriting error messages that should be visible for 5 seconds
        if (!isTemporaryStatusActive()) {
            updateStatusUI(commands, session);
        } else {
            // Still update accept statuses even when skipping status message
            boolean iAmInitiator = playerRef.getUuid().equals(session.getInitiator().getUuid());
            boolean myAccepted = iAmInitiator ? session.isInitiatorAccepted() : session.isTargetAccepted();
            boolean partnerAccepted = iAmInitiator ? session.isTargetAccepted() : session.isInitiatorAccepted();
            commands.set("#MyAcceptStatus.Text", myAccepted ? TradeMessages.uiStatusAccepted() : TradeMessages.uiStatusNotAccepted());
            commands.set("#MyAcceptStatus.Style.TextColor", myAccepted ? COLOR_SUCCESS : COLOR_ERROR);
            commands.set("#PartnerAcceptStatus.Text", partnerAccepted ? TradeMessages.uiStatusAccepted() : TradeMessages.uiStatusNotAccepted());
            commands.set("#PartnerAcceptStatus.Style.TextColor", partnerAccepted ? COLOR_SUCCESS : COLOR_ERROR);
        }

        // Send update without full rebuild (preserves scroll position)
        sendUpdate(commands, events, false);
    }

    private void updateStatusUI(UICommandBuilder commands, TradeSession session) {
        boolean iAmInitiator = playerRef.getUuid().equals(session.getInitiator().getUuid());
        boolean myAccepted = iAmInitiator ? session.isInitiatorAccepted() : session.isTargetAccepted();
        boolean partnerAccepted = iAmInitiator ? session.isTargetAccepted() : session.isInitiatorAccepted();

        String acceptedText = TradeMessages.uiStatusAccepted();
        String notAcceptedText = TradeMessages.uiStatusNotAccepted();
        commands.set("#MyAcceptStatus.Text", myAccepted ? acceptedText : notAcceptedText);
        commands.set("#MyAcceptStatus.Style.TextColor", myAccepted ? COLOR_SUCCESS : COLOR_ERROR);
        commands.set("#PartnerAcceptStatus.Text", partnerAccepted ? acceptedText : notAcceptedText);
        commands.set("#PartnerAcceptStatus.Style.TextColor", partnerAccepted ? COLOR_SUCCESS : COLOR_ERROR);

        String statusMsg;
        TradeState state = session.getState();

        String statusColor = COLOR_NORMAL;

        switch (state) {
            case NEGOTIATING:
                statusMsg = TradeMessages.uiClickInstructions();
                statusColor = COLOR_NORMAL;
                commands.set("#CountdownTimer.Text", "");
                stopCountdownTimer();
                break;
            case ONE_ACCEPTED:
                statusMsg = myAccepted ? TradeMessages.uiWaitingForPartner() : TradeMessages.uiPartnerAccepted();
                statusColor = myAccepted ? COLOR_NORMAL : COLOR_WARNING;
                commands.set("#CountdownTimer.Text", "");
                stopCountdownTimer();
                break;
            case BOTH_ACCEPTED_COUNTDOWN:
                long remaining = session.getRemainingCountdownMs();
                long displaySeconds = (remaining + 999) / 1000;
                if (remaining > 0) {
                    statusMsg = TradeMessages.statusCountdown(displaySeconds).getAnsiMessage();
                    statusColor = COLOR_SUCCESS;
                    commands.set("#CountdownTimer.Text", displaySeconds + "s");
                    // Start countdown timer if not already running
                    if (countdownUpdateTask == null || countdownUpdateTask.isDone()) {
                        startCountdownTimer();
                    }
                } else {
                    statusMsg = TradeMessages.uiCountdownReady();
                    statusColor = COLOR_SUCCESS;
                    commands.set("#CountdownTimer.Text", TradeMessages.uiStatusReady());
                }
                break;
            default:
                statusMsg = "State: " + state.name();
                statusColor = COLOR_NORMAL;
                commands.set("#CountdownTimer.Text", "");
        }

        commands.set("#StatusMessage.Text", statusMsg);
        commands.set("#StatusMessage.Style.TextColor", statusColor);

        int totalOffered = myOfferItems.values().stream().mapToInt(Integer::intValue).sum();
        commands.set("#DebugInfo.Text", "State: " + state.name() + " | " +
            consolidatedInventory.size() + " unique items | Offered: " + totalOffered);
    }

    /**
     * Refresh the status UI to show the normal state-based message.
     * Used by the status reset timer to restore the default status after temporary messages.
     */
    private void refreshStatusUI() {
        Optional<TradeSession> optSession = tradeManager.getSession(playerRef);
        if (optSession.isEmpty()) {
            return;
        }
        TradeSession session = optSession.get();
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        updateStatusUI(commands, session);
        sendUpdate(commands, events, false);
    }

    /**
     * Start the countdown UI update timer.
     * Updates the countdown display every 500ms while in BOTH_ACCEPTED_COUNTDOWN state.
     */
    private void startCountdownTimer() {
        // Don't start if scheduler is shut down (UI is closing)
        if (countdownScheduler.isShutdown() || countdownScheduler.isTerminated()) {
            return;
        }

        stopCountdownTimer(); // Cancel any existing timer

        lastCountdownValue = -1;

        try {
            countdownUpdateTask = countdownScheduler.scheduleAtFixedRate(() -> {
            try {
                Optional<TradeSession> optSession = tradeManager.getSession(playerRef);
                if (optSession.isEmpty()) {
                    stopCountdownTimer();
                    return;
                }

                TradeSession session = optSession.get();
                if (session.getState() != TradeState.BOTH_ACCEPTED_COUNTDOWN) {
                    // State changed, hide timer and stop
                    UICommandBuilder commands = new UICommandBuilder();
                    UIEventBuilder events = new UIEventBuilder();
                    commands.set("#CountdownTimer.Text", "");
                    sendUpdate(commands, events, false);
                    stopCountdownTimer();
                    return;
                }

                long remaining = session.getRemainingCountdownMs();
                // Add 999ms to round up (so 2001ms shows as 3s, 1001ms shows as 2s, etc.)
                long displaySeconds = (remaining + 999) / 1000;

                // Use -2 as special value for "READY" state
                long newValue = remaining <= 0 ? -2 : displaySeconds;

                // Only update if the value changed
                if (newValue != lastCountdownValue) {
                    lastCountdownValue = newValue;

                    UICommandBuilder commands = new UICommandBuilder();
                    UIEventBuilder events = new UIEventBuilder();

                    // Update countdown timer display
                    if (remaining > 0) {
                        commands.set("#CountdownTimer.Text", displaySeconds + "s");
                    } else {
                        commands.set("#CountdownTimer.Text", TradeMessages.uiStatusReady());
                    }

                    // Only update status message if no temporary status is active
                    // (allows "Both accepted!" message to display for 5 seconds)
                    if (!isTemporaryStatusActive()) {
                        if (remaining > 0) {
                            commands.set("#StatusMessage.Text", TradeMessages.statusCountdown(displaySeconds).getAnsiMessage());
                        } else {
                            commands.set("#StatusMessage.Text", TradeMessages.uiCountdownReady());
                        }
                    }

                    sendUpdate(commands, events, false);

                    // Stop timer after showing READY
                    if (remaining <= 0) {
                        stopCountdownTimer();
                    }
                }
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Error updating countdown");
            }
        }, 0, COUNTDOWN_UPDATE_INTERVAL_MS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // Scheduler was shut down between the check and the schedule call
            Common.logDebug(LOGGER, "Countdown task rejected - scheduler shut down");
        }
    }

    /**
     * Stop the countdown UI update timer.
     */
    private void stopCountdownTimer() {
        if (countdownUpdateTask != null && !countdownUpdateTask.isDone()) {
            countdownUpdateTask.cancel(false);
            countdownUpdateTask = null;
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        // Stop countdown timer and status reset timer
        stopCountdownTimer();
        cancelStatusReset();
        countdownScheduler.shutdown();
        // Unregister from inventory change events
        tradeManager.unregisterTradingPage(playerRef);
    }

    /**
     * Public method to close this trading page.
     * Can be called from TradeManager to close the UI.
     */
    public void closeUI() {
        close();
    }
}
