package org.toskan4134.hytrade.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
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
 * Subcommand: /trade remove <slot>
 * Remove an item from the trade offer.
 */
public class TradeRemoveSubCommand extends AbstractPlayerCommand {

    private final TradeManager tradeManager;
    private final RequiredArg<Integer> slotArg;

    public TradeRemoveSubCommand(TradeManager tradeManager) {
        super("remove", "Remove an item from your offer");
        addAliases("rem", "take");
        this.tradeManager = tradeManager;
        this.slotArg = withRequiredArg("slot", "Offer slot number (0-based)", ArgTypes.INTEGER);
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
        if (session.getState() != TradeState.NEGOTIATING) {
            ctx.sender().sendMessage(Message.raw("Cannot modify offer in current state: " + session.getState().name()));
            return;
        }

        int slot = slotArg.get(ctx);

        TradeOffer myOffer = session.getOfferFor(playerRef);

        if (slot < 0 || slot >= myOffer.getItemCount()) {
            ctx.sender().sendMessage(Message.raw("Invalid slot. Your offer has " + myOffer.getItemCount() + " items (0-" + (myOffer.getItemCount() - 1) + ")"));
            return;
        }

        var removed = myOffer.removeItem(slot);
        if (removed != null) {
            tradeManager.onOfferChanged(playerRef);
            ctx.sender().sendMessage(Message.raw("Removed " + removed.getItem().getId() + " x" + removed.getQuantity() + " from your offer."));
            ctx.sender().sendMessage(Message.raw("Your offer now has " + myOffer.getItemCount() + " items"));
        } else {
            ctx.sender().sendMessage(Message.raw("Slot " + slot + " is already empty."));
        }
    }
}
