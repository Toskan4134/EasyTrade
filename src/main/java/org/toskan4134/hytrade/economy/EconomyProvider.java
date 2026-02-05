package org.toskan4134.hytrade.economy;

import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * Interface for economy provider integration.
 * Currently disabled - prepared for future mod compatibility.
 *
 * When economy mods become available, implement this interface
 * to allow money trading alongside item trading.
 */
public interface EconomyProvider {

    /**
     * Check if this economy provider is available and functional.
     * @return true if the economy system is available
     */
    boolean isAvailable();

    /**
     * Get the name of the currency (e.g., "Coins", "Gold", "Credits").
     * @return Currency display name
     */
    String getCurrencyName();

    /**
     * Get the currency symbol (e.g., "$", "G", "C").
     * @return Currency symbol
     */
    String getCurrencySymbol();

    /**
     * Get a player's current balance.
     * @param player The player to check
     * @return The player's balance, or 0 if unavailable
     */
    double getBalance(PlayerRef player);

    /**
     * Check if a player has at least the specified amount.
     * @param player The player to check
     * @param amount The amount to check for
     * @return true if player has sufficient funds
     */
    boolean hasBalance(PlayerRef player, double amount);

    /**
     * Withdraw money from a player's account.
     * @param player The player to withdraw from
     * @param amount The amount to withdraw
     * @return true if successful, false if insufficient funds or error
     */
    boolean withdraw(PlayerRef player, double amount);

    /**
     * Deposit money to a player's account.
     * @param player The player to deposit to
     * @param amount The amount to deposit
     */
    void deposit(PlayerRef player, double amount);

    /**
     * Format an amount for display (e.g., "100 Coins" or "$100.00").
     * @param amount The amount to format
     * @return Formatted string
     */
    String formatAmount(double amount);
}
