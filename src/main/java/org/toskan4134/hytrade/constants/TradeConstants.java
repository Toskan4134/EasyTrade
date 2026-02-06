package org.toskan4134.hytrade.constants;

/**
 * Constants for the HyTrade plugin.
 * Contains configuration values and hardcoded constants.
 */
public final class TradeConstants {

    private TradeConstants() {
        // Utility class - prevent instantiation
    }
    // ==== Permissions ====

    public static final String DEFAULT_PERMISSION = "hytrade.trade.";
    public static final String ADMIN_PERMISSION = "hytrade.admin.";

    // ===== Trade Configuration (loaded from TradeConfig) =====

    /**
     * Countdown duration after both accept (default: 3000ms = 3 seconds)
     */
    public static final int COUNTDOWN_DURATION_MS = 3000;

    /**
     * Request timeout in milliseconds (default: 30000ms = 30 seconds)
     */
    public static final int REQUEST_TIMEOUT_MS = 30000;

    /**
     * Whether it checks for updates or not (default: true = yes)
     */
    public static final boolean CHECK_FOR_UPDATES = true;

    /**
     * Whether it is on debug mode or not (default: false = no)
     */
    public static final boolean DEBUG = false;

    /**
     * Maximum concurrent trades per player
     */
    public static final int MAX_PENDING_REQUESTS = 1;

    /**
     * Default max stack size when we can't determine it
     */
    public static final int DEFAULT_MAX_STACK = 100;

    public static final int UPDATE_CHECK_INTERVAL_HOURS = 12;

    // ===== UI Constants =====

    /**
     * Status message reset delay in milliseconds
     */
    public static final long STATUS_RESET_DELAY_MS = 5000;

    /**
     * Countdown UI update interval in milliseconds
     */
    public static final long COUNTDOWN_UPDATE_INTERVAL_MS = 500;

    /**
     * Number of slots per row in inventory display
     */
    public static final int SLOTS_PER_ROW = 8;

    /**
     * Number of slots per row in offer display
     */
    public static final int SLOTS_PER_OFFER_ROW = 4;

    /**
     * Number of slots per row in partner offer display
     */
    public static final int SLOTS_PER_PARTNER_ROW = 5;

    // ===== Status Colors =====

    /**
     * Normal status message color (white/blue)
     */
    public static final String COLOR_NORMAL = "#8fa8b8";

    /**
     * Warning status message color (yellow)
     */
    public static final String COLOR_WARNING = "#ffcc00";

    /**
     * Error status message color (red)
     */
    public static final String COLOR_ERROR = "#f87171";

    /**
     * Success status message color (green)
     */
    public static final String COLOR_SUCCESS = "#44ff44";

    // ===== UI Paths =====

    /**
     * Main trading page UI layout
     */
    public static final String TRADING_PAGE_LAYOUT = "Pages/Toskan4134_Trading_TradingPage.ui";

    /**
     * Inventory slot UI component
     */
    public static final String INVENTORY_SLOT_UI = "Pages/Toskan4134_Trading_InventorySlot.ui";

    /**
     * Offer slot UI component
     */
    public static final String OFFER_SLOT_UI = "Pages/Toskan4134_Trading_OfferSlot.ui";

    /**
     * Partner slot UI component
     */
    public static final String PARTNER_SLOT_UI = "Pages/Toskan4134_Trading_PartnerSlot.ui";

    // ===== Event Action Keys =====

    /**
     * Action key for button events
     */
    public static final String KEY_ACTION = "Action";

    /**
     * Accept button action
     */
    public static final String ACTION_ACCEPT = "accept";

    /**
     * Confirm button action
     */
    public static final String ACTION_CONFIRM = "confirm";

    /**
     * Cancel button action
     */
    public static final String ACTION_CANCEL = "cancel";

    /**
     * Prefix for inventory slot actions
     */
    public static final String ACTION_INV_PREFIX = "inv_";

    /**
     * Prefix for offer slot actions
     */
    public static final String ACTION_OFFER_PREFIX = "offer_";

    // ===== Thread Pool Configuration =====

    /**
     * Number of threads for the trade manager scheduler
     */
    public static final int TRADE_MANAGER_THREADS = 2;

    /**
     * Scheduler shutdown timeout in seconds
     */
    public static final int SCHEDULER_SHUTDOWN_TIMEOUT_SEC = 5;
}
