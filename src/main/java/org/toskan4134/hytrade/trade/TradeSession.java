package org.toskan4134.hytrade.trade;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.toskan4134.hytrade.util.InventoryHelper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

/**
 * Represents an active trade session between two players.
 * Handles the state machine for trade negotiation and execution.
 */
public class TradeSession {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final long COUNTDOWN_DURATION_MS = 3000; // 3 seconds

    private final UUID sessionId;
    private final PlayerRef initiator;
    private final PlayerRef target;
    private final TradeOffer initiatorOffer;
    private final TradeOffer targetOffer;
    private final long createdAt;
    private final boolean testMode;

    private TradeState state;
    private boolean initiatorAccepted;
    private boolean targetAccepted;
    private long countdownStartTime;
    private ScheduledFuture<?> countdownTask;

    // Inventory locks
    private boolean initiatorInventoryLocked;
    private boolean targetInventoryLocked;

    public TradeSession(PlayerRef initiator, PlayerRef target) {
        this(initiator, target, false);
    }

    public TradeSession(PlayerRef initiator, PlayerRef target, boolean testMode) {
        this.sessionId = UUID.randomUUID();
        this.initiator = initiator;
        this.target = target;
        this.testMode = testMode;
        this.initiatorOffer = new TradeOffer();
        this.targetOffer = new TradeOffer();
        this.state = TradeState.PENDING_REQUEST;
        this.createdAt = System.currentTimeMillis();
        this.initiatorAccepted = false;
        this.targetAccepted = false;
        this.initiatorInventoryLocked = false;
        this.targetInventoryLocked = false;
    }

    /**
     * Check if this is a test mode session (solo development).
     */
    public boolean isTestMode() {
        return testMode;
    }

    // ===== GETTERS =====

    public UUID getSessionId() {
        return sessionId;
    }

    public PlayerRef getInitiator() {
        return initiator;
    }

    public PlayerRef getTarget() {
        return target;
    }

    public TradeOffer getInitiatorOffer() {
        return initiatorOffer;
    }

    public TradeOffer getTargetOffer() {
        return targetOffer;
    }

    public TradeState getState() {
        return state;
    }

    public boolean isInitiatorAccepted() {
        return initiatorAccepted;
    }

    public boolean isTargetAccepted() {
        return targetAccepted;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getCountdownStartTime() {
        return countdownStartTime;
    }

    public long getRemainingCountdownMs() {
        if (state != TradeState.BOTH_ACCEPTED_COUNTDOWN) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - countdownStartTime;
        return Math.max(0, COUNTDOWN_DURATION_MS - elapsed);
    }

    // ===== STATE MANAGEMENT =====

    /**
     * Check if a player is part of this trade session.
     * Uses UUID comparison for reliable matching.
     */
    public boolean isParticipant(PlayerRef player) {
        if (player == null) return false;
        UUID playerUuid = player.getUuid();
        return playerUuid.equals(initiator.getUuid()) || playerUuid.equals(target.getUuid());
    }

    /**
     * Check if the player matches the initiator (by UUID).
     */
    private boolean isInitiator(PlayerRef player) {
        return player != null && player.getUuid().equals(initiator.getUuid());
    }

    /**
     * Check if the player matches the target (by UUID).
     */
    private boolean isTarget(PlayerRef player) {
        return player != null && player.getUuid().equals(target.getUuid());
    }

    /**
     * Get the other player in the trade.
     */
    public PlayerRef getOtherPlayer(PlayerRef player) {
        if (isInitiator(player)) {
            return target;
        } else if (isTarget(player)) {
            return initiator;
        }
        return null;
    }

    /**
     * Get the offer for a specific player.
     */
    public TradeOffer getOfferFor(PlayerRef player) {
        if (isInitiator(player)) {
            return initiatorOffer;
        } else if (isTarget(player)) {
            return targetOffer;
        }
        return null;
    }

    /**
     * Check if a player has accepted.
     */
    public boolean hasAccepted(PlayerRef player) {
        if (isInitiator(player)) {
            return initiatorAccepted;
        } else if (isTarget(player)) {
            return targetAccepted;
        }
        return false;
    }

    /**
     * Target accepts the trade request, moving to NEGOTIATING state.
     */
    public boolean acceptRequest() {
        if (state != TradeState.PENDING_REQUEST) {
            return false;
        }
        state = TradeState.NEGOTIATING;
        initiatorInventoryLocked = true;
        targetInventoryLocked = true;
        LOGGER.atInfo().log("Trade session " + sessionId + " moved to NEGOTIATING");
        return true;
    }

    /**
     * Player accepts the current trade offers.
     * In test mode, accepting sets both parties as accepted.
     */
    public boolean accept(PlayerRef player) {
        if (state != TradeState.NEGOTIATING && state != TradeState.ONE_ACCEPTED) {
            return false;
        }

        if (testMode) {
            // In test mode, accepting means both parties accept
            initiatorAccepted = true;
            targetAccepted = true;
            initiatorOffer.lock();
            targetOffer.lock();
            state = TradeState.BOTH_ACCEPTED_COUNTDOWN;
            countdownStartTime = System.currentTimeMillis();
            LOGGER.atInfo().log("Trade session " + sessionId + " [TEST] - both accepted, starting countdown");
            return true;
        }

        if (isInitiator(player)) {
            initiatorAccepted = true;
            initiatorOffer.lock();
        } else if (isTarget(player)) {
            targetAccepted = true;
            targetOffer.lock();
        } else {
            return false;
        }

        // Check if both have accepted
        if (initiatorAccepted && targetAccepted) {
            state = TradeState.BOTH_ACCEPTED_COUNTDOWN;
            countdownStartTime = System.currentTimeMillis();
            LOGGER.atInfo().log("Trade session " + sessionId + " - both accepted, starting countdown");
        } else {
            state = TradeState.ONE_ACCEPTED;
            LOGGER.atInfo().log("Trade session " + sessionId + " - one player accepted");
        }

        return true;
    }

    /**
     * Player revokes their acceptance (only works during countdown or one_accepted).
     */
    public boolean revokeAccept(PlayerRef player) {
        if (state != TradeState.ONE_ACCEPTED && state != TradeState.BOTH_ACCEPTED_COUNTDOWN) {
            LOGGER.atInfo().log("Cannot revoke - wrong state: " + state);
            return false;
        }

        if (isInitiator(player)) {
            initiatorAccepted = false;
            initiatorOffer.unlock();
            LOGGER.atInfo().log("Initiator revoked acceptance");
        } else if (isTarget(player)) {
            targetAccepted = false;
            targetOffer.unlock();
            LOGGER.atInfo().log("Target revoked acceptance");
        } else {
            LOGGER.atInfo().log("Player not recognized as initiator or target");
            return false;
        }

        // Reset to negotiating state
        state = TradeState.NEGOTIATING;
        cancelCountdown();

        LOGGER.atInfo().log("Trade session " + sessionId + " - acceptance revoked, back to NEGOTIATING");
        return true;
    }

    /**
     * Called when offer changes - resets accept states.
     */
    public void onOfferChanged(PlayerRef player) {
        if (state == TradeState.ONE_ACCEPTED || state == TradeState.BOTH_ACCEPTED_COUNTDOWN) {
            // Reset acceptances when offers change
            revokeAllAcceptances();
            LOGGER.atInfo().log("Trade session " + sessionId + " - offer changed, reset to NEGOTIATING");
        }
    }

    /**
     * Revoke all acceptances and return to NEGOTIATING state.
     * Used when either player modifies their offer while accepted.
     */
    public void revokeAllAcceptances() {
        initiatorAccepted = false;
        targetAccepted = false;
        initiatorOffer.unlock();
        targetOffer.unlock();
        state = TradeState.NEGOTIATING;
        cancelCountdown();
        LOGGER.atInfo().log("Trade session " + sessionId + " - all acceptances revoked");
    }

    /**
     * Check if the countdown has completed.
     */
    public boolean isCountdownComplete() {
        if (state != TradeState.BOTH_ACCEPTED_COUNTDOWN) {
            return false;
        }
        return getRemainingCountdownMs() <= 0;
    }

    /**
     * Execute the trade atomically.
     * This is the critical section that must succeed or rollback completely.
     * On verification failures, revokes acceptances and returns to NEGOTIATING.
     */
    public TradeResult execute(Store<EntityStore> store,
                                Ref<EntityStore> initiatorEntityRef,
                                Ref<EntityStore> targetEntityRef) {
        if (state != TradeState.BOTH_ACCEPTED_COUNTDOWN || !isCountdownComplete()) {
            return new TradeResult(false, "Trade not ready for execution");
        }

        state = TradeState.EXECUTING;
        LOGGER.atInfo().log("Trade session " + sessionId + " - executing atomic trade");

        try {
            // Get player components
            Player initiatorPlayer = store.getComponent(initiatorEntityRef, Player.getComponentType());
            Player targetPlayer = store.getComponent(targetEntityRef, Player.getComponentType());

            if (initiatorPlayer == null || targetPlayer == null) {
                revokeAllAcceptances();
                return new TradeResult(false, "Could not access player data");
            }

            Inventory initiatorInventory = initiatorPlayer.getInventory();
            Inventory targetInventory = targetPlayer.getInventory();

            if (initiatorInventory == null || targetInventory == null) {
                revokeAllAcceptances();
                return new TradeResult(false, "Could not access inventories");
            }

            // Get ALL containers for each player (hotbar + backpack + storage)
            List<ItemContainer> initiatorContainers = getAllContainers(initiatorInventory);
            List<ItemContainer> targetContainers = getAllContainers(targetInventory);

            // Get containers in deposit order (storage → hotbar → backpack)
            List<ItemContainer> initiatorDepositContainers = getContainersForDeposit(initiatorInventory);
            List<ItemContainer> targetDepositContainers = getContainersForDeposit(targetInventory);

            // === VERIFICATION PHASE ===
            // Verify initiator has all offered items (check across ALL containers)
            if (!verifyPlayerHasItems(initiatorContainers, initiatorOffer.getItems())) {
                revokeAllAcceptances();
                return TradeResult.initiatorFailure(
                    "You don't have all the offered items anymore",
                    initiator.getUsername() + " doesn't have all offered items"
                );
            }

            // Verify target has all offered items (check across ALL containers)
            if (!verifyPlayerHasItems(targetContainers, targetOffer.getItems())) {
                revokeAllAcceptances();
                return TradeResult.targetFailure(
                    "You don't have all the offered items anymore",
                    target.getUsername() + " doesn't have all offered items"
                );
            }

            // Verify both players have space for received items (smart check with stack merging)
            if (!canReceiveItems(initiatorContainers, targetOffer.getItems())) {
                revokeAllAcceptances();
                return TradeResult.initiatorFailure(
                    "You don't have enough inventory space",
                    initiator.getUsername() + " doesn't have enough inventory space"
                );
            }

            if (!canReceiveItems(targetContainers, initiatorOffer.getItems())) {
                revokeAllAcceptances();
                return TradeResult.targetFailure(
                    "You don't have enough inventory space",
                    target.getUsername() + " doesn't have enough inventory space"
                );
            }

            // === WITHDRAWAL PHASE ===
            // Remove items from initiator (from any container)
            List<ItemStack> initiatorWithdrawn = withdrawItems(initiatorContainers, initiatorOffer.getItems());
            if (initiatorWithdrawn == null) {
                revokeAllAcceptances();
                return TradeResult.initiatorFailure(
                    "Failed to withdraw your items",
                    initiator.getUsername() + " - failed to withdraw items"
                );
            }

            // Remove items from target (from any container)
            List<ItemStack> targetWithdrawn = withdrawItems(targetContainers, targetOffer.getItems());
            if (targetWithdrawn == null) {
                // ROLLBACK: Return items to initiator
                depositItemsSmart(initiatorDepositContainers, initiatorWithdrawn);
                revokeAllAcceptances();
                return TradeResult.targetFailure(
                    "Failed to withdraw your items",
                    target.getUsername() + " - failed to withdraw items"
                );
            }

            // === DEPOSIT PHASE ===
            // Give initiator the target's items (merge with stacks first, then empty slots)
            if (!depositItemsSmart(initiatorDepositContainers, targetWithdrawn)) {
                // ROLLBACK: Return all items
                depositItemsSmart(initiatorDepositContainers, initiatorWithdrawn);
                depositItemsSmart(targetDepositContainers, targetWithdrawn);
                revokeAllAcceptances();
                return TradeResult.initiatorFailure(
                    "Failed to receive items - not enough space",
                    initiator.getUsername() + " couldn't receive items"
                );
            }

            // Give target the initiator's items (merge with stacks first, then empty slots)
            if (!depositItemsSmart(targetDepositContainers, initiatorWithdrawn)) {
                // ROLLBACK: This is tricky, we need to reverse the initiator deposit too
                withdrawItems(initiatorContainers, targetWithdrawn);
                depositItemsSmart(initiatorDepositContainers, initiatorWithdrawn);
                depositItemsSmart(targetDepositContainers, targetWithdrawn);
                revokeAllAcceptances();
                return TradeResult.targetFailure(
                    "Failed to receive items - not enough space",
                    target.getUsername() + " couldn't receive items"
                );
            }

            // === SUCCESS ===
            state = TradeState.COMPLETED;
            LOGGER.atInfo().log("Trade session " + sessionId + " - completed successfully!");

            return new TradeResult(true, "Trade completed successfully");

        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Trade session " + sessionId + " - execution failed with exception");
            revokeAllAcceptances();
            return new TradeResult(false, "Trade failed: " + e.getMessage());
        }
    }

    /**
     * Cancel the trade session.
     */
    public void cancel(PlayerRef cancelledBy) {
        state = TradeState.CANCELLED;
        cancelCountdown();
        unlockInventories();
        LOGGER.atInfo().log("Trade session " + sessionId + " - cancelled by " +
            (cancelledBy != null ? "player" : "system"));
    }

    // ===== HELPER METHODS =====

    /**
     * Get all item containers from an inventory (hotbar + backpack + storage).
     * Used for verification and withdrawal - order doesn't matter.
     */
    private List<ItemContainer> getAllContainers(Inventory inventory) {
        List<ItemContainer> containers = new java.util.ArrayList<>();

        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar != null && hotbar.getCapacity() > 0) {
            containers.add(hotbar);
        }

        ItemContainer backpack = inventory.getBackpack();
        if (backpack != null && backpack.getCapacity() > 0) {
            containers.add(backpack);
        }

        ItemContainer storage = inventory.getStorage();
        if (storage != null && storage.getCapacity() > 0) {
            containers.add(storage);
        }

        return containers;
    }

    /**
     * Get containers in preferred deposit order: storage → hotbar → backpack.
     * Items will be deposited to storage first, then hotbar, then backpack.
     */
    private List<ItemContainer> getContainersForDeposit(Inventory inventory) {
        List<ItemContainer> containers = new java.util.ArrayList<>();

        // 1. Storage first (main inventory)
        ItemContainer storage = inventory.getStorage();
        if (storage != null && storage.getCapacity() > 0) {
            containers.add(storage);
        }

        // 2. Hotbar second
        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar != null && hotbar.getCapacity() > 0) {
            containers.add(hotbar);
        }

        // 3. Backpack last
        ItemContainer backpack = inventory.getBackpack();
        if (backpack != null && backpack.getCapacity() > 0) {
            containers.add(backpack);
        }

        return containers;
    }

    private void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel(false);
            countdownTask = null;
        }
        countdownStartTime = 0;
    }

    public void setCountdownTask(ScheduledFuture<?> task) {
        this.countdownTask = task;
    }

    private void unlockInventories() {
        initiatorInventoryLocked = false;
        targetInventoryLocked = false;
    }

    public boolean isInventoryLocked(PlayerRef player) {
        if (isInitiator(player)) {
            return initiatorInventoryLocked;
        } else if (isTarget(player)) {
            return targetInventoryLocked;
        }
        return false;
    }

    /**
     * Verify a player has all the specified items in their inventory.
     * Handles consolidated offers against split inventory stacks.
     * Scans ALL containers (hotbar, backpack, storage).
     */
    private boolean verifyPlayerHasItems(List<ItemContainer> containers, List<ItemStack> items) {
        // Build a map of available quantities by item ID across ALL containers
        java.util.Map<String, Integer> availableQuantities = new java.util.HashMap<>();

        for (ItemContainer container : containers) {
            short capacity = container.getCapacity();
            for (short i = 0; i < capacity; i++) {
                ItemStack item = container.getItemStack(i);
                if (item != null && !item.isEmpty()) {
                    String itemId = item.getItem().getId();
                    int qty = item.getQuantity();
                    availableQuantities.merge(itemId, qty, Integer::sum);
                }
            }
        }

        // Check if we have enough of each required item
        for (ItemStack required : items) {
            if (required == null || required.isEmpty()) {
                continue;
            }

            var reqItem = required.getItem();
            if (reqItem == null) {
                LOGGER.atWarning().log("Required item has null Item object!");
                return false;
            }

            String itemId = reqItem.getId();
            int requiredQty = required.getQuantity();
            int availableQty = availableQuantities.getOrDefault(itemId, 0);

            if (availableQty < requiredQty) {
                LOGGER.atWarning().log("Verification failed: need " + requiredQty + " of " + itemId +
                    " but only have " + availableQty);
                return false;
            }

            // Reduce available quantity for subsequent checks of same item type
            availableQuantities.put(itemId, availableQty - requiredQty);
        }
        return true;
    }

    /**
     * Check if player has enough empty slots across all containers.
     */
    private boolean hasInventorySpace(List<ItemContainer> containers, int requiredSlots) {
        if (requiredSlots <= 0) {
            return true;
        }

        int emptySlots = 0;
        for (ItemContainer container : containers) {
            short capacity = container.getCapacity();
            for (short i = 0; i < capacity; i++) {
                ItemStack item = container.getItemStack(i);
                if (item == null || item.isEmpty()) {
                    emptySlots++;
                    if (emptySlots >= requiredSlots) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Withdraw items from inventory across all containers.
     * Returns the withdrawn items, or null if failed.
     * Handles withdrawing from multiple stacks across multiple containers if needed.
     */
    private List<ItemStack> withdrawItems(List<ItemContainer> containers, List<ItemStack> items) {
        java.util.List<ItemStack> withdrawn = new java.util.ArrayList<>();

        for (ItemStack toWithdraw : items) {
            if (toWithdraw == null || toWithdraw.isEmpty()) continue;

            String itemId = toWithdraw.getItem().getId();
            int remainingToWithdraw = toWithdraw.getQuantity();

            // Withdraw from multiple containers/stacks if needed
            for (ItemContainer container : containers) {
                if (remainingToWithdraw <= 0) break;

                short capacity = container.getCapacity();
                for (short i = 0; i < capacity && remainingToWithdraw > 0; i++) {
                    ItemStack current = container.getItemStack(i);
                    if (current != null && !current.isEmpty() && current.getItem().getId().equals(itemId)) {
                        int currentQty = current.getQuantity();

                        if (currentQty <= remainingToWithdraw) {
                            // Take entire stack
                            container.removeItemStackFromSlot(i);
                            remainingToWithdraw -= currentQty;
                        } else {
                            // Take partial stack - reduce quantity
                            int newQty = currentQty - remainingToWithdraw;
                            ItemStack reduced = new ItemStack(itemId, newQty);
                            container.setItemStackForSlot(i, reduced);
                            remainingToWithdraw = 0;
                        }
                    }
                }
            }

            if (remainingToWithdraw > 0) {
                // Failed to withdraw enough - rollback
                LOGGER.atWarning().log("Failed to withdraw " + toWithdraw.getQuantity() + " of " + itemId +
                    ", still need " + remainingToWithdraw);
                depositItems(containers, withdrawn);
                return null;
            }

            // Record what we withdrew (consolidated)
            withdrawn.add(InventoryHelper.copyItemStack(toWithdraw));
        }

        return withdrawn;
    }

    /**
     * Deposit items into inventory across all containers.
     * Tries to find empty slots in any container.
     * @deprecated Use depositItemsSmart instead for stack merging support
     */
    @Deprecated
    private boolean depositItems(List<ItemContainer> containers, List<ItemStack> items) {
        for (ItemStack toDeposit : items) {
            boolean deposited = false;
            for (ItemContainer container : containers) {
                if (deposited) break;

                short capacity = container.getCapacity();
                for (short i = 0; i < capacity && !deposited; i++) {
                    ItemStack current = container.getItemStack(i);
                    if (current == null || current.isEmpty()) {
                        container.setItemStackForSlot(i, InventoryHelper.copyItemStack(toDeposit));
                        deposited = true;
                    }
                }
            }
            if (!deposited) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if player can receive the given items, accounting for stack merging.
     * First counts space in existing non-full stacks, then counts empty slots.
     */
    private boolean canReceiveItems(List<ItemContainer> containers, List<ItemStack> items) {
        // Build a map of: itemId -> (currentTotal, maxStack, availableStackSpace)
        java.util.Map<String, int[]> stackInfo = new java.util.HashMap<>();
        int emptySlots = 0;

        // Scan all containers to find existing stacks and empty slots
        for (ItemContainer container : containers) {
            short capacity = container.getCapacity();
            for (short i = 0; i < capacity; i++) {
                ItemStack current = container.getItemStack(i);
                if (current == null || current.isEmpty()) {
                    emptySlots++;
                } else {
                    String itemId = current.getItem().getId();
                    int qty = current.getQuantity();
                    int rawMaxStack = current.getItem().getMaxStack();
                    final int maxStack = rawMaxStack > 0 ? rawMaxStack : 100; // Default max stack

                    int[] info = stackInfo.computeIfAbsent(itemId, k -> new int[]{0, maxStack, 0});
                    info[0] += qty; // current total
                    info[2] += (maxStack - qty); // available space in this stack
                }
            }
        }

        // Check if we can fit all the items we need to receive
        int slotsNeeded = 0;
        for (ItemStack toReceive : items) {
            if (toReceive == null || toReceive.isEmpty()) continue;

            String itemId = toReceive.getItem().getId();
            int qtyToReceive = toReceive.getQuantity();
            int rawMax = toReceive.getItem().getMaxStack();
            int maxStack = rawMax > 0 ? rawMax : 100;

            // Check if we have existing stacks to merge with
            int[] info = stackInfo.get(itemId);
            if (info != null && info[2] > 0) {
                // Use available stack space first
                int canMerge = Math.min(qtyToReceive, info[2]);
                qtyToReceive -= canMerge;
                info[2] -= canMerge;
            }

            // Remaining needs new slots
            if (qtyToReceive > 0) {
                slotsNeeded += (int) Math.ceil((double) qtyToReceive / maxStack);
            }
        }

        return slotsNeeded <= emptySlots;
    }

    /**
     * Smart deposit: first merge with existing non-full stacks, then use empty slots.
     * Containers are checked in order (storage → hotbar → backpack for deposits).
     */
    private boolean depositItemsSmart(List<ItemContainer> containers, List<ItemStack> items) {
        for (ItemStack toDeposit : items) {
            if (toDeposit == null || toDeposit.isEmpty()) continue;

            String itemId = toDeposit.getItem().getId();
            int remaining = toDeposit.getQuantity();
            int rawMax = toDeposit.getItem().getMaxStack();
            int maxStack = rawMax > 0 ? rawMax : 100;

            // PHASE 1: Try to merge with existing stacks (check ALL containers)
            for (ItemContainer container : containers) {
                if (remaining <= 0) break;

                short capacity = container.getCapacity();
                for (short i = 0; i < capacity && remaining > 0; i++) {
                    ItemStack current = container.getItemStack(i);
                    if (current != null && !current.isEmpty() && current.getItem().getId().equals(itemId)) {
                        int currentQty = current.getQuantity();
                        int canAdd = maxStack - currentQty;

                        if (canAdd > 0) {
                            int toAdd = Math.min(remaining, canAdd);
                            ItemStack updated = new ItemStack(itemId, currentQty + toAdd);
                            container.setItemStackForSlot(i, updated);
                            remaining -= toAdd;
                        }
                    }
                }
            }

            // PHASE 2: Use empty slots for remaining (in preferred order)
            for (ItemContainer container : containers) {
                if (remaining <= 0) break;

                short capacity = container.getCapacity();
                for (short i = 0; i < capacity && remaining > 0; i++) {
                    ItemStack current = container.getItemStack(i);
                    if (current == null || current.isEmpty()) {
                        int toPlace = Math.min(remaining, maxStack);
                        ItemStack newStack = new ItemStack(itemId, toPlace);
                        container.setItemStackForSlot(i, newStack);
                        remaining -= toPlace;
                    }
                }
            }

            // If we couldn't deposit everything, fail
            if (remaining > 0) {
                LOGGER.atWarning().log("Failed to deposit " + toDeposit.getQuantity() + " of " + itemId +
                    ", remaining: " + remaining);
                return false;
            }
        }
        return true;
    }

    /**
     * Check if two items are the same type (ignoring quantity).
     */
    private boolean itemsMatch(ItemStack a, ItemStack b) {
        if (a == null || b == null) {
            return false;
        }
        return a.getItem().equals(b.getItem());
    }

    /**
     * Send message to both participants.
     */
    public void broadcastMessage(String message) {
        initiator.sendMessage(Message.raw(message));
        target.sendMessage(Message.raw(message));
    }

    /**
     * Result of a trade execution attempt.
     */
    public static class TradeResult {
        public final boolean success;
        public final String message;
        public final String messageForOther; // Message for the other player
        public final FailureCause cause;

        public enum FailureCause {
            NONE,           // No failure (success)
            INITIATOR,      // Initiator caused the issue
            TARGET,         // Target caused the issue
            SYSTEM          // System error (both see same message)
        }

        public TradeResult(boolean success, String message) {
            this(success, message, null, FailureCause.SYSTEM);
        }

        public TradeResult(boolean success, String message, String messageForOther, FailureCause cause) {
            this.success = success;
            this.message = message;
            this.messageForOther = messageForOther;
            this.cause = cause;
        }

        /**
         * Create a failure result where the initiator caused the issue.
         */
        public static TradeResult initiatorFailure(String initiatorMsg, String targetMsg) {
            return new TradeResult(false, initiatorMsg, targetMsg, FailureCause.INITIATOR);
        }

        /**
         * Create a failure result where the target caused the issue.
         */
        public static TradeResult targetFailure(String targetMsg, String initiatorMsg) {
            return new TradeResult(false, targetMsg, initiatorMsg, FailureCause.TARGET);
        }
    }
}
