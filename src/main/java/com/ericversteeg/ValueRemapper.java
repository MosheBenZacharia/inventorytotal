package com.ericversteeg;

import static net.runelite.api.ItemID.*;

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
        else if(itemId == TOKKUL)
        {
            switch(config.tokkulValue())
            {
                case NO_VALUE:
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
                default:
                    return 0f;
            }
        }
        //careful with recursive loop of crystal shards (since the chest can have crystal shards)

        return null;
    }

    public enum TokkulOverride {
        NO_VALUE("No Value (Default)"),
        BUY_CHAOS_RUNE      ("Chaos Rune (Buy)"),
        BUY_DEATH_RUNE      ("Death Rune (Buy)"),
        SELL_UNCUT_ONYX     ("Uncut Onyx (Sell)"),
        SELL_TOKTZ_MEJ_TAL  ("Toktz-mej-tal (Sell)"),
        SELL_TOKTZ_KET_XIL  ("Toktz-ket-xil (Sell)"),
        SELL_OBBY_CAPE      ("Obsidian cape (Sell)");
    
        private final String description;
    
        // Constructor to initialize the description for each enum constant
        private TokkulOverride(String description) {
            this.description = description;
        }
    
        // Override the toString() method to return the custom string representation
        @Override
        public String toString() {
            return description;
        }
    }

}
