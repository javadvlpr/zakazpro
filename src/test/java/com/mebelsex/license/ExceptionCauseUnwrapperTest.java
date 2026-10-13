package com.mebelsex.license;

import com.mebelsex.common.BusinessException;
import com.mebelsex.ui.ExceptionHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The UI error handler must still find a wrapped business violation. */
class ExceptionCauseUnwrapperTest {

    @Test
    void findsADirectlyThrownBusinessException() {
        BusinessException business = new BusinessException("license.error.readonly");
        assertSame(business, ExceptionHandler.findBusinessException(business));
    }

    @Test
    void findsABusinessExceptionDeepInTheCauseChain() {
        BusinessException business = new BusinessException("license.error.readonly");
        Throwable wrapped = new IllegalStateException("flush failed",
                new RuntimeException("transaction", business));
        assertSame(business, ExceptionHandler.findBusinessException(wrapped));
    }

    @Test
    void returnsNullWhenNoBusinessExceptionIsPresent() {
        assertNull(ExceptionHandler.findBusinessException(new RuntimeException("oddiy xatolik")));
        assertNull(ExceptionHandler.findBusinessException(null));
    }

    @Test
    void keepsTheMessageKey() {
        BusinessException business = new BusinessException("license.error.readonly");
        Throwable wrapped = new RuntimeException("outer", business);
        assertEquals("license.error.readonly",
                ExceptionHandler.findBusinessException(wrapped).getMessageKey());
    }
}
