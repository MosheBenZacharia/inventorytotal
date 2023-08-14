//TODO: attribute tictac7x
package com.ericversteeg.itemcharges.triggers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TriggerAnimation {
    public final int animation_id;

    public int charges;
    public boolean decrease_charges;
    public boolean equipped;
    public boolean menu_target;

    @Nullable public int[] unallowed_items;
    @Nullable public String menu_option;

    public TriggerAnimation(final int animation_id) {
        this.animation_id = animation_id;
    }

    public TriggerAnimation increaseCharges(final int charges) {
        this.charges = charges;
        this.decrease_charges = false;
        return this;
    }

    public TriggerAnimation decreaseCharges(final int decharges) {
        this.charges = decharges;
        this.decrease_charges = true;
        return this;
    }

    public TriggerAnimation equipped() {
        this.equipped = true;
        return this;
    }

    public TriggerAnimation onItemClick() {
        this.menu_target = true;
        return this;
    }

    public TriggerAnimation unallowedItems(@Nonnull final int[] unallowed_items) {
        this.unallowed_items = unallowed_items;
        return this;
    }

    public TriggerAnimation onMenuOption(@Nonnull final String menu_option) {
        this.menu_option = menu_option;
        return this;
    }
}
