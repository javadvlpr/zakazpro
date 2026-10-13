package com.mebelsex.ui;

import org.springframework.stereotype.Component;

/**
 * Spring-injectable enum label mapper. The logic lives in
 * {@link com.mebelsex.ui.components.EnumLabels} so there is a single implementation.
 */
@Component
public class EnumLabels {

    public String getLabel(Enum<?> enumValue) {
        return com.mebelsex.ui.components.EnumLabels.getLabel(enumValue);
    }
}
