package net.kalob.FishEverything;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GlobTest {

    @Test
    void exactMatch() {
        Glob glob = Glob.of("elytra");
        assertTrue(glob.matches("elytra"));
        assertFalse(glob.matches("elytra_wings"));
        assertFalse(glob.matches("my_elytra"));
    }

    @Test
    void suffixWildcard() {
        Glob glob = Glob.of("netherite_*");
        assertTrue(glob.matches("netherite_sword"));
        assertTrue(glob.matches("netherite_"));
        assertFalse(glob.matches("netherite"));
        assertFalse(glob.matches("ancient_netherite_sword"));
    }

    @Test
    void prefixWildcard() {
        Glob glob = Glob.of("*_ore");
        assertTrue(glob.matches("iron_ore"));
        assertTrue(glob.matches("deepslate_diamond_ore"));
        assertFalse(glob.matches("ore_block"));
        assertFalse(glob.matches("raw_iron"));
    }

    @Test
    void containsWildcard() {
        Glob glob = Glob.of("*diamond*");
        assertTrue(glob.matches("diamond"));
        assertTrue(glob.matches("diamond_sword"));
        assertTrue(glob.matches("deepslate_diamond_ore"));
        assertFalse(glob.matches("emerald"));
    }

    @Test
    void starMatchesEverything() {
        assertTrue(Glob.of("*").matches("anything_at_all"));
    }

    @Test
    void caseAndNamespaceInsensitive() {
        Glob glob = Glob.of("  minecraft:Diamond_SWORD ");
        assertTrue(glob.matches("diamond_sword"));
        assertTrue(glob.matches("DIAMOND_SWORD"));
        assertEquals("minecraft:Diamond_SWORD", glob.source());
    }

    @Test
    void regexCharactersAreLiteral() {
        Glob glob = Glob.of("a.b+c");
        assertTrue(glob.matches("a.b+c"));
        assertFalse(glob.matches("axbbc"));
        assertDoesNotThrow(() -> Glob.of("[(bad regex"));
        assertTrue(Glob.of("[(bad regex").matches("[(bad regex"));
    }

    @Test
    void blankAndNullIgnored() {
        assertNull(Glob.of(null));
        assertNull(Glob.of("   "));
        assertNull(Glob.of("minecraft:"));
        assertEquals(1, Glob.compileAll(List.of("", " ", "dirt")).size());
    }

    @Test
    void anyMatch() {
        List<Glob> globs = Glob.compileAll(List.of("dirt", "*_log"));
        assertTrue(Glob.anyMatch(globs, "oak_log"));
        assertTrue(Glob.anyMatch(globs, "dirt"));
        assertFalse(Glob.anyMatch(globs, "stone"));
        assertFalse(Glob.anyMatch(List.of(), "stone"));
    }
}
