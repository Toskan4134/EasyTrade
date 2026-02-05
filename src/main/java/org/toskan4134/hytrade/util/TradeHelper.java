package org.toskan4134.hytrade.util;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.toskan4134.hytrade.translation.TranslationManager;

import java.util.function.Consumer;

/**
 * Common utility methods for the HyTrade plugin.
 * Contains shared helper functions used across the codebase.
 */
public final class TradeHelper {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private TradeHelper() {
        // Utility class - prevent instantiation
    }

    // ===== Message Sending Helpers =====

    /**
     * Send a translated message to a player.
     * @param player The player to send the message to
     * @param key The translation key
     * @param placeholders Optional placeholder name-value pairs (alternating key, value)
     */
    public static void sendMessage(PlayerRef player, String key, String... placeholders) {
        String message = TranslationManager.getInstance().get(key, placeholders);
        player.sendMessage(Message.raw(message));
    }

    /**
     * Send a raw message to a player.
     * @param player The player to send the message to
     * @param message The message to send
     */
    public static void sendRawMessage(PlayerRef player, String message) {
        player.sendMessage(Message.raw(message));
    }

    /**
     * Send a message to a player with logging.
     * @param player The player to send the message to
     * @param key The translation key
     * @param placeholders Optional placeholder name-value pairs (alternating key, value)
     */
    public static void sendMessageWithLog(PlayerRef player, String key, String... placeholders) {
        String message = TranslationManager.getInstance().get(key, placeholders);
        LOGGER.atInfo().log("Sending message to " + player.getUsername() + ": " + message);
        player.sendMessage(Message.raw(message));
    }

    // ===== Container Iteration Helpers =====

    /**
     * Iterate through all items in a container, performing an action on each non-empty item stack.
     * @param container The container to iterate through
     * @param callback The action to perform on each item stack
     */
    public static void forEachItem(ItemContainer container, Consumer<ItemStack> callback) {
        if (container == null || container.getCapacity() <= 0) {
            return;
        }

        for (int i = 0; i < container.getCapacity(); i++) {
            ItemStack item = container.getItemStack((short) i);
            if (item != null && !item.isEmpty()) {
                callback.accept(item);
            }
        }
    }

    /**
     * Count the total number of empty slots in a container.
     * @param container The container to count
     * @return The number of empty slots
     */
    public static int countEmptySlots(ItemContainer container) {
        if (container == null || container.getCapacity() <= 0) {
            return 0;
        }

        int emptySlots = 0;
        for (int i = 0; i < container.getCapacity(); i++) {
            ItemStack item = container.getItemStack((short) i);
            if (item == null || item.isEmpty()) {
                emptySlots++;
            }
        }
        return emptySlots;
    }

    /**
     * Check if a container has at least the specified number of empty slots.
     * @param container The container to check
     * @param requiredSlots The minimum number of empty slots required
     * @return true if the container has enough empty slots
     */
    public static boolean hasEmptySlots(ItemContainer container, int requiredSlots) {
        return requiredSlots <= 0 || countEmptySlots(container) >= requiredSlots;
    }

    // ===== String Helpers =====

    /**
     * Sanitize a string for use in UI event binding by replacing special characters.
     * Replaces "_" with "_US_" and "-" with "_DA_" to avoid parsing issues.
     * @param input The input string
     * @return The sanitized string
     */
    public static String sanitizeForUI(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("_", "_US_").replace("-", "_DA_");
    }

    /**
     * Restore a sanitized string back to its original form.
     * @param input The sanitized string
     * @return The original string
     */
    public static String restoreFromUI(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("_US_", "_").replace("_DA_", "-");
    }

    // ===== Validation Helpers =====

    /**
     * Check if a player reference is valid (not null and has a valid UUID).
     * @param player The player reference to check
     * @return true if the player reference is valid
     */
    public static boolean isValidPlayer(PlayerRef player) {
        return player != null && player.getUuid() != null;
    }

    /**
     * Check if an item stack is valid (not null and not empty).
     * @param itemStack The item stack to check
     * @return true if the item stack is valid
     */
    public static boolean isValidItem(ItemStack itemStack) {
        return itemStack != null && !itemStack.isEmpty();
    }

    // ===== Time Formatting =====

    /**
     * Format milliseconds as seconds for display.
     * @param millis The time in milliseconds
     * @return The formatted string (e.g., "3s")
     */
    public static String formatSeconds(long millis) {
        long seconds = (millis + 999) / 1000; // Round up
        return seconds + "s";
    }

    /**
     * Format milliseconds as a countdown display.
     * @param millis The time in milliseconds
     * @return The formatted string (e.g., "3 more seconds")
     */
    public static String formatCountdown(long millis) {
        long seconds = (millis + 999) / 1000; // Round up
        return String.valueOf(seconds);
    }
}
