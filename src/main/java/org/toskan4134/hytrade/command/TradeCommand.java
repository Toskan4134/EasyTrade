package org.toskan4134.hytrade.command;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import org.toskan4134.hytrade.TradingPlugin;
import org.toskan4134.hytrade.trade.TradeManager;
import org.toskan4134.hytrade.util.Common;

import static org.toskan4134.hytrade.constants.TradeConstants.DEFAULT_PERMISSION;

/**
 * Main trade command collection.
 * Groups all trade-related subcommands together.
 *
 * Usage:
 *   /trade help              - Show help
 *   /trade request <player>  - Send a trade request to a player
 *   /trade accept            - Accept a pending trade request
 *   /trade decline           - Decline a pending trade request
 *   /trade cancel            - Cancel the current trade
 *   /trade confirm           - Confirm trade after countdown
 *   /trade open              - Open trading UI
 *   /trade reload            - Reload config and messages (admin only)
 *   /trade test              - Start a test trade (solo development)
 */
public class TradeCommand extends AbstractCommandCollection {

    public TradeCommand(String pluginName, String pluginVersion,
                        TradingPlugin plugin, TradeManager tradeManager) {
        super("trade", "Trade items with another player");
        this.requirePermission("hytrade");

        // Add all subcommands
        addSubCommand(new TradeHelpSubCommand(pluginName, pluginVersion, plugin));
        addSubCommand(new TradeRequestSubCommand(tradeManager));
        addSubCommand(new TradeAcceptSubCommand(tradeManager));
        addSubCommand(new TradeDeclineSubCommand(tradeManager));
        addSubCommand(new TradeCancelSubCommand(tradeManager));
        addSubCommand(new TradeConfirmSubCommand(tradeManager));
        addSubCommand(new TradeOpenSubCommand(tradeManager));
        addSubCommand(new TradeReloadSubCommand(plugin));

        if (Common.isDebug()) {
            addSubCommand(new TradeTestSubCommand(tradeManager));
        }
    }
}
