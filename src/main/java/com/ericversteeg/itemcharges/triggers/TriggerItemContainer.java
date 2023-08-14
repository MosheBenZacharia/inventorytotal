//TODO: attribute tictac7x
package com.ericversteeg.itemcharges.triggers;

import javax.annotation.Nullable;

public class TriggerItemContainer {
    public final int inventory_id;

    public boolean increase_by_difference;

    @Nullable public String menu_target;
    @Nullable public String menu_option;
    @Nullable public Integer fixed_charges;

    public TriggerItemContainer(final int inventory_id) {
        this.inventory_id = inventory_id;
    }

    public TriggerItemContainer menuTarget(final String menu_target) {
        this.menu_target = menu_target;
        return this;
    }

    public TriggerItemContainer menuOption(final String menu_option) {
        this.menu_option = menu_option;
        return this;
    }

    public TriggerItemContainer fixedCharges(final int fixed_charges) {
        this.fixed_charges = fixed_charges;
        return this;
    }

    public TriggerItemContainer increaseByDifference() {
        this.increase_by_difference = true;
        return this;
    }
}
