package org.toskan4134.hytrade.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.toskan4134.hytrade.trade.TradeManager;
import org.toskan4134.hytrade.trade.TradeState;

import javax.annotation.Nonnull;

/**
 * Subcommand: /trade request <player>
 * Send a trade request to another player.
 */
public class TradeRequestSubCommand extends AbstractPlayerCommand {

    private final TradeManager tradeManager;
    private final RequiredArg<String> playerArg;

    public TradeRequestSubCommand(TradeManager tradeManager) {
        super("request", "Send a trade request to a player");
        addAliases("req", "send");
        this.tradeManager = tradeManager;
        this.playerArg = withRequiredArg("player", "Target player name", ArgTypes.STRING);
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

        String targetName = playerArg.get(ctx);

        // Check if player is already in a trade
        if (tradeManager.isInTrade(playerRef)) {
            ctx.sender().sendMessage(Message.raw("You are already in a trade. Use /trade cancel to cancel it."));
            return;
        }

        // Find target player by name
        PlayerRef targetRef = Universe.get().getPlayerByUsername(targetName, NameMatching.EXACT_IGNORE_CASE);

        if (targetRef == null) {
            ctx.sender().sendMessage(Message.raw("Player '" + targetName + "' not found."));
            return;
        }

        // Can't trade with yourself
        if (targetRef.getUuid().equals(playerRef.getUuid())) {
            ctx.sender().sendMessage(Message.raw("You cannot trade with yourself. Use /trade test for testing."));
            return;
        }

        // Send trade request
        TradeManager.TradeRequestResult result = tradeManager.requestTrade(playerRef, targetRef);

        if (result.success) {
            ctx.sender().sendMessage(Message.raw("Trade request sent to " + targetName));
            targetRef.sendMessage(Message.raw("You received a trade request from " + playerRef.getUsername() + ". Use /trade accept or /trade decline"));

            // If trade was auto-accepted (they had sent us a request)
            if (result.session != null && result.session.getState() == TradeState.NEGOTIATING) {
                ctx.sender().sendMessage(Message.raw("Trade started! Use /trade status to view."));
                targetRef.sendMessage(Message.raw("Trade started! Use /trade status to view."));
            }
        } else {
            ctx.sender().sendMessage(Message.raw(result.message));
        }
    }

}
