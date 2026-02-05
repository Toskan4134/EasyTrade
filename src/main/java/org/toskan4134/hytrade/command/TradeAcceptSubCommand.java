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
 * Subcommand: /trade accept
 * Accept a pending trade request OR accept current trade offers.
 */
public class TradeAcceptSubCommand extends AbstractPlayerCommand {

    private final TradeManager tradeManager;

    public TradeAcceptSubCommand(TradeManager tradeManager) {
        super("accept", "Accept a trade request or accept current offers");
        addAliases("a", "yes");
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

        // First check if player has a pending request to accept
        if (tradeManager.hasPendingRequest(playerRef)) {
            TradeManager.TradeRequestResult result = tradeManager.acceptTradeRequest(playerRef);

            if (result.success) {
                ctx.sender().sendMessage(Message.raw("Trade accepted! Use /trade status to view."));
                tradeManager.openTradeUI(playerRef, store, playerEntityRef);

                // Notify the initiator
                if (result.session != null) {
                    result.session.getInitiator().sendMessage(Message.raw("Your trade request was accepted!"));
                    tradeManager.openTradeUI(result.session.getInitiator(), store, result.session.getInitiator().getReference());

                }
            } else {
                ctx.sender().sendMessage(Message.raw(result.message));
            }
            return;
        }

        // Otherwise, check if in active trade and accept the offers
        Optional<TradeSession> optSession = tradeManager.getSession(playerRef);
        if (optSession.isEmpty()) {
            ctx.sender().sendMessage(Message.raw("You don't have any pending trade requests or active trades."));
            return;
        }

        TradeSession session = optSession.get();
        if (session.getState() != TradeState.NEGOTIATING && session.getState() != TradeState.ONE_ACCEPTED) {
            ctx.sender().sendMessage(Message.raw("Cannot accept in current state: " + session.getState().name()));
            return;
        }

        if (session.hasAccepted(playerRef)) {
            ctx.sender().sendMessage(Message.raw("You have already accepted. Waiting for partner..."));
            return;
        }

        if (tradeManager.acceptTrade(playerRef)) {
            ctx.sender().sendMessage(Message.raw("You accepted the trade offers."));

            // Notify partner
            PlayerRef partner = session.getOtherPlayer(playerRef);
            if (partner != null && !session.isTestMode()) {
                partner.sendMessage(Message.raw("Your trade partner accepted!"));
            }

            // Check if both accepted
            if (session.getState() == TradeState.BOTH_ACCEPTED_COUNTDOWN) {
                String msg = "Both players accepted! Wait 3 seconds, then use /trade confirm";
                ctx.sender().sendMessage(Message.raw(msg));
                if (partner != null && !session.isTestMode()) {
                    partner.sendMessage(Message.raw(msg));
                }
            }
        } else {
            ctx.sender().sendMessage(Message.raw("Failed to accept trade."));
        }
    }
}
