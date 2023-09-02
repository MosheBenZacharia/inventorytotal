package com.ericversteeg.itemcharges.triggers;

import net.runelite.api.Skill;
import net.runelite.api.TileItem;

import java.util.function.Consumer;

import lombok.NonNull;

public class TriggerItemDespawn {

    public final Consumer<TileItem> consumer;

    public TriggerItemDespawn(final Consumer<TileItem> consumer) {
        this.consumer = consumer;
    }
}
