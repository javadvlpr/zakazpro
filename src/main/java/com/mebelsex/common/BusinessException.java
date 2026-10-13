package com.mebelsex.common;

/**
 * Checked-style runtime exception for business-rule violations.
 * Carries a message key that can be resolved through i18n.
 */
public class BusinessException extends RuntimeException {

    private final String messageKey;

    public BusinessException(String messageKey) {
        super(messageKey);
        this.messageKey = messageKey;
    }

    public BusinessException(String messageKey, String detail) {
        super(detail);
        this.messageKey = messageKey;
    }

    public String getMessageKey() {
        return messageKey;
    }
}
