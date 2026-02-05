package org.toskan4134.hytrade.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * Subcommand: /trade help
 * Shows detailed help for the trading system.
 */
public class TradeHelpSubCommand extends AbstractCommand {

    private final String pluginName;
    private final String pluginVersion;

    public TradeHelpSubCommand(String pluginName, String pluginVersion) {
        super("help", "Show trading help");
        addAliases("?");
        this.pluginName = pluginName;
        this.pluginVersion = pluginVersion;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false; // Anyone can use help
    }

    @Override
    @Nullable
    protected CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        ctx.sender().sendMessage(Message.raw("=== " + pluginName + " v" + pluginVersion + " ==="));
        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(Message.raw("BASIC COMMANDS:"));
        ctx.sender().sendMessage(Message.raw("  /trade request <player> - Send trade request"));
        ctx.sender().sendMessage(Message.raw("  /trade accept           - Accept pending request"));
        ctx.sender().sendMessage(Message.raw("  /trade decline          - Decline pending request"));
        ctx.sender().sendMessage(Message.raw("  /trade cancel           - Cancel current trade"));
        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(Message.raw("TRADING COMMANDS:"));
        ctx.sender().sendMessage(Message.raw("  /trade status           - View current trade status"));
        ctx.sender().sendMessage(Message.raw("  /trade offer <slot>     - Add item from inventory"));
        ctx.sender().sendMessage(Message.raw("  /trade remove <slot>    - Remove item from offer"));
        ctx.sender().sendMessage(Message.raw("  /trade confirm          - Confirm after countdown"));
        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(Message.raw("UI & DEV:"));
        ctx.sender().sendMessage(Message.raw("  /trade open             - Open trading UI"));
        ctx.sender().sendMessage(Message.raw("  /trade test             - Start solo test trade"));
        ctx.sender().sendMessage(Message.raw("  /trade help             - Show this help"));
        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(Message.raw("HOW TO TRADE:"));
        ctx.sender().sendMessage(Message.raw("  1. /trade request <player>"));
        ctx.sender().sendMessage(Message.raw("  2. Other player: /trade accept"));
        ctx.sender().sendMessage(Message.raw("  3. Both: /trade offer <slot> to add items"));
        ctx.sender().sendMessage(Message.raw("  4. Both: /trade accept when ready"));
        ctx.sender().sendMessage(Message.raw("  5. Wait 3 seconds, then /trade confirm"));

        return CompletableFuture.completedFuture(null);
    }
}
