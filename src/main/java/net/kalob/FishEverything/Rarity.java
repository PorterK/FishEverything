package net.kalob.FishEverything;

import java.util.Locale;

/**
 * Rarity tiers, ordered from most common to rarest. Each tier has its own catch animation.
 */
public enum Rarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY,
    MYTHIC;

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean atLeast(Rarity other) {
        return ordinal() >= other.ordinal();
    }

    public static Rarity parse(String input) {
        if (input == null) return null;
        try {
            return valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
