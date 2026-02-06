package org.toskan4134.hytrade.ui;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;

/**
 * Represents a consolidated item (multiple stacks merged into one entry).
 * Used by the trading UI to display inventory items in a consolidated format.
 */
public class ConsolidatedItem {
    String itemId;
    Item item; // Store the actual Item object for creating valid ItemStacks
    int totalQuantity;
    int offeredQuantity;
    int maxStackSize;

    public ConsolidatedItem(String itemId, Item item, int maxStackSize) {
        this.itemId = itemId;
        this.item = item;
        this.totalQuantity = 0;
        this.offeredQuantity = 0;
        this.maxStackSize = maxStackSize;
    }

    /**
     * Get the number of items available (not yet offered).
     */
    public int getAvailable() {
        return totalQuantity - offeredQuantity;
    }

    public String getItemId() {
        return itemId;
    }

    public Item getItem() {
        return item;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(int totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public int getOfferedQuantity() {
        return offeredQuantity;
    }

    public void setOfferedQuantity(int offeredQuantity) {
        this.offeredQuantity = offeredQuantity;
    }

    public int getMaxStackSize() {
        return maxStackSize;
    }
}
