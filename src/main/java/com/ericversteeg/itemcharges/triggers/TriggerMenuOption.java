//TODO: attribute tictac7x
package com.ericversteeg.itemcharges.triggers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TriggerMenuOption {
    @Nullable public final String target;
    @Nonnull public final String option;

    @Nonnull public Integer charges;

    public TriggerMenuOption(@Nonnull final String target, @Nonnull final String option, final int charges) {
        this.target = target;
        this.option = option;
        this.charges = charges;
    }

    public TriggerMenuOption(@Nonnull final String option, final int charges) {
        this.target = null;
        this.option = option;
        this.charges = charges;
    }
}
