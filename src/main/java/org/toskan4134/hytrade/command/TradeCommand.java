package org.toskan4134.hytrade.command;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import org.toskan4134.hytrade.TradingPlugin;
import org.toskan4134.hytrade.trade.TradeManager;

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
 *   /trade status            - View current trade status
 *   /trade offer <slot>      - Add item from inventory to offer
 *   /trade remove <slot>     - Remove item from offer
 *   /trade confirm           - Confirm trade after countdown
 *   /trade test              - Start a test trade (solo development)
 */
public class TradeCommand extends AbstractCommandCollection {

    public TradeCommand(String pluginName, String pluginVersion,
                        TradingPlugin plugin, TradeManager tradeManager) {
        super("trade", "Trade items with another player");

        // Add all subcommands
        addSubCommand(new TradeHelpSubCommand(pluginName, pluginVersion));
        addSubCommand(new TradeRequestSubCommand(tradeManager));
        addSubCommand(new TradeAcceptSubCommand(tradeManager));
        addSubCommand(new TradeDeclineSubCommand(tradeManager));
        addSubCommand(new TradeCancelSubCommand(tradeManager));
        addSubCommand(new TradeStatusSubCommand(tradeManager));
        addSubCommand(new TradeOfferSubCommand(tradeManager));
        addSubCommand(new TradeRemoveSubCommand(tradeManager));
        addSubCommand(new TradeConfirmSubCommand(tradeManager));
        addSubCommand(new TradeTestSubCommand(tradeManager));
        addSubCommand(new TradeOpenSubCommand(tradeManager));
    }
}
