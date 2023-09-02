package com.ericversteeg.itemcharges.triggers;

import net.runelite.api.HitsplatID;
import net.runelite.api.Skill;

public class TriggerXPDrop {

    public final Skill skill;
    public final int discharges;

    public TriggerXPDrop(final Skill skill, final int discharges) {
        this.skill = skill;
        this.discharges = discharges;
    }
}
