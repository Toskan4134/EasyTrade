package org.toskan4134.hytrade.economy;

import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * Default economy provider when no economy system is available.
 * All operations return false/0 to indicate no economy support.
 *
 * This is the default provider until economy mods are integrated.
 */
public class NoEconomyProvider implements EconomyProvider {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String getCurrencyName() {
        return "N/A";
    }

    @Override
    public String getCurrencySymbol() {
        return "";
    }

    @Override
    public double getBalance(PlayerRef player) {
        return 0;
    }

    @Override
    public boolean hasBalance(PlayerRef player, double amount) {
        return false;
    }

    @Override
    public boolean withdraw(PlayerRef player, double amount) {
        return false;
    }

    @Override
    public void deposit(PlayerRef player, double amount) {
        // No-op
    }

    @Override
    public String formatAmount(double amount) {
        return "N/A";
    }
}
