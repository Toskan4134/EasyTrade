package org.toskan4134.hytrade.config;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.util.Config;
import org.toskan4134.hytrade.storage.MessagesStorage;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages plugin configuration and messages.
 * Config is stored in HyTrade.json (handled by Hytale's Config system).
 * Messages are stored in messages.json (custom JSON file).
 */
public class ConfigManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Config<TradeConfig> config;
    private final MessagesStorage messagesStorage;

    public ConfigManager(Config<TradeConfig> config, File dataFolder) {
        this.config = config;
        this.messagesStorage = new MessagesStorage(dataFolder);
        loadMessages();
    }

    /**
     * Load messages from messages.json.
     * If file doesn't exist, creates it with default messages.
     */
    private void loadMessages() {
        if (messagesStorage.load()) {
            // Merge with defaults to add any new message keys
            if (messagesStorage.mergeWithDefaults(getDefaultMessages())) {
                LOGGER.atInfo().log("Added new message keys to messages.json");
                messagesStorage.save();
            }
            return;
        }

        // messages.json doesn't exist - create with defaults
        LOGGER.atInfo().log("Creating default messages.json");
        messagesStorage.setMessages(getDefaultMessages());
        messagesStorage.save();
    }

    /**
     * Get the Trade configuration.
     */
    public TradeConfig getConfig() {
        return config.get();
    }

    /**
     * Get a message by key.
     */
    public String getMessage(String key) {
        return messagesStorage.getMessage(key);
    }

    /**
     * Get a message with placeholder replacements.
     *
     * @param key          The message key
     * @param replacements Key-value pairs for replacement (must be even length)
     * @return Message with replacements applied
     */
    public String getMessage(String key, String... replacements) {
        return messagesStorage.getMessage(key, replacements);
    }

    /**
     * Set a message value.
     */
    public void setMessage(String key, String value) {
        messagesStorage.setMessage(key, value);
    }

    /**
     * Save messages to disk.
     */
    public void saveMessages() {
        messagesStorage.save();
    }

    /**
     * Reload configuration from disk.
     */
    public void reloadConfig() {
        config.load();
        LOGGER.atInfo().log("Configuration reloaded");
    }

    /**
     * Reload messages from disk.
     */
    public void reloadMessages() {
        loadMessages();
        LOGGER.atInfo().log("Messages reloaded");
    }

    /**
     * Define default messages with color codes.
     * Color codes: &0-9,&a-f for standard colors, &#RRGGBB for hex colors, &l for bold, &r for reset
     */
    private static Map<String, String> getDefaultMessages() {
        Map<String, String> messages = new LinkedHashMap<>();

        // ===== Request Messages =====
        messages.put("trade.request.sent", "&aTrade request sent to &f{target}");
        messages.put("trade.request.received", "&eTrade request received from &f{initiator}&e. Use &6/trade accept &7to accept.");
        messages.put("trade.request.accepted", "&aTradeRequest accepted! Trade started.");
        messages.put("trade.request.declined", "&cTrade request declined.");
        messages.put("trade.request.alreadyInTrade", "&cYou are already in a trade.");
        messages.put("trade.request.targetAlreadyInTrade", "&cThat player is already in a trade.");
        messages.put("trade.request.alreadyPending", "&cYou already have a pending request to this player.");
        messages.put("trade.request.noPending", "&cNo pending trade request.");
        messages.put("trade.request.expired", "&cTrade request expired.");
        messages.put("trade.request.targetNotFound", "&cTarget player not found");
        messages.put("trade.request.cannotTradeSelf", "&eYou cannot trade with yourself.");

        // ===== Cancel Messages =====
        messages.put("trade.cancelled.byYou", "&eTrade cancelled");
        messages.put("trade.cancelled.byPartner", "&eTrade cancelled by your partner");
        messages.put("trade.cancelled.notInTrade", "&cYou are not in an active trade");
        messages.put("trade.cancelled.tradeCancelled", "&eTrade cancelled");
        messages.put("trade.cancel.failed", "&cFailed to cancel trade");

        // ===== Status Messages =====
        messages.put("trade.status.negotiating", "&fNegotiating offers");
        messages.put("trade.status.oneAccepted", "&eOne player accepted");
        messages.put("trade.status.countdown", "&aBoth accepted! Completing in &f{seconds}s&a...");
        messages.put("trade.status.completed", "&aTrade completed successfully!");
        messages.put("trade.status.failed", "&cTrade failed: {reason}");
        messages.put("trade.status.cancelled", "&eTrade cancelled");

        // ===== Pending Request =====
        messages.put("trade.pending.request", "&eYou have a pending trade request");
        messages.put("trade.pending.instructions", "&fUse &6/trade accept &7or &6/trade decline");
        messages.put("trade.pending.useRequest", "&eYou are not trading right now. Use &6/trade request &3<player> &eto start trading");

        // ===== Help Messages =====
        messages.put("trade.help.header", "&b&l=== HyTrade v{version} ===");
        messages.put("trade.help.basic", "&f&lCOMMANDS:");
        messages.put("trade.help.request", "&6  /trade request &3<player> &7- Send trade request");
        messages.put("trade.help.accept", "&6  /trade accept &7- Accept pending request");
        messages.put("trade.help.decline", "&6  /trade decline &7- Decline pending request");
        messages.put("trade.help.cancel", "&6  /trade cancel &7- Cancel current trade");
        messages.put("trade.help.confirm", "&6  /trade confirm &7- Confirm after countdown");
        messages.put("trade.help.open", "&6  /trade open &7- Open trading UI");
        messages.put("trade.help.reload", "&6  /trade reload &7- Reload config and messages &c(admin)");
        messages.put("trade.help.test", "&6  /trade test &7- Start solo test trade &c(admin)");
        messages.put("trade.help.helpCmd", "&6  /trade help &7- Show this help");
        messages.put("trade.help.howTo", "&f&lHOW TO TRADE:");
        messages.put("trade.help.step1", "&7  1. &6/trade request &3<player>");
        messages.put("trade.help.step2", "&7  2. Other player: &6/trade accept");
        messages.put("trade.help.step3", "&7  3. Use the trading UI to add/remove items");
        messages.put("trade.help.step4", "&7  4. Both: Use UI or &6/trade accept &7when ready");
        messages.put("trade.help.step5", "&7  5. Wait for countdown, then use UI or &6/trade confirm");
        messages.put("trade.autoaccepted", "&aTrade accepted! Use &6/trade open &ato begin trading.");

        // ===== Accept/Decline =====
        messages.put("trade.accepted.trade", "&aTrade accepted! Use &6/trade open &ato begin trading.");
        messages.put("trade.declined.trade", "&eTrade declined");
        messages.put("trade.declined.request", "&eTrade request declined");

        // ===== Errors =====
        messages.put("trade.error.noActiveSession", "&cNo active trade session");
        messages.put("trade.error.notReady", "&cTrade not ready");
        messages.put("trade.error.countdownNotComplete", "&cCountdown not complete");
        messages.put("trade.error.playerUnavailable", "&cOther player not available");
        messages.put("trade.error.itemsNotFound", "&cYou don't have all the offered items anymore");
        messages.put("trade.error.partnerItemsNotFound", "&c{player} doesn't have all offered items");
        messages.put("trade.error.noSpace", "&cYou don't have enough inventory space");
        messages.put("trade.error.partnerNoSpace", "&c{player} doesn't have enough space");
        messages.put("trade.error.withdrawFailed", "&cFailed to withdraw your items");
        messages.put("trade.error.partnerWithdrawFailed", "&c{player} - failed to withdraw items");
        messages.put("trade.error.depositFailed", "&cFailed to receive items");
        messages.put("trade.error.partnerDepositFailed", "&c{player} couldn't receive items");
        messages.put("trade.error.systemError", "&cTrade failed: {reason}");
        messages.put("trade.error.acceptFirst", "&eBoth players must accept first");

        // ===== Disconnect =====
        messages.put("trade.disconnect.cancelled", "&eTrade cancelled - other player disconnected");
        messages.put("trade.disconnect.requestCancelled", "&eTrade request cancelled - player disconnected");

        // ===== Test Mode =====
        messages.put("trade.test.started", "&aTest trade session started. You are both players");
        messages.put("trade.test.notAvailable", "&cTest mode is not available");
        messages.put("trade.test.header", "&b&l=== TEST MODE STARTED ===");
        messages.put("trade.test.description", "&fYou are now in a simulated trade session.");
        messages.put("trade.test.commands", "&f&lAvailable commands:");
        messages.put("trade.test.accept", "&6  /trade accept &7- Accept the trade");
        messages.put("trade.test.confirm", "&6  /trade confirm &7- Confirm after countdown");
        messages.put("trade.test.cancel", "&6  /trade cancel &7- Cancel the test");
        messages.put("trade.test.autoAccept", "&eThe simulated partner will auto-accept when you do.");
        messages.put("trade.test.failed", "&cFailed to start test mode");

        // ===== Reload =====
        messages.put("trade.reload.success", "&aConfiguration and messages reloaded successfully!");
        messages.put("trade.reload.failed", "&cFailed to reload: {reason}");

        // ===== UI =====
        messages.put("trade.ui.opened", "&aTrading UI opened");
        messages.put("trade.ui.openFailed", "&cFailed to open trading UI");
        messages.put("trade.confirm.success", "&aTrade confirmed and completed!");
        messages.put("trade.confirm.tooEarly", "&eWait for countdown before confirming");
        messages.put("trade.confirm.failed", "&cTrade confirmation failed");

        // ===== UI Status Messages (plain text for UI elements) =====
        messages.put("ui.status.clickInstructions", "Click item = 1 stack, use +1/+10 buttons");
        messages.put("ui.status.waitingForPartner", "Waiting for partner...");
        messages.put("ui.status.partnerAccepted", "Partner accepted! Click ACCEPT");
        messages.put("ui.status.bothAccepted", "Both accepted! Wait for countdown");
        messages.put("ui.status.countdownReady", "Ready! Click CONFIRM to complete");
        messages.put("ui.status.modifiedInventory", "{player}'s inventory has been modified");
        messages.put("ui.status.partnerModified", "Partner modified their offer");
        messages.put("ui.status.acceptRevoked", "Acceptance revoked - inventory changed");
        messages.put("ui.status.acceptRevokedManual", "Acceptance revoked");
        messages.put("ui.status.partnerAcceptRevoked", "Partner's acceptance revoked");
        messages.put("ui.status.noItemsAvailable", "No items available to offer");
        messages.put("ui.status.itemNotFound", "Item not found");
        messages.put("ui.status.failedToAdd", "Failed to add item to offer");
        messages.put("ui.status.notEnoughSpace", "You don't have enough inventory space");
        messages.put("ui.status.waitMoreSeconds", "Wait {seconds} more seconds");
        messages.put("ui.status.tradeCompleted", "Trade completed successfully!");
        messages.put("ui.status.cancelledByPartner", "Trade cancelled by partner");
        messages.put("ui.status.failedValidation", "Cannot accept - items no longer available");
        messages.put("ui.status.cannotAcceptState", "Cannot accept in current state");
        messages.put("ui.status.noActiveSession", "No active trade session");

        // ===== UI Item Actions =====
        messages.put("ui.action.addedToOffer", "Added x{amount} to offer");
        messages.put("ui.action.returnedFromOffer", "Returned x{amount} from offer");
        messages.put("ui.action.removedFromOffer", "Removed {item} from offer");
        messages.put("ui.action.reducedInOffer", "Reduced {item} to {amount}");

        // ===== UI Element Text =====
        messages.put("ui.title.trade", "HyTrade");
        messages.put("ui.label.yourOffer", "YOUR OFFER");
        messages.put("ui.label.partnerOffer", "PARTNER OFFER");
        messages.put("ui.label.yourInventory", "YOUR INVENTORY");
        messages.put("ui.label.yourStatus", "Your Status:");
        messages.put("ui.label.partnerStatus", "Partner Status:");
        messages.put("ui.label.tradingWith", "Trading with:");
        messages.put("ui.label.testPartner", "Test Partner (You)");
        messages.put("ui.label.unknown", "Unknown");
        messages.put("ui.button.accept", "ACCEPT");
        messages.put("ui.button.confirm", "CONFIRM");
        messages.put("ui.button.cancel", "CANCEL");
        messages.put("ui.status.accepted", "ACCEPTED");
        messages.put("ui.status.notAccepted", "Not accepted");
        messages.put("ui.status.acceptedWaiting", "Accepted! Waiting for partner...");
        messages.put("ui.status.ready", "READY");

        return messages;
    }
}
