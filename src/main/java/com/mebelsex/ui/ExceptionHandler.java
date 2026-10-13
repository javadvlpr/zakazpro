package com.mebelsex.ui;

import com.mebelsex.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import javafx.application.Platform;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

@Component
public class ExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ExceptionHandler.class);
    private final ResourceBundle bundle;

    public ExceptionHandler() {
        this.bundle = com.mebelsex.common.Messages.bundle();
    }

    /**
     * Walks the cause chain and returns the first {@link BusinessException}, or null.
     * Framework layers (Hibernate flush, Spring transactions, JavaFX tasks) often wrap
     * the original business violation, so the user must still see its Uzbek message.
     */
    public static BusinessException findBusinessException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof BusinessException business) {
                return business;
            }
            current = current.getCause();
        }
        return null;
    }

    public void handle(Throwable ex) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> handle(ex));
            return;
        }

        BusinessException businessException = findBusinessException(ex);
        if (businessException != null) {
            String messageKey = businessException.getMessageKey();
            String displayMessage;
            try {
                displayMessage = bundle.getString(messageKey);
            } catch (MissingResourceException e) {
                displayMessage = messageKey;
            }
            String errorTitle = bundle.getString("ui.error");
            AlertHelper.error(errorTitle, displayMessage);
        } else {
            log.error("Unhandled exception trapped in UI", ex);
            String unkTitle = bundle.getString("ui.error");
            String unkMsg = bundle.getString("ui.unexpected_error");
            AlertHelper.error(unkTitle, unkMsg);
        }
    }
}
