package com.ericversteeg;

import static net.runelite.api.ItemID.*;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;

public class ValueRemapper {

    public static Float remapPrice(int itemId, InventoryTotalPlugin plugin, InventoryTotalConfig config)
    {
        if (itemId == BRIMSTONE_KEY)
        {
            //doesn't include fish because of how complex it is
            float value = 
                    (5f/60f)*100000f +// coins
                    (5f/60f)*plugin.getPrice(UNCUT_DIAMOND)*30F +
                    (5f/60f)*plugin.getPrice(UNCUT_RUBY)*30F +
                    (5f/60f)*plugin.getPrice(COAL)*400F +
                    (4f/60f)*plugin.getPrice(GOLD_ORE)*150F +
                    (4f/60f)*plugin.getPrice(DRAGON_ARROWTIPS)*125F +
                    (3f/60f)*plugin.getPrice(IRON_ORE)*425F +
                    (3f/60f)*plugin.getPrice(RUNE_FULL_HELM)*3F +
                    (3f/60f)*plugin.getPrice(RUNE_PLATELEGS)*1.5F +
                    (3f/60f)*plugin.getPrice(RUNE_PLATEBODY)*1.5F +
                    (2f/60f)*plugin.getPrice(RUNITE_ORE)*12.5F +
                    (2f/60f)*plugin.getPrice(STEEL_BAR)*400F +
                    (2f/60f)*plugin.getPrice(DRAGON_DART_TIP)*100F +
                    (2f/60f)*plugin.getPrice(MAGIC_LOGS)*140F +
                    (1f/60f)*plugin.getPrice(PALM_TREE_SEED)*3F +
                    (1f/60f)*plugin.getPrice(MAGIC_SEED)*2.5F +
                    (1f/60f)*plugin.getPrice(CELASTRUS_SEED)*3F +
                    (1f/60f)*plugin.getPrice(DRAGONFRUIT_TREE_SEED)*3F +
                    (1f/60f)*plugin.getPrice(REDWOOD_TREE_SEED)*1F +
                    (1f/60f)*plugin.getPrice(TORSTOL_SEED)*4F +
                    (1f/60f)*plugin.getPrice(SNAPDRAGON_SEED)*4F +
                    (1f/60f)*plugin.getPrice(RANARR_SEED)*4F +
                    (1f/60f)*plugin.getPrice(PURE_ESSENCE)*4500F +
                    (1f/200f)*plugin.getPrice(DRAGON_HASTA)*1F +
                    (1f/1000f)*plugin.getPrice(MYSTIC_ROBE_TOP_DUSK)*1f +
                    (1f/1000f)*plugin.getPrice(MYSTIC_ROBE_BOTTOM_DUSK)*1f +
                    (1f/1000f)*plugin.getPrice(MYSTIC_GLOVES_DUSK)*1f +
                    (1f/1000f)*plugin.getPrice(MYSTIC_BOOTS_DUSK)*1f +
                    (1f/1000f)*plugin.getPrice(MYSTIC_HAT_DUSK)*1f;
            return value;
        }
        else if (itemId == TOKKUL)
        {
            switch(config.tokkulValue())
            {
                case NO_VALUE:
                default:
                    return 0f;
                //overstock price for runes since they hit overstock quickly. overstock price same with/without gloves.
                case BUY_CHAOS_RUNE:
                    return plugin.getPrice(CHAOS_RUNE) / 9f;
                case BUY_DEATH_RUNE:
                    return plugin.getPrice(DEATH_RUNE) / 18f;
                case SELL_OBBY_CAPE:
                    return plugin.getPrice(OBSIDIAN_CAPE) / (config.tokkulKaramjaGloves() ? 78000f : 90000f);
                case SELL_TOKTZ_KET_XIL:
                    return plugin.getPrice(TOKTZKETXIL) / (config.tokkulKaramjaGloves() ? 58500f : 67500f);
                case SELL_TOKTZ_MEJ_TAL:
                    return plugin.getPrice(TOKTZMEJTAL) / (config.tokkulKaramjaGloves() ? 45500f : 52500f);
                case SELL_UNCUT_ONYX:
                    return plugin.getPrice(UNCUT_ONYX) / (config.tokkulKaramjaGloves() ? 260000f : 300000f);
            }
        }
        else if (itemId == CRYSTAL_SHARD)
        {
            switch(config.crystalShardValue())
            {
                case NO_VALUE:
                default:
                    return 0f;
                case BUY_TELEPORT_SEED:
                    return plugin.getPrice(ENHANCED_CRYSTAL_TELEPORT_SEED) / 150f;
                case SELL_BASTION:
                    return (plugin.getPrice(DIVINE_BASTION_POTION4) - plugin.getPrice(BASTION_POTION4))/0.4f;
                case SELL_BATTLEMAGE:
                    return (plugin.getPrice(DIVINE_BATTLEMAGE_POTION4) - plugin.getPrice(BATTLEMAGE_POTION4))/0.4f;
                case SELL_MAGIC:
                    return (plugin.getPrice(DIVINE_MAGIC_POTION4) - plugin.getPrice(MAGIC_POTION4))/0.4f;
                case SELL_RANGING:
                    return (plugin.getPrice(DIVINE_RANGING_POTION4) - plugin.getPrice(RANGING_POTION4))/0.4f;
                case SELL_SUPER_ATTACK:
                    return (plugin.getPrice(DIVINE_SUPER_ATTACK_POTION4) - plugin.getPrice(SUPER_ATTACK4))/0.4f;
                case SELL_SUPER_COMBAT:
                    return (plugin.getPrice(DIVINE_SUPER_COMBAT_POTION4) - plugin.getPrice(SUPER_COMBAT_POTION4))/0.4f;
                case SELL_SUPER_DEFENCE:
                    return (plugin.getPrice(DIVINE_SUPER_DEFENCE_POTION4) - plugin.getPrice(SUPER_DEFENCE4))/0.4f;
                case SELL_SUPER_STRENGTH:
                    return (plugin.getPrice(DIVINE_SUPER_STRENGTH_POTION4) - plugin.getPrice(SUPER_STRENGTH4))/0.4f;
            }
        }
        else if (itemId == CRYSTAL_DUST_23964)
        {
            switch(config.crystalDustValue())
            {
                case NO_VALUE:
                default:
                    return 0f;
                case SELL_BASTION:
                    return (plugin.getPrice(DIVINE_BASTION_POTION4) - plugin.getPrice(BASTION_POTION4))/4f;
                case SELL_BATTLEMAGE:
                    return (plugin.getPrice(DIVINE_BATTLEMAGE_POTION4) - plugin.getPrice(BATTLEMAGE_POTION4))/4f;
                case SELL_MAGIC:
                    return (plugin.getPrice(DIVINE_MAGIC_POTION4) - plugin.getPrice(MAGIC_POTION4))/4f;
                case SELL_RANGING:
                    return (plugin.getPrice(DIVINE_RANGING_POTION4) - plugin.getPrice(RANGING_POTION4))/4f;
                case SELL_SUPER_ATTACK:
                    return (plugin.getPrice(DIVINE_SUPER_ATTACK_POTION4) - plugin.getPrice(SUPER_ATTACK4))/4f;
                case SELL_SUPER_COMBAT:
                    return (plugin.getPrice(DIVINE_SUPER_COMBAT_POTION4) - plugin.getPrice(SUPER_COMBAT_POTION4))/4f;
                case SELL_SUPER_DEFENCE:
                    return (plugin.getPrice(DIVINE_SUPER_DEFENCE_POTION4) - plugin.getPrice(SUPER_DEFENCE4))/4f;
                case SELL_SUPER_STRENGTH:
                    return (plugin.getPrice(DIVINE_SUPER_STRENGTH_POTION4) - plugin.getPrice(SUPER_STRENGTH4))/4f;
            }
        }
        else if (itemId == MERMAIDS_TEAR)
        {
            switch(config.mermaidsTearValue())
            {
                case NO_VALUE:
                default:
                    return 0f;
                case SELL_MERFOLK_TRIDENT:
                    return plugin.getPrice(MERFOLK_TRIDENT)/400f;
            }
        }

        return null;
    }

    @AllArgsConstructor
    public enum TokkulOverride {
        NO_VALUE("No Value (Default)"),
        BUY_CHAOS_RUNE      ("Chaos Rune (Buy)"),
        BUY_DEATH_RUNE      ("Death Rune (Buy)"),
        SELL_UNCUT_ONYX     ("Uncut Onyx (Sell)"),
        SELL_TOKTZ_MEJ_TAL  ("Toktz-mej-tal (Sell)"),
        SELL_TOKTZ_KET_XIL  ("Toktz-ket-xil (Sell)"),
        SELL_OBBY_CAPE      ("Obsidian cape (Sell)");
    
        private final String configName;
        @Override
        public String toString() { return configName; }
    }

    @AllArgsConstructor
    public enum CrystalShardOverride {
        NO_VALUE("No Value (Default)"),
        BUY_TELEPORT_SEED       ("Teleport Seed (Buy)"),
        SELL_SUPER_ATTACK       ("Super Attack (Sell)"),
        SELL_SUPER_STRENGTH     ("Super Strength (Sell)"),
        SELL_SUPER_DEFENCE      ("Super Defence (Sell)"),
        SELL_RANGING            ("Ranging (Sell)"),
        SELL_MAGIC              ("Magic (Sell)"),
        SELL_BASTION            ("Bastion (Sell)"),
        SELL_BATTLEMAGE         ("Battlemage (Sell)"),
        SELL_SUPER_COMBAT       ("Super Combat (Sell)");
    
        private final String configName;
        @Override
        public String toString() { return configName; }
    }

    @AllArgsConstructor
    public enum CrystalDustOverride {
        NO_VALUE("No Value (Default)"),
        SELL_SUPER_ATTACK       ("Super Attack (Sell)"),
        SELL_SUPER_STRENGTH     ("Super Strength (Sell)"),
        SELL_SUPER_DEFENCE      ("Super Defence (Sell)"),
        SELL_RANGING            ("Ranging (Sell)"),
        SELL_MAGIC              ("Magic (Sell)"),
        SELL_BASTION            ("Bastion (Sell)"),
        SELL_BATTLEMAGE         ("Battlemage (Sell)"),
        SELL_SUPER_COMBAT       ("Super Combat (Sell)");
    
        private final String configName;
        @Override
        public String toString() { return configName; }
    }

    @AllArgsConstructor
    public enum MermaidsTearOverride {
        NO_VALUE                    ("No Value (Default)"),
        SELL_MERFOLK_TRIDENT        ("Merfolk Trident");
    
        private final String configName;
        @Override
        public String toString() { return configName; }
    }

}
