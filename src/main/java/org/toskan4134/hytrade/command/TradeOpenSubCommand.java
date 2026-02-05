package org.toskan4134.hytrade.command;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.toskan4134.hytrade.trade.TradeManager;
import org.toskan4134.hytrade.trade.TradeSession;
import org.toskan4134.hytrade.ui.TradingPage;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Subcommand: /trade open
 * Open the trading UI for the current trade session.
 */
public class TradeOpenSubCommand extends AbstractPlayerCommand {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final TradeManager tradeManager;

    public TradeOpenSubCommand(TradeManager tradeManager) {
        super("open", "Open the trading UI");
        addAliases("ui", "gui", "window");
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

        // Check if player is in an active trade
        Optional<TradeSession> optSession = tradeManager.getSession(playerRef);
        if (optSession.isEmpty()) {
            ctx.sender().sendMessage(Message.raw("You are not in an active trade. Use /trade request <player> or /trade test"));
            return;
        }

        try {
            // Get player component
            Player player = store.getComponent(playerEntityRef, Player.getComponentType());
            if (player == null) {
                ctx.sender().sendMessage(Message.raw("Could not access player data."));
                return;
            }

            // Get page manager
            PageManager pageManager = player.getPageManager();
            if (pageManager == null) {
                ctx.sender().sendMessage(Message.raw("Could not access page manager."));
                return;
            }

            // Create and open the trading page
            LOGGER.atInfo().log("[TradeOpen] Calling tradeManager.openTradeUI()");
            tradeManager.openTradeUI(playerRef, store, playerEntityRef);
            LOGGER.atInfo().log("[TradeOpen] openTradeUI() returned");

            ctx.sender().sendMessage(Message.raw("Trading UI opened."));

        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("[TradeOpen] Exception: " + e.getMessage());
            ctx.sender().sendMessage(Message.raw("Failed to open trading UI: " + e.getMessage()));
        }
    }
}
