package org.toskan4134.hytrade.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
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
import org.toskan4134.hytrade.constants.TradeConstants;
import org.toskan4134.hytrade.trade.TradeManager;
import org.toskan4134.hytrade.trade.TradeOffer;
import org.toskan4134.hytrade.trade.TradeSession;
import org.toskan4134.hytrade.trade.TradeState;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * UI Controller for the trading interface.
 * Displays consolidated inventories and handles trade interactions with quantity controls.
 */
public class TradingPage extends InteractiveCustomUIPage<TradingPage.TradingPageData> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String PAGE_LAYOUT = "Pages/Toskan4134_Trading_TradingPage.ui";

    // Slot component .ui files
    private static final String INVENTORY_SLOT_UI = "Pages/Toskan4134_Trading_InventorySlot.ui";
    private static final String OFFER_SLOT_UI = "Pages/Toskan4134_Trading_OfferSlot.ui";
    private static final String PARTNER_SLOT_UI = "Pages/Toskan4134_Trading_PartnerSlot.ui";

    // Default max stack size when we can't determine it - use constant from TradeConstants

    // Event action keys
    private static final String KEY_ACTION = "Action";
    private static final String ACTION_ACCEPT = "accept";
    private static final String ACTION_CONFIRM = "confirm";
    private static final String ACTION_CANCEL = "cancel";

    // Action prefixes: inv_[itemId]_[amount], offer_[itemId]_[amount]
    private static final String ACTION_INV_PREFIX = "inv_";
    private static final String ACTION_OFFER_PREFIX = "offer_";

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

    /**
     * Check if a temporary status message is currently being displayed.
     * Used to prevent updateStatusUI from overwriting warning/error messages.
     */
    private boolean isTemporaryStatusActive() {
        return statusResetTask != null && !statusResetTask.isDone();
    }

    /**
     * Represents a consolidated item (multiple stacks merged into one entry)
     */
    private static class ConsolidatedItem {
        String itemId;
        Item item; // Store the actual Item object for creating valid ItemStacks
        int totalQuantity;
        int offeredQuantity;
        int maxStackSize;

        ConsolidatedItem(String itemId, Item item, int maxStackSize) {
            this.itemId = itemId;
            this.item = item;
            this.totalQuantity = 0;
            this.offeredQuantity = 0;
            this.maxStackSize = maxStackSize;
        }

        int getAvailable() {
            return totalQuantity - offeredQuantity;
        }
    }

    public TradingPage(PlayerRef playerRef, TradeManager tradeManager,
                       Store<EntityStore> store, Ref<EntityStore> entityRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, TradingPageData.CODEC);
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
        cancelStatusReset();
        statusResetTask = countdownScheduler.schedule(() -> {
            try {
                refreshStatusUI();
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Error resetting status message");
            }
        }, TradeConstants.STATUS_RESET_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Update status message with normal (white) color.
     * Normal messages are persistent (no auto-reset).
     */
    private void setStatusNormal(String message) {
        cancelStatusReset(); // Cancel any pending reset since we're setting a normal message
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        commands.set("#StatusMessage.Text", message);
        commands.set("#StatusMessage.Style.TextColor", COLOR_NORMAL);
        sendUpdate(commands, events, false);
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
     * Success messages are persistent (no auto-reset).
     */
    private void setStatusSuccess(String message) {
        cancelStatusReset(); // Cancel any pending reset since success is a final state
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        commands.set("#StatusMessage.Text", message);
        commands.set("#StatusMessage.Style.TextColor", COLOR_SUCCESS);
        sendUpdate(commands, events, false);
    }

    /**
     * Set status message in a UICommandBuilder (for batch updates).
     * Does NOT schedule auto-reset - caller must handle that separately if needed.
     */
    private void setStatusInCommands(UICommandBuilder commands, String message, String color) {
        commands.set("#StatusMessage.Text", message);
        commands.set("#StatusMessage.Style.TextColor", color);
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

    // Expose color constants for external use
    public static String getColorNormal() { return COLOR_NORMAL; }
    public static String getColorWarning() { return COLOR_WARNING; }
    public static String getColorError() { return COLOR_ERROR; }
    public static String getColorSuccess() { return COLOR_SUCCESS; }

    @Override
    public void build(@Nonnull Ref<EntityStore> entityRef,
                      @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        //LOGGER.atInfo().log("build() called");

        // Load the UI template
        commands.append(PAGE_LAYOUT);

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
            commands.set("#StatusMessage.Text", "No active trade session");
            commands.set("#DebugInfo.Text", "Use /trade test to start");
            return;
        }

        TradeSession session = optSession.get();

        // Set partner name
        PlayerRef partner = session.getOtherPlayer(playerRef);
        String partnerName = session.isTestMode() ? "Test Partner (You)" :
                (partner != null ? partner.getUsername() : "Unknown");
        commands.set("#PartnerName.Text", "Trading with: " + partnerName);

        // Initialize consolidated inventory
        initializeConsolidatedInventory(store, entityRef);

        // Initialize inventory snapshot for change detection
        previousInventorySnapshot.clear();
        previousInventorySnapshot.putAll(getCurrentInventorySnapshot(store, entityRef));

        // Build dynamic UI elements
        buildInventorySlots(commands, events);
        buildMyOfferSlots(commands, events, session);
        buildPartnerOfferSlots(commands, session);

        // Update status
        updateStatusUI(commands, session);

        // Register for inventory change events (also stores entityRef for trade execution and this page instance)
        tradeManager.registerTradingPage(playerRef, this::onInventoryChangedEvent, entityRef, this::setStatus, this);
    }

    /**
     * Called when player's inventory changes (from event listener)
     * This runs on the correct thread since it's triggered by the event system
     */
    private void onInventoryChangedEvent() {
        //LOGGER.atInfo().log("=== onInventoryChangedEvent START ===");
        //LOGGER.atInfo().log("PlayerRef UUID: " + playerRef.getUuid());

        // Get session info before checking changes
        Optional<TradeSession> preCheckSession = tradeManager.getSession(playerRef);
        if (preCheckSession.isPresent()) {
            TradeSession s = preCheckSession.get();
            //LOGGER.atInfo().log("Session state BEFORE check: " + s.getState());
            //LOGGER.atInfo().log("Has accepted BEFORE check: " + s.hasAccepted(playerRef));
        } else {
            //LOGGER.atInfo().log("No session found BEFORE check!");
        }

        // Check and handle inventory changes
        checkAndHandleInventoryChanges(store, entityRef);

        // Refresh the UI
        Optional<TradeSession> optSession = tradeManager.getSession(playerRef);
        if (optSession.isEmpty()) {
            //LOGGER.atInfo().log("No session found AFTER check, returning");
            return;
        }
        TradeSession session = optSession.get();
        //LOGGER.atInfo().log("Session state AFTER check: " + session.getState());
        //LOGGER.atInfo().log("Has accepted AFTER check: " + session.hasAccepted(playerRef));

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
            commands.set("#MyAcceptStatus.Text", myAccepted ? "ACCEPTED" : "Not accepted");
            commands.set("#MyAcceptStatus.Style.TextColor", myAccepted ? COLOR_SUCCESS : COLOR_ERROR);
            commands.set("#PartnerAcceptStatus.Text", partnerAccepted ? "ACCEPTED" : "Not accepted");
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
                                //LOGGER.atInfo().log("Already offered: " + itemId + " x" + offeredQty);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            //LOGGER.atWarning().withCause(e).log("Failed to initialize consolidated inventory");
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
            //LOGGER.atWarning().withCause(e).log("Failed to get inventory snapshot");
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
                //LOGGER.atWarning().log("Offer validation failed: " + itemId +
                //" offered=" + offeredQty + " available=" + availableQty);
                return false;
            }
        }
        return true;
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
                LOGGER.atInfo().log("Skipping inventory change handling - trade state: " + currentState);
                return;
            }
        }

        Map<String, Integer> currentSnapshot = getCurrentInventorySnapshot(store, entityRef);
        //LOGGER.atInfo().log("Current snapshot size: " + currentSnapshot.size());
        //LOGGER.atInfo().log("Previous snapshot size: " + previousInventorySnapshot.size());

        // First time - just save snapshot
        if (previousInventorySnapshot.isEmpty()) {
            //LOGGER.atInfo().log("Previous snapshot was empty - saving current and returning");
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

            LOGGER.atInfo().log("Comparing " + itemId + ": prev=" + prevQty + " current=" + currentQty);

            if (currentQty < prevQty) {
                hasChanges = true;
                decreasedItems.put(itemId, prevQty - currentQty);
                LOGGER.atInfo().log("DETECTED DECREASE: " + itemId + " from " + prevQty + " to " + currentQty);
            }
        }

        // Check for new items
        for (Map.Entry<String, Integer> curr : currentSnapshot.entrySet()) {
            String itemId = curr.getKey();
            if (!previousInventorySnapshot.containsKey(itemId)) {
                hasChanges = true;
                //LOGGER.atInfo().log("DETECTED NEW ITEM: " + itemId + " x" + curr.getValue());
            } else if (curr.getValue() > previousInventorySnapshot.get(itemId)) {
                hasChanges = true;
                //LOGGER.atInfo().log("DETECTED INCREASE: " + itemId + " to " + curr.getValue());
            }
        }

        //LOGGER.atInfo().log("hasChanges = " + hasChanges);
        //LOGGER.atInfo().log("decreasedItems = " + decreasedItems);

        if (hasChanges) {
            Optional<TradeSession> optSession = tradeManager.getSession(playerRef);
            //LOGGER.atInfo().log("Session present: " + optSession.isPresent());

            if (optSession.isPresent()) {
                TradeSession session = optSession.get();
                boolean hasAccepted = session.hasAccepted(playerRef);
                //LOGGER.atInfo().log("Player has accepted: " + hasAccepted);

                // Auto-unaccept if player had accepted
                if (hasAccepted) {
                    boolean revoked = tradeManager.revokeAccept(playerRef);
                    setStatusWarning(playerRef.getUsername() + "'s inventory has been modified");
                }

                // Remove items from offer if they're no longer available
                TradeOffer myOffer = session.getOfferFor(playerRef);
                LOGGER.atInfo().log("My offer: " + (myOffer != null ? myOffer.getItems().size() + " items" : "null"));
                LOGGER.atInfo().log("decreasedItems: " + decreasedItems);

                if (myOffer != null && !decreasedItems.isEmpty()) {
                    LOGGER.atInfo().log("Calling handleDecreasedItemsInOffer...");
                    handleDecreasedItemsInOffer(myOffer, decreasedItems, currentSnapshot, session);
                } else {
                    LOGGER.atInfo().log("NOT calling handleDecreasedItemsInOffer - myOffer=" + myOffer + " decreasedItems.isEmpty=" + decreasedItems.isEmpty());
                }
            }
        } else {
            LOGGER.atInfo().log("No changes detected - snapshots appear identical");
        }

        // Update snapshot
        previousInventorySnapshot.clear();
        previousInventorySnapshot.putAll(currentSnapshot);
        //LOGGER.atInfo().log("=== checkAndHandleInventoryChanges END ===");
    }

    /**
     * Handle items that decreased in inventory - remove from offer if necessary
     */
    private void handleDecreasedItemsInOffer(TradeOffer myOffer, Map<String, Integer> decreasedItems,
                                              Map<String, Integer> currentInventory, TradeSession session) {
        LOGGER.atInfo().log("handleDecreasedItemsInOffer called - decreasedItems: " + decreasedItems);
        LOGGER.atInfo().log("currentInventory: " + currentInventory);
        LOGGER.atInfo().log("Offer locked: " + myOffer.isLocked());

        List<ItemStack> offerItems = myOffer.getItems();
        LOGGER.atInfo().log("Offer items count: " + offerItems.size());
        boolean offerChanged = false;

        for (int i = offerItems.size() - 1; i >= 0; i--) {
            ItemStack offerItem = offerItems.get(i);
            if (offerItem == null || offerItem.isEmpty()) continue;

            String itemId = offerItem.getItem().getId();
            int offeredQty = offerItem.getQuantity();
            int availableQty = currentInventory.getOrDefault(itemId, 0);

            LOGGER.atInfo().log("Checking offer item: " + itemId + " offered=" + offeredQty + " available=" + availableQty);

            if (availableQty < offeredQty) {
                // Need to reduce or remove from offer
                if (availableQty <= 0) {
                    // Remove entirely
                    ItemStack removed = myOffer.removeItem(i);
                    LOGGER.atInfo().log("Removed item from offer: " + (removed != null ? "success" : "FAILED"));
                    setStatusWarning("Removed " + itemId + " from offer");
                } else {
                    // Reduce quantity
                    boolean success = myOffer.setItemQuantity(i, availableQty);
                    LOGGER.atInfo().log("Set item quantity to " + availableQty + ": " + (success ? "success" : "FAILED"));
                    setStatusWarning("Reduced " + itemId + " to " + availableQty);
                }
                offerChanged = true;
            }
        }

        if (offerChanged) {
            LOGGER.atInfo().log("Offer changed, notifying tradeManager");
            tradeManager.onOfferChanged(playerRef);
        } else {
            LOGGER.atInfo().log("No offer changes needed");
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

             //LOGGER.atInfo().log("Built inventory slot " + index + " (row " + currentRowNum +
            //", slot " + slotInRow + "): " + item.itemId + " x" + item.getAvailable() + "]");
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
        int slotsPerOfferRow = 4; // Wider container fits ~4 slots

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

            // //LOGGER.atInfo().log("Built my offer slot " + index + ": " + itemId + " x" + quantity + "]");
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
        int slotsPerOfferRow = 5; // Partner slots are smaller (80px), fit ~5 per row

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

            // //LOGGER.atInfo().log("Built partner slot " + index + ": " + itemId + " x" + quantity + "]");
            index++;
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> entityRef,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull TradingPageData data) {
        super.handleDataEvent(entityRef, store, data);

        String action = data.getAction();
        //LOGGER.atInfo().log("handleDataEvent - action: " + action);

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
                //LOGGER.atWarning().log("Unknown action: " + action);
        }
    }

    private void handleInventoryAction(String action, Store<EntityStore> store, Ref<EntityStore> entityRef) {
        // Parse action: inv_[safeItemId]_[amount]
        String remainder = action.substring(ACTION_INV_PREFIX.length());
        int lastUnderscore = remainder.lastIndexOf('_');
        if (lastUnderscore == -1) {
            //LOGGER.atWarning().log("Invalid inventory action format: " + action);
            return;
        }

        String safeItemId = remainder.substring(0, lastUnderscore);
        String amountStr = remainder.substring(lastUnderscore + 1);

        // Convert safe ID back to original
        String itemId = safeItemId.replace("_US_", "_").replace("_DA_", "-");

        ConsolidatedItem item = consolidatedInventory.get(itemId);
        if (item == null) {
            //LOGGER.atWarning().log("Item not found in inventory: " + itemId);
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
                //LOGGER.atWarning().log("Invalid amount: " + amountStr);
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
            //LOGGER.atWarning().log("Invalid offer action format: " + action);
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
                //LOGGER.atWarning().log("Invalid amount: " + amountStr);
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
            setStatusError("No active trade session");
            return false;
        }

        TradeSession session = optSession.get();

        // Auto-unaccept both players if trying to modify while accepted
        if (session.getState() == TradeState.ONE_ACCEPTED || session.getState() == TradeState.BOTH_ACCEPTED_COUNTDOWN) {
            session.revokeAllAcceptances();
            setStatusWarning("Acceptances revoked - offer modified");
            // Notify partner via their trading page
            tradeManager.notifyPartnerStatus(playerRef, "Partner modified their offer", COLOR_WARNING);
        }

        ConsolidatedItem item = consolidatedInventory.get(itemId);
        if (item == null) {
            setStatusError("Item not found");
            return false;
        }

        int available = item.getAvailable();
        if (available <= 0) {
            setStatusWarning("No items available to offer");
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
                setStatusError("Failed to add item to offer");
                return false;
            }
            createdNewSlot = true;
        }

        // Update tracking
        item.offeredQuantity += actualAmount;
        tradeManager.onOfferChanged(playerRef);

        // Clear any previous error/warning - show normal status
        setStatusNormal("Added x" + actualAmount + " to offer");
        return createdNewSlot;
    }

    /**
     * Return items from offer back to inventory.
     * @return true if a slot was removed (requires rebuild), false if just quantity changed
     */
    private boolean returnFromOffer(String itemId, int requestedAmount, Store<EntityStore> store, Ref<EntityStore> entityRef) {
        Optional<TradeSession> optSession = tradeManager.getSession(playerRef);
        if (optSession.isEmpty()) {
            setStatusError("No active trade session");
            return false;
        }

        TradeSession session = optSession.get();

        // Auto-unaccept both players if trying to modify while accepted
        if (session.getState() == TradeState.ONE_ACCEPTED || session.getState() == TradeState.BOTH_ACCEPTED_COUNTDOWN) {
            session.revokeAllAcceptances();
            setStatusWarning("Acceptances revoked - offer modified");
            // Notify partner via their trading page
            tradeManager.notifyPartnerStatus(playerRef, "Partner modified their offer", COLOR_WARNING);
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
            setStatusError("Item not found in offer");
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
        setStatusNormal("Returned x" + actualAmount + " from offer");
        return removedSlot;
    }

    private void handleAccept() {
        Optional<TradeSession> optSession = tradeManager.getSession(playerRef);
        if (optSession.isEmpty()) {
            setStatusError("No active trade session");
            return;
        }

        TradeSession session = optSession.get();

        if (session.hasAccepted(playerRef)) {
            if (tradeManager.revokeAccept(playerRef)) {
                setStatusWarning("Acceptance revoked");
                stopCountdownTimer();
            }
        } else {
            // Validate inventory before accepting
            if (!validateOfferAgainstInventory(session)) {
                setStatusError("Cannot accept - items no longer available");
                return;
            }

            if (tradeManager.acceptTrade(playerRef)) {
                if (session.getState() == TradeState.BOTH_ACCEPTED_COUNTDOWN) {
                    setStatusSuccess("Both accepted! Wait for countdown");
                    startCountdownTimer();
                } else {
                    setStatusNormal("Accepted! Waiting for partner...");
                }
            } else {
                setStatusError("Cannot accept in current state");
            }
        }
    }

    private void handleConfirm(Ref<EntityStore> entityRef, Store<EntityStore> store) {
        Optional<TradeSession> optSession = tradeManager.getSession(playerRef);
        if (optSession.isEmpty()) {
            setStatusError("No active trade session");
            return;
        }

        TradeSession session = optSession.get();

        if (session.getState() != TradeState.BOTH_ACCEPTED_COUNTDOWN) {
            setStatusWarning("Both players must accept first");
            return;
        }

        if (!session.isCountdownComplete()) {
            long remaining = session.getRemainingCountdownMs();
            setStatusWarning("Wait " + (remaining / 1000) + " more seconds");
            return;
        }

        TradeSession.TradeResult result = tradeManager.confirmTrade(playerRef, store, entityRef);

        if (result.success) {
            setStatusSuccess("Trade completed successfully!");
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
            setStatusError("Failed to cancel trade");
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
            commands.set("#MyAcceptStatus.Text", myAccepted ? "ACCEPTED" : "Not accepted");
            commands.set("#MyAcceptStatus.Style.TextColor", myAccepted ? COLOR_SUCCESS : COLOR_ERROR);
            commands.set("#PartnerAcceptStatus.Text", partnerAccepted ? "ACCEPTED" : "Not accepted");
            commands.set("#PartnerAcceptStatus.Style.TextColor", partnerAccepted ? COLOR_SUCCESS : COLOR_ERROR);
        }

        // Send update without full rebuild (preserves scroll position)
        sendUpdate(commands, events, false);
    }

    private void updateStatusUI(UICommandBuilder commands, TradeSession session) {
        boolean iAmInitiator = playerRef.getUuid().equals(session.getInitiator().getUuid());
        boolean myAccepted = iAmInitiator ? session.isInitiatorAccepted() : session.isTargetAccepted();
        boolean partnerAccepted = iAmInitiator ? session.isTargetAccepted() : session.isInitiatorAccepted();

        commands.set("#MyAcceptStatus.Text", myAccepted ? "ACCEPTED" : "Not accepted");
        commands.set("#MyAcceptStatus.Style.TextColor", myAccepted ? COLOR_SUCCESS : COLOR_ERROR);
        commands.set("#PartnerAcceptStatus.Text", partnerAccepted ? "ACCEPTED" : "Not accepted");
        commands.set("#PartnerAcceptStatus.Style.TextColor", partnerAccepted ? COLOR_SUCCESS : COLOR_ERROR);

        String statusMsg;
        TradeState state = session.getState();

        String statusColor = COLOR_NORMAL;

        switch (state) {
            case NEGOTIATING:
                statusMsg = "Click item = 1 stack, use +1/+10 buttons";
                statusColor = COLOR_NORMAL;
                commands.set("#CountdownTimer.Text", "");
                stopCountdownTimer();
                break;
            case ONE_ACCEPTED:
                statusMsg = myAccepted ? "Waiting for partner..." : "Partner accepted! Click ACCEPT";
                statusColor = myAccepted ? COLOR_NORMAL : COLOR_WARNING;
                commands.set("#CountdownTimer.Text", "");
                stopCountdownTimer();
                break;
            case BOTH_ACCEPTED_COUNTDOWN:
                long remaining = session.getRemainingCountdownMs();
                long displaySeconds = (remaining + 999) / 1000;
                if (remaining > 0) {
                    statusMsg = "Both accepted! Confirm in " + displaySeconds + "s";
                    statusColor = COLOR_SUCCESS;
                    commands.set("#CountdownTimer.Text", displaySeconds + "s");
                    // Start countdown timer if not already running
                    if (countdownUpdateTask == null || countdownUpdateTask.isDone()) {
                        startCountdownTimer();
                    }
                } else {
                    statusMsg = "Ready! Click CONFIRM to complete";
                    statusColor = COLOR_SUCCESS;
                    commands.set("#CountdownTimer.Text", "READY");
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
        stopCountdownTimer(); // Cancel any existing timer

        //LOGGER.atInfo().log("Starting countdown timer");
        lastCountdownValue = -1;

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

                    if (remaining > 0) {
                        commands.set("#CountdownTimer.Text", displaySeconds + "s");
                        commands.set("#StatusMessage.Text", "Both accepted! Confirm in " + displaySeconds + "s");
                    } else {
                        commands.set("#CountdownTimer.Text", "READY");
                        commands.set("#StatusMessage.Text", "Ready! Click CONFIRM to complete");
                    }

                    sendUpdate(commands, events, false);
                    //LOGGER.atInfo().log("Countdown update: " + (remaining > 0 ? displaySeconds + "s" : "READY"));

                    // Stop timer after showing READY
                    if (remaining <= 0) {
                        stopCountdownTimer();
                    }
                }
            } catch (Exception e) {
                //LOGGER.atWarning().withCause(e).log("Error updating countdown");
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    /**
     * Stop the countdown UI update timer.
     */
    private void stopCountdownTimer() {
        if (countdownUpdateTask != null && !countdownUpdateTask.isDone()) {
            //LOGGER.atInfo().log("Stopping countdown timer");
            countdownUpdateTask.cancel(false);
            countdownUpdateTask = null;
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        //LOGGER.atInfo().log("TradingPage dismissed");
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

    /**
     * Data class for UI events.
     */
    public static class TradingPageData {
        public static final BuilderCodec<TradingPageData> CODEC;

        static {
            BuilderCodec<TradingPageData> codec;
            try {
                codec = BuilderCodec.builder(TradingPageData.class, TradingPageData::new)
                        .addField(
                                new KeyedCodec<>("Action", Codec.STRING),
                                (data, value) -> data.action = value,
                                data -> data.action
                        )
                        .build();
            } catch (Exception e) {
                codec = BuilderCodec.builder(TradingPageData.class, TradingPageData::new).build();
                e.printStackTrace();
            }
            CODEC = codec;
        }

        public String action = "";

        public TradingPageData() {
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }
    }
}
