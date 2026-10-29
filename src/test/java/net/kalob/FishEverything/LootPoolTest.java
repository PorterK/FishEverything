package net.kalob.FishEverything;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class LootPoolTest {

    private static final List<String> ITEMS = List.of(
            "dirt", "stone", "poppy", "oak_planks",
            "iron_ingot", "iron_sword",
            "gold_ingot", "coal_ore",
            "diamond", "diamond_sword", "golden_apple",
            "netherite_ingot", "elytra",
            "dragon_egg", "enchanted_golden_apple",
            "command_block", "barrier");

    private static LootPool<String> pool(double luckBonus) {
        return new LootPool<>(LootRulesTest.rules(luckBonus), ITEMS, Function.identity());
    }

    @Test
    void itemsAreSortedIntoTiers() {
        LootPool<String> pool = pool(0);
        assertEquals(List.of("dirt", "stone", "poppy", "oak_planks"), pool.items(Rarity.COMMON));
        assertEquals(List.of("dragon_egg", "enchanted_golden_apple"), pool.items(Rarity.MYTHIC));
        assertEquals(ITEMS.size() - 2, pool.total(), "excluded items are not in the pool");
    }

    @Test
    void tierListsAreReadOnly() {
        assertThrows(UnsupportedOperationException.class, () -> pool(0).items(Rarity.COMMON).add("hacked"));
    }

    @Test
    void excludedItemsAreNeverRolled() {
        LootPool<String> pool = pool(0);
        Random random = new Random(3);
        for (int i = 0; i < 100_000; i++) {
            LootPool.Roll<String> roll = pool.roll(i % 4, random);
            assertNotNull(roll);
            assertNotEquals("command_block", roll.item());
            assertNotEquals("barrier", roll.item());
            assertTrue(pool.items(roll.rarity()).contains(roll.item()), "item must belong to its rolled tier");
        }
    }

    @Test
    void everyItemIsReachable() {
        LootPool<String> pool = pool(0);
        Random random = new Random(11);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 300_000; i++) seen.add(pool.roll(0, random).item());
        List<String> expected = new ArrayList<>(ITEMS);
        expected.removeAll(List.of("command_block", "barrier"));
        assertEquals(new HashSet<>(expected), seen);
    }

    @Test
    void rollFrequenciesMatchWeights() {
        for (int luck : new int[]{0, 3}) {
            LootPool<String> pool = pool(0.35);
            Random random = new Random(42 + luck);
            int n = 500_000;
            Map<Rarity, Integer> seen = new EnumMap<>(Rarity.class);
            for (int i = 0; i < n; i++) seen.merge(pool.roll(luck, random).rarity(), 1, Integer::sum);

            Map<Rarity, Double> weights = pool.effectiveWeights(luck);
            double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
            for (Rarity rarity : Rarity.values()) {
                double p = weights.get(rarity) / total;
                double expected = n * p;
                double sigma = Math.sqrt(n * p * (1 - p));
                int actual = seen.getOrDefault(rarity, 0);
                assertTrue(Math.abs(actual - expected) <= 5 * sigma + 1,
                        "luck " + luck + " " + rarity + ": expected ~" + expected + " got " + actual);
            }
        }
    }

    @Test
    void luckMakesRareCatchesMoreLikely() {
        LootPool<String> pool = pool(0.35);
        int n = 200_000;
        int rareNoLuck = 0;
        int rareLuck = 0;
        Random a = new Random(5);
        Random b = new Random(5);
        for (int i = 0; i < n; i++) {
            if (pool.roll(0, a).rarity().atLeast(Rarity.RARE)) rareNoLuck++;
            if (pool.roll(3, b).rarity().atLeast(Rarity.RARE)) rareLuck++;
        }
        assertTrue(rareLuck > rareNoLuck * 1.5, "Luck III should make rare+ much more common");
    }

    @Test
    void emptyTierIsSkippedNotNull() {
        LootPool<String> pool = new LootPool<>(LootRulesTest.rules(0), List.of("dirt", "elytra"), Function.identity());
        Random random = new Random(9);
        for (int i = 0; i < 50_000; i++) {
            LootPool.Roll<String> roll = pool.roll(3, random);
            assertNotNull(roll);
            assertTrue(roll.rarity() == Rarity.COMMON || roll.rarity() == Rarity.LEGENDARY);
        }
        assertNull(pool.randomIn(Rarity.MYTHIC, random));
    }

    @Test
    void emptyPoolRollsNull() {
        LootPool<String> pool = new LootPool<>(LootRulesTest.rules(0), List.of(), Function.identity());
        assertNull(pool.roll(0, new Random(1)));
        assertEquals(0, pool.total());
    }
}
