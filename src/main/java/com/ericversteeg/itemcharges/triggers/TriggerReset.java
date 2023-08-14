//TODO: attribute tictac7x
package com.ericversteeg.itemcharges.triggers;

import javax.annotation.Nullable;

public class TriggerReset {
    public final int charges;

    @Nullable public Integer item_id;

    public TriggerReset(final int charges) {
        this.charges = charges;
    }

    public TriggerReset dynamicItem(final int item_id) {
        this.item_id = item_id;
        return this;
    }
}
