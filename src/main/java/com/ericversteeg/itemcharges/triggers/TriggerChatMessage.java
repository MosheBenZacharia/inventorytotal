//TODO: attribute tictac7x
package com.ericversteeg.itemcharges.triggers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class TriggerChatMessage {
    @Nonnull public final Pattern message;

    public boolean menu_target;
    public boolean equipped;
    public boolean inventory_same;
    public boolean inventory_count_same;
    public boolean multiple_charges;
    public boolean increase_dynamically;
    public boolean notification;

    @Nullable public Consumer<String> consumer;
    @Nullable public Integer fixed_charges;
    @Nullable public Integer decrease_charges;
    @Nullable public Integer increase_charges;
    @Nullable public String notification_message;

    public TriggerChatMessage(@Nonnull final String message) {
        this.message = Pattern.compile(message);
    }

    public TriggerChatMessage fixedCharges(final int charges) {
        this.fixed_charges = charges;
        return this;
    }

    public TriggerChatMessage onItemClick() {
        this.menu_target = true;
        return this;
    }

    public TriggerChatMessage increaseCharges(final int charges) {
        this.increase_charges = charges;
        return this;
    }

    public TriggerChatMessage decreaseCharges(final int charges) {
        this.decrease_charges = charges;
        return this;
    }

    public TriggerChatMessage equipped() {
        this.equipped = true;
        return this;
    }

    public TriggerChatMessage inventorySame() {
        this.inventory_same = true;
        return this;
    }

    public TriggerChatMessage inventoryCountSame() {
        this.inventory_count_same = true;
        return this;
    }

    public TriggerChatMessage extraConsumer(final Consumer<String> consumer) {
        this.consumer = consumer;
        return this;
    }

    public TriggerChatMessage multipleCharges() {
        this.multiple_charges = true;
        return this;
    }

    public TriggerChatMessage increaseDynamically() {
        this.increase_dynamically = true;
        return this;
    }

    public TriggerChatMessage notification() {
        this.notification = true;
        return this;
    }

    public TriggerChatMessage notification(final String message) {
        this.notification = true;
        this.notification_message = message;
        return this;
    }
}
