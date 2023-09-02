//TODO: attribute tictac7x
package com.ericversteeg.itemcharges.triggers;

import javax.annotation.Nullable;

public class TriggerItem {
    public final int item_id;
    public final boolean is_open_container;

    @Nullable public Integer fixed_charges;
    @Nullable public Integer max_charges;

    public TriggerItem(final int item_id) {
        this.item_id = item_id;
        this.is_open_container = false;
    }

    public TriggerItem(final int item_id, final boolean is_open_container) {
        this.item_id = item_id;
        this.is_open_container = is_open_container;
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
