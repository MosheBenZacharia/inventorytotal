//TODO: attribute tictac7x
package com.ericversteeg.itemcharges.triggers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

public class TriggerWidget {
    public static final int WIDGET_DEFAULT_GROUP_ID = 193;
    public static final int WIDGET_DEFAULT_CHILD_ID = 2;

    public static final int WIDGET_NPC_GROUP_ID = 231;
    public static final int WIDGET_NPC_CHILD_ID = 6;

    @Nonnull public final String message;

    public int group_id;
    public int child_id;
    public Integer sub_child_id;

    public boolean increase_dynamically;

    @Nullable public Consumer<String> consumer;
    @Nullable public Integer charges;

    public TriggerWidget(final int group_id, final int child_id, @Nonnull final String message) {
        this.group_id = group_id;
        this.child_id = child_id;
        this.message = message;
    }

    public TriggerWidget(final int group_id, final int child_id, final int sub_child_id, @Nonnull final String message) {
        this.group_id = group_id;
        this.child_id = child_id;
        this.sub_child_id = sub_child_id;
        this.message = message;
    }

    public TriggerWidget fixedCharges(final int charges) {
        this.charges = charges;
        return this;
    }

    public TriggerWidget extraConsumer(@Nonnull final Consumer<String> consumer) {
        this.consumer = consumer;
        return this;
    }

    public TriggerWidget increaseDynamically() {
        this.increase_dynamically = true;
        return this;
    }
}
