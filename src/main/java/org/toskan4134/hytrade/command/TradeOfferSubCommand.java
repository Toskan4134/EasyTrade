package org.toskan4134.hytrade.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.toskan4134.hytrade.trade.TradeManager;
import org.toskan4134.hytrade.trade.TradeOffer;
import org.toskan4134.hytrade.trade.TradeSession;
import org.toskan4134.hytrade.trade.TradeState;
import org.toskan4134.hytrade.util.InventoryHelper;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Subcommand: /trade offer <slot>
 * Add an item from inventory to the trade offer.
 */
public class TradeOfferSubCommand extends AbstractPlayerCommand {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final TradeManager tradeManager;
    private final RequiredArg<Integer> slotArg;

    public TradeOfferSubCommand(TradeManager tradeManager) {
        super("offer", "Add an item from inventory to your offer");
        addAliases("add", "put");
        this.tradeManager = tradeManager;
        this.slotArg = withRequiredArg("slot", "Inventory slot number (0-based)", ArgTypes.INTEGER);
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
            ctx.sender().sendMessage(Message.raw("You are not in a trade. Use /trade test or /trade request <player>"));
            return;
        }

        TradeSession session = optSession.get();
        if (session.getState() != TradeState.NEGOTIATING) {
            ctx.sender().sendMessage(Message.raw("Cannot modify offer in current state: " + session.getState().name()));
            ctx.sender().sendMessage(Message.raw("Both players need to be in NEGOTIATING state."));
            return;
        }

        int slot = slotArg.get(ctx);

        // Get item from player's inventory
        try {
            Player player = store.getComponent(playerEntityRef, Player.getComponentType());
            if (player == null) {
                ctx.sender().sendMessage(Message.raw("Could not access your inventory."));
                return;
            }

            var inventory = player.getInventory();
            var backpack = inventory.getBackpack();

            if (slot < 0 || slot >= backpack.getCapacity()) {
                ctx.sender().sendMessage(Message.raw("Invalid slot. Your inventory has " + backpack.getCapacity() + " slots (0-" + (backpack.getCapacity() - 1) + ")"));
                return;
            }

            var item = backpack.getItemStack((short) slot);
            if (item == null || item.isEmpty()) {
                ctx.sender().sendMessage(Message.raw("Slot " + slot + " is empty."));
                return;
            }

            // Add to offer
            TradeOffer myOffer = session.getOfferFor(playerRef);

            // Copy the item to the offer
            var itemCopy = InventoryHelper.copyItemStack(item);
            if (myOffer.addItem(itemCopy)) {
                tradeManager.onOfferChanged(playerRef);
                ctx.sender().sendMessage(Message.raw("Added " + item.getItem().getId() + " x" + item.getQuantity() + " to your offer."));
                ctx.sender().sendMessage(Message.raw("Your offer now has " + myOffer.getItemCount() + " items"));
            } else {
                ctx.sender().sendMessage(Message.raw("Failed to add item to offer."));
            }

        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to add item to offer");
            ctx.sender().sendMessage(Message.raw("Error adding item: " + e.getMessage()));
        }
    }
}
