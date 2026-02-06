package org.toskan4134.hytrade.command;

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
import org.toskan4134.hytrade.messages.TradeMessages;
import org.toskan4134.hytrade.trade.TradeManager;
import org.toskan4134.hytrade.trade.TradeState;

import javax.annotation.Nonnull;

import static org.toskan4134.hytrade.constants.TradeConstants.DEFAULT_PERMISSION;

/**
 * Subcommand: /trade request <player>
 * Send a trade request to another player.
 */
public class TradeRequestSubCommand extends AbstractPlayerCommand {

    private final TradeManager tradeManager;
    private final RequiredArg<String> playerArg;

    public TradeRequestSubCommand(TradeManager tradeManager) {
        super("request", "Send a trade request to a player");
        addAliases("req", "r", "send", "ask");
        this.requirePermission(DEFAULT_PERMISSION + "request");

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
            ctx.sender().sendMessage(TradeMessages.alreadyInTrade());
            return;
        }

        // Find target player by name
        PlayerRef targetRef = Universe.get().getPlayerByUsername(targetName, NameMatching.EXACT_IGNORE_CASE);

        if (targetRef == null) {
            ctx.sender().sendMessage(TradeMessages.targetNotFound());
            return;
        }

        // Can't trade with yourself
        if (targetRef.getUuid().equals(playerRef.getUuid())) {
            ctx.sender().sendMessage(TradeMessages.cannotTradeSelf());
            return;
        }

        // Send trade request
        TradeManager.TradeRequestResult result = tradeManager.requestTrade(playerRef, targetRef);

        if (result.success) {
            // Send to requester with target name
            ctx.sender().sendMessage(TradeMessages.requestSent(targetName));
            // Send to target with initiator name
            targetRef.sendMessage(TradeMessages.requestReceived(playerRef.getUsername()));

            // If trade was auto-accepted (they had sent us a request)
            if (result.session != null && result.session.getState() == TradeState.NEGOTIATING) {
                ctx.sender().sendMessage(TradeMessages.requestAccepted());
                targetRef.sendMessage(TradeMessages.requestAccepted());
            }
        } else {
            ctx.sender().sendMessage(TradeMessages.alreadyPending());
        }
    }
}
