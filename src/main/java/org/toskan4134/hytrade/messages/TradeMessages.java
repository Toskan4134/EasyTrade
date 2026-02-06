package org.toskan4134.hytrade.messages;

import com.hypixel.hytale.server.core.Message;
import org.toskan4134.hytrade.config.ConfigManager;
import org.toskan4134.hytrade.util.MessageFormatter;

/**
 * Trade message helper class.
 * Uses ConfigManager to load messages from messages.json and MessageFormatter to convert color codes.
 *
 * All messages support color codes (&a, &c, &#FF0000, etc.) and placeholders ({player}, {target}, etc.)
 */
public class TradeMessages {

    private static ConfigManager configManager;

    /**
     * Initialize the message system with a config manager.
     * Must be called during plugin startup before using any message methods.
     */
    public static void init(ConfigManager manager) {
        configManager = manager;
    }

    // ===== Request Messages =====

    public static Message requestSent(String target) {
        return format("trade.request.sent", "target", target);
    }

    public static Message requestReceived(String initiator) {
        return format("trade.request.received", "initiator", initiator);
    }

    public static Message requestAccepted() {
        return format("trade.request.accepted");
    }

    public static Message requestDeclined() {
        return format("trade.request.declined");
    }

    public static Message alreadyInTrade() {
        return format("trade.request.alreadyInTrade");
    }

    public static Message targetAlreadyInTrade() {
        return format("trade.request.targetAlreadyInTrade");
    }

    public static Message alreadyPending() {
        return format("trade.request.alreadyPending");
    }

    public static Message noPendingRequest() {
        return format("trade.request.noPending");
    }

    public static Message requestExpired() {
        return format("trade.request.expired");
    }

    public static Message targetNotFound() {
        return format("trade.request.targetNotFound");
    }

    public static Message cannotTradeSelf() {
        return format("trade.request.cannotTradeSelf");
    }

    // ===== Cancel Messages =====

    public static Message cancelledByYou() {
        return format("trade.cancelled.byYou");
    }

    public static Message cancelledByPartner() {
        return format("trade.cancelled.byPartner");
    }

    public static Message notInTrade() {
        return format("trade.cancelled.notInTrade");
    }

    public static Message tradeCancelled() {
        return format("trade.cancelled.tradeCancelled");
    }

    public static Message cancelFailed() {
        return format("trade.cancel.failed");
    }

    // ===== Status Messages =====

    public static Message statusNegotiating() {
        return format("trade.status.negotiating");
    }

    public static Message statusOneAccepted() {
        return format("trade.status.oneAccepted");
    }

    public static Message statusCountdown(long seconds) {
        return format("trade.status.countdown", "seconds", String.valueOf(seconds));
    }

    public static Message statusCompleted() {
        return format("trade.status.completed");
    }

    public static Message statusFailed(String reason) {
        return format("trade.status.failed", "reason", reason);
    }

    public static Message statusCancelled() {
        return format("trade.status.cancelled");
    }

    // ===== Pending Request =====

    public static Message pendingRequest() {
        return format("trade.pending.request");
    }

    public static Message pendingInstructions() {
        return format("trade.pending.instructions");
    }

    public static Message notInTradeUseRequest() {
        return format("trade.pending.useRequest");
    }

    // ===== Help Messages =====

    public static Message helpHeader(String version) {
        return format("trade.help.header", "version", version);
    }

    public static Message helpBasic() {
        return format("trade.help.basic");
    }

    public static Message helpRequest() {
        return format("trade.help.request");
    }

    public static Message helpAccept() {
        return format("trade.help.accept");
    }

    public static Message helpDecline() {
        return format("trade.help.decline");
    }

    public static Message helpCancel() {
        return format("trade.help.cancel");
    }

    public static Message helpConfirm() {
        return format("trade.help.confirm");
    }

    public static Message helpOpen() {
        return format("trade.help.open");
    }

    public static Message helpReload() {
        return format("trade.help.reload");
    }

    public static Message helpTest() {
        return format("trade.help.test");
    }

    public static Message helpHelpCmd() {
        return format("trade.help.helpCmd");
    }

    public static Message helpHowTo() {
        return format("trade.help.howTo");
    }

    public static Message helpStep1() {
        return format("trade.help.step1");
    }

    public static Message helpStep2() {
        return format("trade.help.step2");
    }

    public static Message helpStep3() {
        return format("trade.help.step3");
    }

    public static Message helpStep4() {
        return format("trade.help.step4");
    }

    public static Message helpStep5() {
        return format("trade.help.step5");
    }

    // ===== Accept/Decline =====

    public static Message acceptedTrade() {
        return format("trade.accepted.trade");
    }

    public static Message declinedTrade() {
        return format("trade.declined.trade");
    }

    public static Message declinedRequest() {
        return format("trade.declined.request");
    }

    // ===== Errors =====

    public static Message errorNoActiveSession() {
        return format("trade.error.noActiveSession");
    }

    public static Message errorNotReady() {
        return format("trade.error.notReady");
    }

    public static Message errorCountdownNotComplete() {
        return format("trade.error.countdownNotComplete");
    }

    public static Message errorPlayerUnavailable() {
        return format("trade.error.playerUnavailable");
    }

    public static Message errorItemsNotFound() {
        return format("trade.error.itemsNotFound");
    }

    public static Message errorPartnerItemsNotFound(String player) {
        return format("trade.error.partnerItemsNotFound", "player", player);
    }

    public static Message errorNoSpace() {
        return format("trade.error.noSpace");
    }

    public static Message errorPartnerNoSpace(String player) {
        return format("trade.error.partnerNoSpace", "player", player);
    }

    public static Message errorWithdrawFailed() {
        return format("trade.error.withdrawFailed");
    }

    public static Message errorPartnerWithdrawFailed(String player) {
        return format("trade.error.partnerWithdrawFailed", "player", player);
    }

    public static Message errorDepositFailed() {
        return format("trade.error.depositFailed");
    }

    public static Message errorPartnerDepositFailed(String player) {
        return format("trade.error.partnerDepositFailed", "player", player);
    }

    public static Message errorSystemError(String reason) {
        return format("trade.error.systemError", "reason", reason);
    }

    public static Message errorAcceptFirst() {
        return format("trade.error.acceptFirst");
    }

    // ===== Disconnect =====

    public static Message disconnectCancelled() {
        return format("trade.disconnect.cancelled");
    }

    public static Message disconnectRequestCancelled() {
        return format("trade.disconnect.requestCancelled");
    }

    // ===== Test Mode =====

    public static Message testStarted() {
        return format("trade.test.started");
    }

    public static Message testNotAvailable() {
        return format("trade.test.notAvailable");
    }

    public static Message testHeader() {
        return format("trade.test.header");
    }

    public static Message testDescription() {
        return format("trade.test.description");
    }

    public static Message testCommands() {
        return format("trade.test.commands");
    }

    public static Message testAccept() {
        return format("trade.test.accept");
    }

    public static Message testConfirm() {
        return format("trade.test.confirm");
    }

    public static Message testCancel() {
        return format("trade.test.cancel");
    }

    public static Message testAutoAccept() {
        return format("trade.test.autoAccept");
    }

    public static Message testFailed() {
        return format("trade.test.failed");
    }

    // ===== Reload =====

    public static Message reloadSuccess() {
        return format("trade.reload.success");
    }

    public static Message reloadFailed(String reason) {
        return format("trade.reload.failed", "{reason}", reason);
    }

    // ===== UI =====

    public static Message uiOpened() {
        return format("trade.ui.opened");
    }

    public static Message uiOpenFailed() {
        return format("trade.ui.openFailed");
    }

    public static Message confirmSuccess() {
        return format("trade.confirm.success");
    }

    public static Message confirmTooEarly() {
        return format("trade.confirm.tooEarly");
    }

    public static Message confirmFailed() {
        return format("trade.confirm.failed");
    }

    // ===== UI Status (plain text for UI elements) =====

    public static String uiClickInstructions() {
        return getText("ui.status.clickInstructions");
    }

    public static String uiWaitingForPartner() {
        return getText("ui.status.waitingForPartner");
    }

    public static String uiPartnerAccepted() {
        return getText("ui.status.partnerAccepted");
    }

    public static String uiBothAccepted() {
        return getText("ui.status.bothAccepted");
    }

    public static String uiCountdownReady() {
        return getText("ui.status.countdownReady");
    }

    public static String uiModifiedInventory(String player) {
        return getText("ui.status.modifiedInventory", "player", player);
    }

    public static String uiPartnerModified() {
        return getText("ui.status.partnerModified");
    }

    public static String uiAcceptRevoked() {
        return getText("ui.status.acceptRevoked");
    }

    public static String uiAcceptRevokedManual() {
        return getText("ui.status.acceptRevokedManual");
    }

    public static String uiPartnerAcceptRevoked() {
        return getText("ui.status.partnerAcceptRevoked");
    }

    public static String uiNoItemsAvailable() {
        return getText("ui.status.noItemsAvailable");
    }

    public static String uiItemNotFound() {
        return getText("ui.status.itemNotFound");
    }

    public static String uiFailedToAdd() {
        return getText("ui.status.failedToAdd");
    }

    public static String uiNotEnoughSpace() {
        return getText("ui.status.notEnoughSpace");
    }

    public static String uiWaitMoreSeconds(long seconds) {
        return getText("ui.status.waitMoreSeconds", "seconds", String.valueOf(seconds));
    }

    public static String uiTradeCompleted() {
        return getText("ui.status.tradeCompleted");
    }

    public static String uiCancelledByPartner() {
        return getText("ui.status.cancelledByPartner");
    }

    public static String uiFailedValidation() {
        return getText("ui.status.failedValidation");
    }

    public static String uiCannotAcceptState() {
        return getText("ui.status.cannotAcceptState");
    }

    public static String uiNoActiveSession() {
        return getText("ui.status.noActiveSession");
    }

    // ===== UI Item Actions =====

    public static String actionAddedToOffer(int amount) {
        return getText("ui.action.addedToOffer", "amount", String.valueOf(amount));
    }

    public static String actionReturnedFromOffer(int amount) {
        return getText("ui.action.returnedFromOffer", "amount", String.valueOf(amount));
    }

    public static String actionRemovedFromOffer(String item) {
        return getText("ui.action.removedFromOffer", "item", item);
    }

    public static String actionReducedInOffer(String item, int amount) {
        return getText("ui.action.reducedInOffer", "item", item, "amount", String.valueOf(amount));
    }

    // ===== UI Element Text =====

    public static String uiTitleTrade() {
        return getText("ui.title.trade");
    }

    public static String uiLabelYourOffer() {
        return getText("ui.label.yourOffer");
    }

    public static String uiLabelPartnerOffer() {
        return getText("ui.label.partnerOffer");
    }

    public static String uiLabelYourInventory() {
        return getText("ui.label.yourInventory");
    }

    public static String uiLabelYourStatus() {
        return getText("ui.label.yourStatus");
    }

    public static String uiLabelPartnerStatus() {
        return getText("ui.label.partnerStatus");
    }

    public static String uiLabelTradingWith() {
        return getText("ui.label.tradingWith");
    }

    public static String uiLabelTestPartner() {
        return getText("ui.label.testPartner");
    }

    public static String uiLabelUnknown() {
        return getText("ui.label.unknown");
    }

    public static String uiButtonAccept() {
        return getText("ui.button.accept");
    }

    public static String uiButtonConfirm() {
        return getText("ui.button.confirm");
    }

    public static String uiButtonCancel() {
        return getText("ui.button.cancel");
    }

    public static String uiStatusAccepted() {
        return getText("ui.status.accepted");
    }

    public static String uiStatusNotAccepted() {
        return getText("ui.status.notAccepted");
    }

    public static String uiStatusAcceptedWaiting() {
        return getText("ui.status.acceptedWaiting");
    }

    public static String uiStatusReady() {
        return getText("ui.status.ready");
    }

    // ===== Helper Methods =====

    /**
     * Get formatted Message with color codes processed.
     */
    private static Message format(String key, String... replacements) {
        if (configManager == null) {
            return Message.raw("&cTradeMessages not initialized!");
        }
        String text = configManager.getMessage(key, replacements);
        return MessageFormatter.format(text);
    }

    /**
     * Get plain text (for UI elements that don't support color codes).
     */
    private static String getText(String key, String... replacements) {
        if (configManager == null) {
            return "TradeMessages not initialized!";
        }
        return MessageFormatter.stripColors(configManager.getMessage(key, replacements));
    }
}
