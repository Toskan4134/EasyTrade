package org.toskan4134.hytrade.command;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import org.toskan4134.hytrade.TradingPlugin;
import org.toskan4134.hytrade.messages.TradeMessages;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

import static org.toskan4134.hytrade.constants.TradeConstants.ADMIN_PERMISSION;
import static org.toskan4134.hytrade.constants.TradeConstants.DEFAULT_PERMISSION;

/**
 * Subcommand: /trade reload
 * Reloads the plugin configuration and messages from disk.
 * Requires admin permission.
 */
public class TradeReloadSubCommand extends AbstractCommand {

    private final TradingPlugin plugin;

    public TradeReloadSubCommand(TradingPlugin plugin) {
        super("reload", "Reload plugin configuration and messages");
        this.requirePermission(ADMIN_PERMISSION + "reload");
        this.plugin = plugin;
    }

    @Override
    @Nullable
    protected CompletableFuture<Void> execute(@Nonnull CommandContext ctx) {
        try {
            // Reload config
            plugin.getConfigManager().reloadConfig();

            // Reload messages
            plugin.getConfigManager().reloadMessages();

            // Re-initialize TradeMessages with new config
            TradeMessages.init(plugin.getConfigManager());

            ctx.sender().sendMessage(TradeMessages.reloadSuccess());
        } catch (Exception e) {
            ctx.sender().sendMessage(TradeMessages.reloadFailed(e.getMessage()));
        }

        return CompletableFuture.completedFuture(null);
    }
}
