//TODO: attribute tictac7x
package com.ericversteeg.itemcharges.triggers;

import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TriggerMenuOption {
    @Nullable public final String target;
    @Nonnull public final String option;

    @Nullable public Integer charges;
    @Nullable public Consumer<String> consumer;

    public TriggerMenuOption(@Nonnull final String target, @Nonnull final String option) {
        this.target = target;
        this.option = option;
    }

    public TriggerMenuOption(@Nonnull final String option) {
        this.target = null;
        this.option = option;
    }

    public TriggerMenuOption fixedCharges(final int charges) {
        this.charges = charges;
        return this;
    }

    public TriggerMenuOption extraConsumer(final Consumer<String> consumer) {
        this.consumer = consumer;
        return this;
    }
}
