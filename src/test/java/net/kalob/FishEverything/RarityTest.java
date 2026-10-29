package net.kalob.FishEverything;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RarityTest {

    @Test
    void parseIsForgiving() {
        assertEquals(Rarity.MYTHIC, Rarity.parse("mythic"));
        assertEquals(Rarity.EPIC, Rarity.parse("  EpIc "));
        assertNull(Rarity.parse("nope"));
        assertNull(Rarity.parse(""));
        assertNull(Rarity.parse(null));
    }

    @Test
    void orderingIsCommonToMythic() {
        assertTrue(Rarity.MYTHIC.atLeast(Rarity.LEGENDARY));
        assertTrue(Rarity.EPIC.atLeast(Rarity.EPIC));
        assertFalse(Rarity.RARE.atLeast(Rarity.EPIC));
        assertEquals(Rarity.COMMON, Rarity.values()[0]);
        assertEquals(Rarity.MYTHIC, Rarity.values()[Rarity.values().length - 1]);
    }

    @Test
    void configKeysAreLowercase() {
        for (Rarity r : Rarity.values()) assertEquals(r.name().toLowerCase(), r.configKey());
    }
}
