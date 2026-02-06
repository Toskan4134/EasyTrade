package org.toskan4134.hytrade.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import org.toskan4134.hytrade.TradingPlugin;
import org.toskan4134.hytrade.messages.TradeMessages;
import org.toskan4134.hytrade.util.Common;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

import static org.toskan4134.hytrade.constants.TradeConstants.DEFAULT_PERMISSION;

/**
 * Subcommand: /trade help
 * Shows detailed help for the trading system.
 */
public class TradeHelpSubCommand extends AbstractCommand {

    private final String pluginVersion;

    public TradeHelpSubCommand(String pluginName, String pluginVersion, TradingPlugin plugin) {
        super("help", "Show trading help");
        addAliases("?");
        this.requirePermission(DEFAULT_PERMISSION + "help");

        this.pluginVersion = pluginVersion;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false; // Anyone can use help
    }

    @Override
    @Nullable
    protected CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        ctx.sender().sendMessage(TradeMessages.helpHeader(pluginVersion));
        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(TradeMessages.helpBasic());
        ctx.sender().sendMessage(TradeMessages.helpRequest());
        ctx.sender().sendMessage(TradeMessages.helpAccept());
        ctx.sender().sendMessage(TradeMessages.helpDecline());
        ctx.sender().sendMessage(TradeMessages.helpCancel());
        ctx.sender().sendMessage(TradeMessages.helpConfirm());
        ctx.sender().sendMessage(TradeMessages.helpOpen());
        ctx.sender().sendMessage(TradeMessages.helpReload());
        if (Common.isDebug()) {
            ctx.sender().sendMessage(TradeMessages.helpTest());
        }
        ctx.sender().sendMessage(TradeMessages.helpHelpCmd());
        ctx.sender().sendMessage(Message.raw(""));
        ctx.sender().sendMessage(TradeMessages.helpHowTo());
        ctx.sender().sendMessage(TradeMessages.helpStep1());
        ctx.sender().sendMessage(TradeMessages.helpStep2());
        ctx.sender().sendMessage(TradeMessages.helpStep3());
        ctx.sender().sendMessage(TradeMessages.helpStep4());
        ctx.sender().sendMessage(TradeMessages.helpStep5());

        return CompletableFuture.completedFuture(null);
    }
}
