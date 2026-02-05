package org.toskan4134.hytrade.trade;

/**
 * Represents the current state of a trade session.
 */
public enum TradeState {
    /**
     * Trade request has been sent, waiting for acceptance.
     */
    PENDING_REQUEST,

    /**
     * Both players are in the trade, can modify offers.
     */
    NEGOTIATING,

    /**
     * One player has accepted, waiting for the other.
     */
    ONE_ACCEPTED,

    /**
     * Both players accepted, countdown started (3 seconds).
     */
    BOTH_ACCEPTED_COUNTDOWN,

    /**
     * Trade is being executed (atomic transaction).
     */
    EXECUTING,

    /**
     * Trade completed successfully.
     */
    COMPLETED,

    /**
     * Trade was cancelled by one of the players.
     */
    CANCELLED,

    /**
     * Trade failed (e.g., inventory full, items changed).
     */
    FAILED
}
