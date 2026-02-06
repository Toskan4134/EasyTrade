package org.toskan4134.hytrade.command;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.toskan4134.hytrade.messages.TradeMessages;
import org.toskan4134.hytrade.trade.TradeManager;
import org.toskan4134.hytrade.trade.TradeSession;
import org.toskan4134.hytrade.trade.TradeState;

import javax.annotation.Nonnull;
import java.util.Optional;

import static org.toskan4134.hytrade.constants.TradeConstants.DEFAULT_PERMISSION;

/**
 * Subcommand: /trade accept
 * Accept a pending trade request OR accept current trade offers.
 */
public class TradeAcceptSubCommand extends AbstractPlayerCommand {

    private final TradeManager tradeManager;

    public TradeAcceptSubCommand(TradeManager tradeManager) {
        super("accept", "Accept a trade request/Accept current offer");
        addAliases("a", "yes");
        this.requirePermission(DEFAULT_PERMISSION + "accept");

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
                ctx.sender().sendMessage(TradeMessages.acceptedTrade());
                tradeManager.openTradeUI(playerRef, store, playerEntityRef);

                // Notify the initiator
                if (result.session != null) {
                    result.session.getInitiator().sendMessage(TradeMessages.requestAccepted());
                    tradeManager.openTradeUI(result.session.getInitiator(), store, result.session.getInitiator().getReference());
                }
            } else {
                ctx.sender().sendMessage(TradeMessages.noPendingRequest());
            }
            return;
        }

        // Otherwise, check if in active trade and accept the offers
        Optional<TradeSession> optSession = tradeManager.getSession(playerRef);
        if (optSession.isEmpty()) {
            ctx.sender().sendMessage(TradeMessages.notInTradeUseRequest());
            return;
        }

        TradeSession session = optSession.get();
        if (session.getState() != TradeState.NEGOTIATING && session.getState() != TradeState.ONE_ACCEPTED) {
            ctx.sender().sendMessage(TradeMessages.errorNotReady());
            return;
        }

        if (session.hasAccepted(playerRef)) {
            ctx.sender().sendMessage(TradeMessages.statusOneAccepted());
            return;
        }

        if (tradeManager.acceptTrade(playerRef)) {
            ctx.sender().sendMessage(TradeMessages.acceptedTrade());

            // Notify partner
            PlayerRef partner = session.getOtherPlayer(playerRef);
            if (partner != null && !session.isTestMode()) {
                partner.sendMessage(TradeMessages.requestAccepted());
            }

            // Check if both accepted
            if (session.getState() == TradeState.BOTH_ACCEPTED_COUNTDOWN) {
                ctx.sender().sendMessage(TradeMessages.statusOneAccepted());
                if (partner != null && !session.isTestMode()) {
                    partner.sendMessage(TradeMessages.statusOneAccepted());
                }
            }
        } else {
            ctx.sender().sendMessage(TradeMessages.confirmFailed());
        }
    }
}
