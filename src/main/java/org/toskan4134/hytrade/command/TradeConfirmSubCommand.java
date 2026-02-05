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
import org.toskan4134.hytrade.trade.TradeSession;
import org.toskan4134.hytrade.trade.TradeState;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Subcommand: /trade confirm
 * Confirm the trade after countdown completes.
 */
public class TradeConfirmSubCommand extends AbstractPlayerCommand {

    private final TradeManager tradeManager;

    public TradeConfirmSubCommand(TradeManager tradeManager) {
        super("confirm", "Confirm the trade after countdown");
        addAliases("finalize", "complete");
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

        Optional<TradeSession> optSession = tradeManager.getSession(playerRef);
        if (optSession.isEmpty()) {
            ctx.sender().sendMessage(Message.raw("You are not in a trade."));
            return;
        }

        TradeSession session = optSession.get();

        if (session.getState() != TradeState.BOTH_ACCEPTED_COUNTDOWN) {
            ctx.sender().sendMessage(Message.raw("Both players must accept first."));
            ctx.sender().sendMessage(Message.raw("Current state: " + session.getState().name()));
            ctx.sender().sendMessage(Message.raw("Use /trade accept to accept the trade."));
            return;
        }

        if (!session.isCountdownComplete()) {
            long remaining = session.getRemainingCountdownMs() / 1000;
            ctx.sender().sendMessage(Message.raw("Please wait " + remaining + " more seconds before confirming."));
            return;
        }

        ctx.sender().sendMessage(Message.raw("Executing trade..."));

        TradeSession.TradeResult result = tradeManager.confirmTrade(playerRef, store, playerEntityRef);

        if (result.success) {
            ctx.sender().sendMessage(Message.raw("Trade completed successfully!"));
        } else {
            ctx.sender().sendMessage(Message.raw("Trade failed: " + result.message));
        }
    }
}
