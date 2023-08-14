//TODO: attribute tictac7x
package com.ericversteeg.itemcharges.triggers;

import javax.annotation.Nullable;

public class TriggerItem {
    public final int item_id;

    @Nullable public Integer fixed_charges;
    @Nullable public Integer max_charges;

    public TriggerItem(final int item_id) {
        this.item_id = item_id;
    }

    public TriggerItem fixedCharges(final int charges) {
        this.fixed_charges = charges;
        return this;
    }

    public TriggerItem maxCharges(final int charges) {
        this.max_charges = charges;
        return this;
    }
}
