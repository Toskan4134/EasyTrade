package org.toskan4134.hytrade.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.toskan4134.hytrade.messages.TradeMessages;
import org.toskan4134.hytrade.trade.TradeManager;

import javax.annotation.Nonnull;

import static org.toskan4134.hytrade.constants.TradeConstants.ADMIN_PERMISSION;
import static org.toskan4134.hytrade.constants.TradeConstants.DEFAULT_PERMISSION;

/**
 * Subcommand: /trade test
 * Start a test trade session for solo development.
 */
public class TradeTestSubCommand extends AbstractPlayerCommand {

    private final TradeManager tradeManager;

    public TradeTestSubCommand(TradeManager tradeManager) {
        super("test", "Start a solo test trade session");
        addAliases("debug", "solo");
        this.requirePermission(ADMIN_PERMISSION + "test");

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
            ctx.sender().sendMessage(TradeMessages.alreadyInTrade());
            return;
        }

        // Start test session
        TradeManager.TradeRequestResult result = tradeManager.startTestSession(playerRef);

        if (result.success) {
            ctx.sender().sendMessage(Message.raw(""));
            ctx.sender().sendMessage(TradeMessages.testHeader());
            ctx.sender().sendMessage(TradeMessages.testDescription());
            ctx.sender().sendMessage(Message.raw(""));
            ctx.sender().sendMessage(TradeMessages.testCommands());
            ctx.sender().sendMessage(TradeMessages.testAccept());
            ctx.sender().sendMessage(TradeMessages.testConfirm());
            ctx.sender().sendMessage(TradeMessages.testCancel());
            ctx.sender().sendMessage(Message.raw(""));
            ctx.sender().sendMessage(TradeMessages.testAutoAccept());

            tradeManager.openTradeUI(playerRef, store, playerEntityRef);

        } else {
            ctx.sender().sendMessage(TradeMessages.testFailed());
        }
    }
}
