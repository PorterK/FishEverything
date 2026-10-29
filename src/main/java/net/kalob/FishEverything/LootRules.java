package net.kalob.FishEverything;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Pure loot rules parsed from config: tier weights, item patterns, exclusions and
 * the Luck of the Sea bonus. Has no server dependency, so it is fully unit tested.
 */
public final class LootRules {

    private final Map<Rarity, Double> weights = new EnumMap<>(Rarity.class);
    private final Map<Rarity, Integer> maxAmounts = new EnumMap<>(Rarity.class);
    private final Map<Rarity, List<Glob>> patterns = new EnumMap<>(Rarity.class);
    private final List<Glob> excluded;
    private final double luckBonusPerLevel;

    public LootRules(Map<Rarity, Double> weights,
                     Map<Rarity, Integer> maxAmounts,
                     Map<Rarity, List<String>> patterns,
                     List<String> excluded,
                     double luckBonusPerLevel) {
        for (Rarity rarity : Rarity.values()) {
            double weight = weights.getOrDefault(rarity, 0.0);
            this.weights.put(rarity, Double.isFinite(weight) ? Math.max(0, weight) : 0);
            this.maxAmounts.put(rarity, Math.max(1, maxAmounts.getOrDefault(rarity, 1)));
            this.patterns.put(rarity, Glob.compileAll(patterns.getOrDefault(rarity, List.of())));
        }
        this.excluded = Glob.compileAll(excluded);
        this.luckBonusPerLevel = Double.isFinite(luckBonusPerLevel) ? Math.max(0, luckBonusPerLevel) : 0;
    }

    public static LootRules fromConfig(ConfigurationSection config) {
        Map<Rarity, Double> weights = new EnumMap<>(Rarity.class);
        Map<Rarity, Integer> maxAmounts = new EnumMap<>(Rarity.class);
        Map<Rarity, List<String>> patterns = new EnumMap<>(Rarity.class);
        for (Rarity rarity : Rarity.values()) {
            ConfigurationSection section = config.getConfigurationSection("rarities." + rarity.configKey());
            if (section == null) continue;
            weights.put(rarity, section.getDouble("weight", 0));
            maxAmounts.put(rarity, section.getInt("max-amount", 1));
            patterns.put(rarity, section.getStringList("items"));
        }
        return new LootRules(weights, maxAmounts, patterns,
                config.getStringList("exclude"),
                config.getDouble("luck-of-the-sea-bonus", 0.35));
    }

    /** The tier an item belongs to (rarest matching tier wins), or null if it's excluded. */
    public Rarity tierOf(String itemKey) {
        if (Glob.anyMatch(excluded, itemKey)) return null;
        Rarity[] all = Rarity.values();
        for (int i = all.length - 1; i > 0; i--) {
            if (Glob.anyMatch(patterns.get(all[i]), itemKey)) return all[i];
        }
        return Rarity.COMMON;
    }

    /** Weights used for a roll. Tiers with no items get 0; luck boosts every non-common tier. */
    public Map<Rarity, Double> effectiveWeights(Map<Rarity, Integer> itemCounts, int luckLevel) {
        Map<Rarity, Double> result = new EnumMap<>(Rarity.class);
        double multiplier = 1 + Math.max(0, luckLevel) * luckBonusPerLevel;
        for (Rarity rarity : Rarity.values()) {
            if (itemCounts.getOrDefault(rarity, 0) <= 0) {
                result.put(rarity, 0.0);
                continue;
            }
            double weight = weights.get(rarity);
            if (rarity != Rarity.COMMON) weight *= multiplier;
            result.put(rarity, weight);
        }
        return result;
    }

    /** Weighted pick of a tier. Returns null if every weight is zero. */
    public static Rarity pick(Map<Rarity, Double> weights, RandomGenerator random) {
        double total = 0;
        for (double w : weights.values()) total += w;
        if (!(total > 0)) return null;

        double roll = random.nextDouble() * total;
        Rarity last = null;
        for (Rarity rarity : Rarity.values()) {
            double w = weights.getOrDefault(rarity, 0.0);
            if (w <= 0) continue;
            last = rarity;
            roll -= w;
            if (roll < 0) return rarity;
        }
        return last; // floating-point edge case
    }

    public int maxAmount(Rarity rarity) {
        return maxAmounts.get(rarity);
    }

    public double weight(Rarity rarity) {
        return weights.get(rarity);
    }

    /** Patterns (tier or exclude) that match none of the given item keys, e.g. typos. */
    public List<String> unmatchedPatterns(Collection<String> itemKeys) {
        List<String> unmatched = new ArrayList<>();
        List<Glob> all = new ArrayList<>(excluded);
        patterns.values().forEach(all::addAll);
        for (Glob glob : all) {
            boolean hit = false;
            for (String key : itemKeys) {
                if (glob.matches(key)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) unmatched.add(glob.source());
        }
        return unmatched;
    }
}
