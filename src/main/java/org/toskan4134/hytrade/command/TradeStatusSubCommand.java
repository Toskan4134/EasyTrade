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
import org.toskan4134.hytrade.trade.TradeOffer;
import org.toskan4134.hytrade.trade.TradeSession;
import org.toskan4134.hytrade.trade.TradeState;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Subcommand: /trade status
 * View current trade status, offers, and acceptance state.
 */
public class TradeStatusSubCommand extends AbstractPlayerCommand {

    private final TradeManager tradeManager;

    public TradeStatusSubCommand(TradeManager tradeManager) {
        super("status", "View current trade status");
        addAliases("s", "info", "view");
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
            // Check pending requests
            if (tradeManager.hasPendingRequest(playerRef)) {
                ctx.sender().sendMessage(Message.raw("You have a pending trade request."));
                ctx.sender().sendMessage(Message.raw("Use /trade accept or /trade decline"));
            } else {
                ctx.sender().sendMessage(Message.raw("You are not in a trade."));
                ctx.sender().sendMessage(Message.raw("Use /trade request <player> or /trade test"));
            }
            return;
        }

        TradeSession session = optSession.get();
        boolean isTestMode = session.isTestMode();
        boolean iAmInitiator = playerRef.getUuid().equals(session.getInitiator().getUuid());

        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(Message.raw("=== TRADE STATUS ===" + (isTestMode ? " [TEST MODE]" : "")));
        ctx.sender().sendMessage(Message.raw("State: " + session.getState().name()));
        ctx.sender().sendMessage(Message.raw(""));

        // My offer
        TradeOffer myOffer = session.getOfferFor(playerRef);
        int myOfferCount = myOffer != null ? myOffer.getItemCount() : 0;
        ctx.sender().sendMessage(Message.raw("YOUR OFFER: " + myOfferCount + " items"));
        if (myOffer != null && myOfferCount > 0) {
            for (int i = 0; i < myOfferCount; i++) {
                var item = myOffer.getItem(i);
                if (item != null && !item.isEmpty()) {
                    ctx.sender().sendMessage(Message.raw("  [" + i + "] " + item.getItem().getId() + " x" + item.getQuantity()));
                }
            }
        } else {
            ctx.sender().sendMessage(Message.raw("  (empty - use /trade offer <slot> to add items)"));
        }

        // Partner offer
        PlayerRef partner = session.getOtherPlayer(playerRef);
        TradeOffer partnerOffer = session.getOfferFor(partner);
        int partnerOfferCount = partnerOffer != null ? partnerOffer.getItemCount() : 0;
        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(Message.raw("PARTNER OFFER: " + partnerOfferCount + " items"));
        if (partnerOffer != null && partnerOfferCount > 0) {
            for (int i = 0; i < partnerOfferCount; i++) {
                var item = partnerOffer.getItem(i);
                if (item != null && !item.isEmpty()) {
                    ctx.sender().sendMessage(Message.raw("  [" + i + "] " + item.getItem().getId() + " x" + item.getQuantity()));
                }
            }
        } else {
            ctx.sender().sendMessage(Message.raw("  (empty)"));
        }

        ctx.sender().sendMessage(Message.raw(""));
        boolean myAccepted = session.hasAccepted(playerRef);
        boolean partnerAccepted = iAmInitiator ? session.isTargetAccepted() : session.isInitiatorAccepted();
        ctx.sender().sendMessage(Message.raw("You accepted: " + (myAccepted ? "YES" : "NO")));
        ctx.sender().sendMessage(Message.raw("Partner accepted: " + (partnerAccepted ? "YES"  : "NO")));

        if (session.getState() == TradeState.BOTH_ACCEPTED_COUNTDOWN) {
            long remaining = session.getRemainingCountdownMs();
            if (remaining > 0) {
                ctx.sender().sendMessage(Message.raw(""));
                ctx.sender().sendMessage(Message.raw("Countdown: " + (remaining / 1000) + "s remaining..."));
            } else {
                ctx.sender().sendMessage(Message.raw(""));
                ctx.sender().sendMessage(Message.raw("Ready to confirm! Use /trade confirm"));
            }
        } else if (session.getState() == TradeState.NEGOTIATING) {
            ctx.sender().sendMessage(Message.raw(""));
            ctx.sender().sendMessage(Message.raw("Use /trade accept when ready"));
        }
    }
}
