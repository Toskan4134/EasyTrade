package org.toskan4134.hytrade.trade;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.toskan4134.hytrade.util.InventoryHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the items a player is offering in a trade.
 * Immutable once finalized to prevent modification during execution.
 */
public class TradeOffer {

    private final List<ItemStack> items;
    private boolean locked;

    public TradeOffer() {
        this.items = new ArrayList<>();
        this.locked = false;
    }

    /**
     * Add an item to the offer.
     * @param item The item to add
     * @return true if added successfully, false if locked or invalid item
     */
    public boolean addItem(ItemStack item) {
        if (locked || item == null || item.isEmpty()) {
            return false;
        }
        items.add(InventoryHelper.copyItemStack(item));
        return true;
    }

    /**
     * Remove an item from the offer by index.
     * @param index The slot index to remove
     * @return The removed item, or null if invalid index or locked
     */
    public ItemStack removeItem(int index) {
        if (locked || index < 0 || index >= items.size()) {
            return null;
        }
        return items.remove(index);
    }

    /**
     * Reduce the quantity of an item at a specific index.
     * If the quantity becomes 0 or less, the item is removed.
     * @param index The slot index
     * @param amount The amount to reduce
     * @return true if successful, false if invalid index, locked, or insufficient quantity
     */
    public boolean reduceItemQuantity(int index, int amount) {
        if (locked || index < 0 || index >= items.size() || amount <= 0) {
            return false;
        }

        ItemStack item = items.get(index);
        if (item == null || item.isEmpty()) {
            return false;
        }

        int currentQuantity = item.getQuantity();
        if (amount >= currentQuantity) {
            // Remove the item entirely
            items.remove(index);
        } else {
            // Create new ItemStack with reduced quantity
            String itemId = item.getItem().getId();
            items.set(index, new ItemStack(itemId, currentQuantity - amount));
        }
        return true;
    }

    /**
     * Set the quantity of an item at a specific index.
     * @param index The slot index
     * @param newQuantity The new quantity
     * @return true if successful, false if invalid index or locked
     */
    public boolean setItemQuantity(int index, int newQuantity) {
        if (locked || index < 0 || index >= items.size() || newQuantity <= 0) {
            return false;
        }

        ItemStack item = items.get(index);
        if (item == null || item.isEmpty()) {
            return false;
        }

        String itemId = item.getItem().getId();
        items.set(index, new ItemStack(itemId, newQuantity));
        return true;
    }

    /**
     * Get an item at a specific index.
     * @param index The slot index
     * @return The item at the index, or null if invalid
     */
    public ItemStack getItem(int index) {
        if (index < 0 || index >= items.size()) {
            return null;
        }
        return items.get(index);
    }

    /**
     * Get all items in the offer.
     * @return Unmodifiable list of items
     */
    public List<ItemStack> getItems() {
        return Collections.unmodifiableList(items);
    }

    /**
     * Get the number of items in the offer.
     * @return Item count
     */
    public int getItemCount() {
        return items.size();
    }

    /**
     * Check if the offer is empty.
     * @return true if no items in offer
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Lock the offer to prevent modifications.
     * Called when player accepts the trade.
     */
    public void lock() {
        this.locked = true;
    }

    /**
     * Unlock the offer to allow modifications.
     * Called when offer changes or trade state resets.
     */
    public void unlock() {
        this.locked = false;
    }

    /**
     * Check if the offer is locked.
     * @return true if locked
     */
    public boolean isLocked() {
        return locked;
    }

    /**
     * Clear all items from the offer.
     */
    public void clear() {
        if (!locked) {
            items.clear();
        }
    }

    /**
     * Create a deep copy of this offer.
     * @return A new TradeOffer with copied items
     */
    public TradeOffer copy() {
        TradeOffer copy = new TradeOffer();
        for (ItemStack item : items) {
            copy.items.add(InventoryHelper.copyItemStack(item));
        }
        return copy;
    }
}
