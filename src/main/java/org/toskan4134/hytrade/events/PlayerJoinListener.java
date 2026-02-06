package org.toskan4134.hytrade.events;

import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import org.toskan4134.hytrade.util.VersionChecker;
import org.toskan4134.hytrade.util.Common;

import javax.annotation.Nonnull;

/**
 * Listens for player join events to notify operators about available plugin updates.
 * Operators are identified as players with the wildcard "*" permission (all permissions).
 */
public class PlayerJoinListener {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final VersionChecker versionChecker;
    private EventRegistration<?, ?> registration;

    public PlayerJoinListener(@Nonnull VersionChecker versionChecker) {
        this.versionChecker = versionChecker;
    }

    /**
     * Registers the player join event listener with the plugin's event registry.
     *
     * @param plugin the plugin to register with
     */
    public void register(@Nonnull JavaPlugin plugin) {
        // Register to listen for PlayerReadyEvent (fired when player is fully loaded)
        // Using registerGlobal since PlayerReadyEvent has a String key type
        registration = plugin.getEventRegistry().registerGlobal(
                PlayerReadyEvent.class,
                this::onPlayerReady
        );
        Common.logDebug(LOGGER, "Registered player join listener for update notifications");
    }

    /**
     * Unregisters the event listener.
     */
    public void unregister() {
        if (registration != null) {
            registration.unregister();
            registration = null;
        }
    }

    /**
     * Called when a player is ready (fully joined).
     *
     * @param event the player ready event
     */
    private void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        Player player = event.getPlayer();

        // Check if player is an operator (has wildcard permission = all permissions)
        if (!player.hasPermission("*")) {
            return;
        }

        // Check if update is available
        if (!versionChecker.isUpdateAvailable()) {
            return;
        }

        // Send update notification to the operator
        String message = versionChecker.getPlayerMessage();
        if (message != null) {
            player.sendMessage(Message.raw(message));
            LOGGER.atInfo().log("Notified operator " + player.getDisplayName() + " about available update");
        }
    }
}
