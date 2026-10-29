package net.kalob.FishEverything;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class LootRulesTest {

    static LootRules rules(double luckBonus) {
        Map<Rarity, Double> weights = new EnumMap<>(Rarity.class);
        weights.put(Rarity.COMMON, 60.0);
        weights.put(Rarity.UNCOMMON, 25.0);
        weights.put(Rarity.RARE, 10.0);
        weights.put(Rarity.EPIC, 4.0);
        weights.put(Rarity.LEGENDARY, 0.9);
        weights.put(Rarity.MYTHIC, 0.1);

        Map<Rarity, List<String>> patterns = new EnumMap<>(Rarity.class);
        patterns.put(Rarity.UNCOMMON, List.of("*iron*"));
        patterns.put(Rarity.RARE, List.of("*_ore", "*gold*"));
        patterns.put(Rarity.EPIC, List.of("*diamond*", "golden_apple"));
        patterns.put(Rarity.LEGENDARY, List.of("netherite_*", "elytra"));
        patterns.put(Rarity.MYTHIC, List.of("dragon_egg", "enchanted_golden_apple"));

        return new LootRules(weights, Map.of(Rarity.COMMON, 4), patterns,
                List.of("command_block", "barrier"), luckBonus);
    }

    static Map<Rarity, Integer> allTiersHaveItems() {
        Map<Rarity, Integer> counts = new EnumMap<>(Rarity.class);
        for (Rarity r : Rarity.values()) counts.put(r, 10);
        return counts;
    }

    @Test
    void unmatchedItemsAreCommon() {
        assertEquals(Rarity.COMMON, rules(0).tierOf("dirt"));
        assertEquals(Rarity.COMMON, rules(0).tierOf("poppy"));
    }

    @Test
    void rarestMatchingTierWins() {
        LootRules rules = rules(0);
        assertEquals(Rarity.UNCOMMON, rules.tierOf("iron_ingot"));
        assertEquals(Rarity.RARE, rules.tierOf("iron_ore"), "*_ore (rare) beats *iron* (uncommon)");
        assertEquals(Rarity.RARE, rules.tierOf("gold_ingot"));
        assertEquals(Rarity.EPIC, rules.tierOf("golden_apple"), "epic beats *gold* (rare)");
        assertEquals(Rarity.EPIC, rules.tierOf("deepslate_diamond_ore"), "epic beats *_ore (rare)");
        assertEquals(Rarity.MYTHIC, rules.tierOf("enchanted_golden_apple"));
        assertEquals(Rarity.LEGENDARY, rules.tierOf("netherite_sword"));
        assertEquals(Rarity.MYTHIC, rules.tierOf("dragon_egg"));
    }

    @Test
    void excludedItemsHaveNoTier() {
        assertNull(rules(0).tierOf("command_block"));
        assertNull(rules(0).tierOf("barrier"));
        assertNotNull(rules(0).tierOf("chain_command_block"));
    }

    @Test
    void luckOnlyBoostsNonCommonTiers() {
        LootRules rules = rules(0.5);
        Map<Rarity, Double> noLuck = rules.effectiveWeights(allTiersHaveItems(), 0);
        Map<Rarity, Double> luck3 = rules.effectiveWeights(allTiersHaveItems(), 3);
        assertEquals(noLuck.get(Rarity.COMMON), luck3.get(Rarity.COMMON));
        for (Rarity r : Rarity.values()) {
            if (r == Rarity.COMMON) continue;
            assertEquals(noLuck.get(r) * 2.5, luck3.get(r), 1e-9, r.name());
        }
    }

    @Test
    void negativeLuckIsIgnored() {
        LootRules rules = rules(0.5);
        assertEquals(rules.effectiveWeights(allTiersHaveItems(), 0), rules.effectiveWeights(allTiersHaveItems(), -5));
    }

    @Test
    void emptyTiersGetZeroWeight() {
        Map<Rarity, Integer> counts = allTiersHaveItems();
        counts.put(Rarity.MYTHIC, 0);
        assertEquals(0.0, rules(0).effectiveWeights(counts, 0).get(Rarity.MYTHIC));
    }

    @Test
    void badWeightsAreClamped() {
        Map<Rarity, Double> weights = new EnumMap<>(Rarity.class);
        weights.put(Rarity.COMMON, -10.0);
        weights.put(Rarity.RARE, Double.NaN);
        weights.put(Rarity.EPIC, Double.POSITIVE_INFINITY);
        weights.put(Rarity.MYTHIC, 1.0);
        LootRules rules = new LootRules(weights, Map.of(Rarity.COMMON, -3), Map.of(), List.of(), Double.NaN);
        assertEquals(0.0, rules.weight(Rarity.COMMON));
        assertEquals(0.0, rules.weight(Rarity.RARE));
        assertEquals(0.0, rules.weight(Rarity.EPIC));
        assertEquals(1.0, rules.weight(Rarity.MYTHIC));
        assertEquals(1, rules.maxAmount(Rarity.COMMON));

        Random random = new Random(1);
        for (int i = 0; i < 1000; i++) {
            assertEquals(Rarity.MYTHIC, LootRules.pick(rules.effectiveWeights(allTiersHaveItems(), 3), random));
        }
    }

    @Test
    void pickReturnsNullWhenNothingCanDrop() {
        Map<Rarity, Double> zero = new EnumMap<>(Rarity.class);
        for (Rarity r : Rarity.values()) zero.put(r, 0.0);
        assertNull(LootRules.pick(zero, new Random(1)));
        assertNull(LootRules.pick(Map.of(), new Random(1)));
    }

    @Test
    void pickNeverReturnsZeroWeightTier() {
        Map<Rarity, Double> weights = new EnumMap<>(Rarity.class);
        weights.put(Rarity.COMMON, 0.0);
        weights.put(Rarity.EPIC, 1.0);
        weights.put(Rarity.MYTHIC, 0.0);
        Random random = new Random(7);
        for (int i = 0; i < 10_000; i++) {
            assertEquals(Rarity.EPIC, LootRules.pick(weights, random));
        }
    }

    @Test
    void pickHandlesRandomAtUpperEdge() {
        Map<Rarity, Double> weights = new EnumMap<>(Rarity.class);
        weights.put(Rarity.COMMON, 1.0);
        weights.put(Rarity.RARE, 1.0);
        Random almostOne = new Random() {
            @Override
            public double nextDouble() {
                return Math.nextDown(1.0);
            }
        };
        assertEquals(Rarity.RARE, LootRules.pick(weights, almostOne));
    }

    @Test
    void unmatchedPatternsAreReported() {
        List<String> unmatched = rules(0).unmatchedPatterns(List.of("iron_ingot", "dirt", "command_block"));
        assertTrue(unmatched.contains("elytra"));
        assertTrue(unmatched.contains("barrier"));
        assertFalse(unmatched.contains("*iron*"));
        assertFalse(unmatched.contains("command_block"));
    }
}
