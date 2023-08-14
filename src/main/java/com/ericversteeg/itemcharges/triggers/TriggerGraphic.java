//TODO: attribute tictac7x
package com.ericversteeg.itemcharges.triggers;

import javax.annotation.Nullable;

public class TriggerGraphic {
    public final int graphic_id;

    public int charges;
    public boolean decrease_charges;
    public boolean equipped;

    @Nullable public int[] unallowed_items;
    @Nullable public String menu_option;

    public TriggerGraphic(final int graphic_id) {
        this.graphic_id = graphic_id;
    }

    public TriggerGraphic increaseCharges(final int charges) {
        this.charges = charges;
        this.decrease_charges = false;
        return this;
    }

    public TriggerGraphic decreaseCharges(final int decharges) {
        this.charges = decharges;
        this.decrease_charges = true;
        return this;
    }

    public TriggerGraphic equipped() {
        this.equipped = true;
        return this;
    }
}
