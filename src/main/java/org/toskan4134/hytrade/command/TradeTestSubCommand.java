package org.toskan4134.hytrade.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.toskan4134.hytrade.trade.TradeManager;

import javax.annotation.Nonnull;

/**
 * Subcommand: /trade test
 * Start a test trade session for solo development.
 */
public class TradeTestSubCommand extends AbstractPlayerCommand {

    private final TradeManager tradeManager;

    public TradeTestSubCommand(TradeManager tradeManager) {
        super("test", "Start a solo test trade session");
        addAliases("debug", "solo");
        this.tradeManager = tradeManager;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> playerEntityRef,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {

        // Check if already in a trade
        if (tradeManager.isInTrade(playerRef)) {
            ctx.sender().sendMessage(Message.raw("You are already in a trade. Use /trade cancel first."));
            return;
        }

        // Start test session
        TradeManager.TradeRequestResult result = tradeManager.startTestSession(playerRef);

        if (result.success) {
            ctx.sender().sendMessage(Message.raw(""));
            ctx.sender().sendMessage(Message.raw("=== TEST MODE STARTED ==="));
            ctx.sender().sendMessage(Message.raw("You are now in a simulated trade session."));
            ctx.sender().sendMessage(Message.raw(""));
            ctx.sender().sendMessage(Message.raw("Available commands:"));
            ctx.sender().sendMessage(Message.raw("  /trade status        - View current trade status"));
            ctx.sender().sendMessage(Message.raw("  /trade offer <slot>  - Add item from inventory slot"));
            ctx.sender().sendMessage(Message.raw("  /trade remove <slot> - Remove item from offer slot"));
            ctx.sender().sendMessage(Message.raw("  /trade accept        - Accept the trade"));
            ctx.sender().sendMessage(Message.raw("  /trade confirm       - Confirm after countdown"));
            ctx.sender().sendMessage(Message.raw("  /trade cancel        - Cancel the test"));
            ctx.sender().sendMessage(Message.raw(""));
            ctx.sender().sendMessage(Message.raw("The simulated partner will auto-accept when you do."));

            tradeManager.openTradeUI(playerRef, store, playerEntityRef);

        } else {
            ctx.sender().sendMessage(Message.raw("Failed to start test mode: " + result.message));
        }
    }
}
