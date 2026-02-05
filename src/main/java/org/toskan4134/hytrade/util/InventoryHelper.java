package org.toskan4134.hytrade.util;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for inventory operations.
 * Provides atomic-safe methods for item manipulation.
 */
public final class InventoryHelper {

    private InventoryHelper() {
        // Utility class - no instantiation
    }

    /**
     * Create a copy of an ItemStack.
     * Since ItemStack.clone() is not public, we create a new instance with the same properties.
     * @param item The item to copy
     * @return A new ItemStack with the same item type and quantity, or null if input is null
     */
    public static ItemStack copyItemStack(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        // Create a new ItemStack with the same item ID and quantity
        // The ItemStack constructor takes (String itemId, int quantity)
        // Use getId() to get the proper item identifier
        String itemId = item.getItem().getId();
        return new ItemStack(itemId, item.getQuantity());
    }

    /**
     * Count empty slots in a container.
     * @param container The container to check
     * @return Number of empty slots
     */
    public static int countEmptySlots(ItemContainer container) {
        if (container == null) {
            return 0;
        }

        int empty = 0;
        short capacity = container.getCapacity();
        for (short i = 0; i < capacity; i++) {
            ItemStack item = container.getItemStack(i);
            if (item == null || item.isEmpty()) {
                empty++;
            }
        }
        return empty;
    }

    /**
     * Get all non-empty items from a container.
     * @param container The container to read from
     * @return List of items (copies)
     */
    public static List<ItemStack> getAllItems(ItemContainer container) {
        List<ItemStack> items = new ArrayList<>();
        if (container == null) {
            return items;
        }

        short capacity = container.getCapacity();
        for (short i = 0; i < capacity; i++) {
            ItemStack item = container.getItemStack(i);
            if (item != null && !item.isEmpty()) {
                items.add(copyItemStack(item));
            }
        }
        return items;
    }

    /**
     * Find the first empty slot in a container.
     * @param container The container to search
     * @return Slot index, or -1 if no empty slot
     */
    public static short findEmptySlot(ItemContainer container) {
        if (container == null) {
            return -1;
        }

        short capacity = container.getCapacity();
        for (short i = 0; i < capacity; i++) {
            ItemStack item = container.getItemStack(i);
            if (item == null || item.isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Find a slot containing a specific item type.
     * @param container The container to search
     * @param itemStack The item to find (matches by type)
     * @return Slot index, or -1 if not found
     */
    public static short findItem(ItemContainer container, ItemStack itemStack) {
        if (container == null || itemStack == null) {
            return -1;
        }

        short capacity = container.getCapacity();
        for (short i = 0; i < capacity; i++) {
            ItemStack current = container.getItemStack(i);
            if (current != null && !current.isEmpty()) {
                if (current.getItem().equals(itemStack.getItem())) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Check if container has at least the specified items.
     * @param container The container to check
     * @param items Items required
     * @return true if all items are present
     */
    public static boolean hasItems(ItemContainer container, List<ItemStack> items) {
        if (container == null || items == null || items.isEmpty()) {
            return items == null || items.isEmpty();
        }

        // Create a working copy to track what we've "found"
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack item : items) {
            remaining.add(copyItemStack(item));
        }

        short capacity = container.getCapacity();
        for (short i = 0; i < capacity && !remaining.isEmpty(); i++) {
            ItemStack current = container.getItemStack(i);
            if (current == null || current.isEmpty()) {
                continue;
            }

            // Check against remaining requirements
            for (int j = remaining.size() - 1; j >= 0; j--) {
                ItemStack required = remaining.get(j);
                if (current.getItem().equals(required.getItem()) &&
                    current.getQuantity() >= required.getQuantity()) {
                    remaining.remove(j);
                    break;
                }
            }
        }

        return remaining.isEmpty();
    }

    /**
     * Safely move an item from one slot to another within the same container.
     * @param container The container
     * @param fromSlot Source slot
     * @param toSlot Destination slot
     * @return true if successful
     */
    public static boolean moveItem(ItemContainer container, short fromSlot, short toSlot) {
        if (container == null) {
            return false;
        }

        short capacity = container.getCapacity();
        if (fromSlot < 0 || fromSlot >= capacity || toSlot < 0 || toSlot >= capacity) {
            return false;
        }

        ItemStack source = container.getItemStack(fromSlot);
        ItemStack dest = container.getItemStack(toSlot);

        if (source == null || source.isEmpty()) {
            return false;
        }

        if (dest != null && !dest.isEmpty()) {
            // Destination not empty - could implement swap here
            return false;
        }

        // Move the item
        container.removeItemStackFromSlot(fromSlot);
        container.setItemStackForSlot(toSlot, source);
        return true;
    }

    /**
     * Create a snapshot of the container state for potential rollback.
     * @param container The container to snapshot
     * @return Array of item copies
     */
    public static ItemStack[] createSnapshot(ItemContainer container) {
        if (container == null) {
            return new ItemStack[0];
        }

        short capacity = container.getCapacity();
        ItemStack[] snapshot = new ItemStack[capacity];
        for (short i = 0; i < capacity; i++) {
            ItemStack item = container.getItemStack(i);
            snapshot[i] = copyItemStack(item);
        }
        return snapshot;
    }

    /**
     * Restore a container from a snapshot.
     * @param container The container to restore
     * @param snapshot The snapshot to restore from
     */
    public static void restoreFromSnapshot(ItemContainer container, ItemStack[] snapshot) {
        if (container == null || snapshot == null) {
            return;
        }

        short capacity = container.getCapacity();
        for (short i = 0; i < Math.min(capacity, snapshot.length); i++) {
            if (snapshot[i] != null) {
                container.setItemStackForSlot(i, copyItemStack(snapshot[i]));
            } else {
                container.removeItemStackFromSlot(i);
            }
        }
    }
}
