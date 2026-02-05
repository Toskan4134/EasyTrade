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
 * Subcommand: /trade decline
 * Decline a pending trade request.
 */
public class TradeDeclineSubCommand extends AbstractPlayerCommand {

    private final TradeManager tradeManager;

    public TradeDeclineSubCommand(TradeManager tradeManager) {
        super("decline", "Decline a pending trade request");
        addAliases("d", "deny", "no");
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

        if (tradeManager.declineTradeRequest(playerRef)) {
            ctx.sender().sendMessage(Message.raw("Trade request declined."));
        } else {
            ctx.sender().sendMessage(Message.raw("You don't have any pending trade requests."));
        }
    }
}
