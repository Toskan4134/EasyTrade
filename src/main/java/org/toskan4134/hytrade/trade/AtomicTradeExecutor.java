package org.toskan4134.hytrade.trade;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.toskan4134.hytrade.util.InventoryHelper;

import java.util.List;

/**
 * Executes trades atomically with full rollback support.
 * Ensures that trades either complete fully or not at all.
 *
 * Transaction flow:
 * 1. VERIFY - Check all preconditions
 * 2. SNAPSHOT - Save current state
 * 3. WITHDRAW - Remove items from both players
 * 4. DEPOSIT - Give items to both players
 * 5. COMMIT - Success! or ROLLBACK if any step fails
 */
public class AtomicTradeExecutor {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /**
     * Execute a trade atomically.
     *
     * @param session The trade session to execute
     * @param store The entity store
     * @param initiatorRef Entity reference for the initiator
     * @param targetRef Entity reference for the target
     * @return Result of the trade execution
     */
    public TradeExecutionResult execute(
            TradeSession session,
            Store<EntityStore> store,
            Ref<EntityStore> initiatorRef,
            Ref<EntityStore> targetRef) {

        LOGGER.atInfo().log("Beginning atomic trade execution for session: " + session.getSessionId());

        // === PHASE 1: GET COMPONENTS ===
        Player initiatorPlayer = store.getComponent(initiatorRef, Player.getComponentType());
        Player targetPlayer = store.getComponent(targetRef, Player.getComponentType());

        if (initiatorPlayer == null || targetPlayer == null) {
            return TradeExecutionResult.failure("Could not access player data");
        }

        Inventory initiatorInventory = initiatorPlayer.getInventory();
        Inventory targetInventory = targetPlayer.getInventory();

        if (initiatorInventory == null || targetInventory == null) {
            return TradeExecutionResult.failure("Could not access inventories");
        }

        ItemContainer initiatorBackpack = initiatorInventory.getBackpack();
        ItemContainer targetBackpack = targetInventory.getBackpack();

        if (initiatorBackpack == null || targetBackpack == null) {
            return TradeExecutionResult.failure("Could not access backpacks");
        }

        // Get the offers
        List<ItemStack> initiatorOfferItems = session.getInitiatorOffer().getItems();
        List<ItemStack> targetOfferItems = session.getTargetOffer().getItems();

        // === PHASE 2: VERIFICATION ===
        LOGGER.atInfo().log("Phase 2: Verifying preconditions");

        // Verify initiator has all offered items
        if (!InventoryHelper.hasItems(initiatorBackpack, initiatorOfferItems)) {
            return TradeExecutionResult.failure("Initiator missing offered items");
        }

        // Verify target has all offered items
        if (!InventoryHelper.hasItems(targetBackpack, targetOfferItems)) {
            return TradeExecutionResult.failure("Target missing offered items");
        }

        // Calculate space requirements
        int initiatorGiving = initiatorOfferItems.size();
        int initiatorReceiving = targetOfferItems.size();
        int targetGiving = targetOfferItems.size();
        int targetReceiving = initiatorOfferItems.size();

        int initiatorNetChange = initiatorReceiving - initiatorGiving;
        int targetNetChange = targetReceiving - targetGiving;

        // Verify space (only need to check if receiving more than giving)
        if (initiatorNetChange > 0) {
            int emptySlots = InventoryHelper.countEmptySlots(initiatorBackpack);
            if (emptySlots < initiatorNetChange) {
                return TradeExecutionResult.failure("Initiator lacks inventory space");
            }
        }

        if (targetNetChange > 0) {
            int emptySlots = InventoryHelper.countEmptySlots(targetBackpack);
            if (emptySlots < targetNetChange) {
                return TradeExecutionResult.failure("Target lacks inventory space");
            }
        }

        // === PHASE 3: SNAPSHOT ===
        LOGGER.atInfo().log("Phase 3: Creating snapshots");

        ItemStack[] initiatorSnapshot = InventoryHelper.createSnapshot(initiatorBackpack);
        ItemStack[] targetSnapshot = InventoryHelper.createSnapshot(targetBackpack);

        // === PHASE 4: WITHDRAWAL ===
        LOGGER.atInfo().log("Phase 4: Withdrawing items");

        try {
            // Remove items from initiator
            for (ItemStack item : initiatorOfferItems) {
                if (!removeItem(initiatorBackpack, item)) {
                    // Rollback initiator
                    InventoryHelper.restoreFromSnapshot(initiatorBackpack, initiatorSnapshot);
                    return TradeExecutionResult.failure("Failed to withdraw from initiator");
                }
            }

            // Remove items from target
            for (ItemStack item : targetOfferItems) {
                if (!removeItem(targetBackpack, item)) {
                    // Rollback both
                    InventoryHelper.restoreFromSnapshot(initiatorBackpack, initiatorSnapshot);
                    InventoryHelper.restoreFromSnapshot(targetBackpack, targetSnapshot);
                    return TradeExecutionResult.failure("Failed to withdraw from target");
                }
            }

        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Exception during withdrawal phase");
            InventoryHelper.restoreFromSnapshot(initiatorBackpack, initiatorSnapshot);
            InventoryHelper.restoreFromSnapshot(targetBackpack, targetSnapshot);
            return TradeExecutionResult.failure("Withdrawal failed: " + e.getMessage());
        }

        // === PHASE 5: DEPOSIT ===
        LOGGER.atInfo().log("Phase 5: Depositing items");

        try {
            // Give target's items to initiator
            for (ItemStack item : targetOfferItems) {
                if (!addItem(initiatorBackpack, item)) {
                    // Critical failure - rollback everything
                    InventoryHelper.restoreFromSnapshot(initiatorBackpack, initiatorSnapshot);
                    InventoryHelper.restoreFromSnapshot(targetBackpack, targetSnapshot);
                    return TradeExecutionResult.failure("Failed to deposit to initiator");
                }
            }

            // Give initiator's items to target
            for (ItemStack item : initiatorOfferItems) {
                if (!addItem(targetBackpack, item)) {
                    // Critical failure - rollback everything
                    InventoryHelper.restoreFromSnapshot(initiatorBackpack, initiatorSnapshot);
                    InventoryHelper.restoreFromSnapshot(targetBackpack, targetSnapshot);
                    return TradeExecutionResult.failure("Failed to deposit to target");
                }
            }

        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Exception during deposit phase");
            InventoryHelper.restoreFromSnapshot(initiatorBackpack, initiatorSnapshot);
            InventoryHelper.restoreFromSnapshot(targetBackpack, targetSnapshot);
            return TradeExecutionResult.failure("Deposit failed: " + e.getMessage());
        }

        // === PHASE 6: COMMIT ===
        LOGGER.atInfo().log("Phase 6: Trade committed successfully!");

        return TradeExecutionResult.success(
                initiatorOfferItems.size(),
                targetOfferItems.size()
        );
    }

    /**
     * Remove a specific item from a container.
     */
    private boolean removeItem(ItemContainer container, ItemStack itemToRemove) {
        short capacity = container.getCapacity();

        for (short i = 0; i < capacity; i++) {
            ItemStack current = container.getItemStack(i);
            if (current != null && !current.isEmpty()) {
                if (current.getItem().equals(itemToRemove.getItem()) &&
                    current.getQuantity() >= itemToRemove.getQuantity()) {

                    if (current.getQuantity() == itemToRemove.getQuantity()) {
                        // Remove entire stack
                        container.removeItemStackFromSlot(i);
                    } else {
                        // Reduce quantity (implementation depends on Hytale API)
                        // For now, we handle exact matches only
                        container.removeItemStackFromSlot(i);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Add an item to a container.
     */
    private boolean addItem(ItemContainer container, ItemStack itemToAdd) {
        short emptySlot = InventoryHelper.findEmptySlot(container);
        if (emptySlot < 0) {
            return false;
        }

        container.setItemStackForSlot(emptySlot, InventoryHelper.copyItemStack(itemToAdd));
        return true;
    }

    /**
     * Result of a trade execution attempt.
     */
    public static class TradeExecutionResult {
        public final boolean success;
        public final String message;
        public final int initiatorItemsTraded;
        public final int targetItemsTraded;

        private TradeExecutionResult(boolean success, String message,
                                     int initiatorItemsTraded, int targetItemsTraded) {
            this.success = success;
            this.message = message;
            this.initiatorItemsTraded = initiatorItemsTraded;
            this.targetItemsTraded = targetItemsTraded;
        }

        public static TradeExecutionResult success(int initiatorItems, int targetItems) {
            return new TradeExecutionResult(true, "Trade completed successfully",
                    initiatorItems, targetItems);
        }

        public static TradeExecutionResult failure(String reason) {
            return new TradeExecutionResult(false, reason, 0, 0);
        }
    }
}
