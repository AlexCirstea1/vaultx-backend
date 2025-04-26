package com.vaultx.user.context.model.messaging;

/**
 * Enum used instead of bare strings.  Maps to VARCHAR in the DB.
 */
public enum ChatRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    EXPIRED,
    BLOCKED
}
