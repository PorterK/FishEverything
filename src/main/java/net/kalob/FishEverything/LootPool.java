package net.kalob.FishEverything;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.random.RandomGenerator;

/**
 * Items sorted into rarity tiers. Generic so it can be unit tested with plain strings
 * and used in game with {@link org.bukkit.Material}.
 */
public final class LootPool<T> {

    public record Roll<T>(Rarity rarity, T item) {}

    private final LootRules rules;
    private final Map<Rarity, List<T>> items = new EnumMap<>(Rarity.class);
    private final Map<Rarity, Integer> counts = new EnumMap<>(Rarity.class);

    public LootPool(LootRules rules, Collection<T> candidates, Function<T, String> keyOf) {
        this.rules = rules;
        for (Rarity rarity : Rarity.values()) items.put(rarity, new ArrayList<>());
        for (T candidate : candidates) {
            Rarity tier = rules.tierOf(keyOf.apply(candidate));
            if (tier != null) items.get(tier).add(candidate);
        }
        for (Rarity rarity : Rarity.values()) {
            items.put(rarity, Collections.unmodifiableList(items.get(rarity)));
            counts.put(rarity, items.get(rarity).size());
        }
    }

    public Roll<T> roll(int luckLevel, RandomGenerator random) {
        Rarity rarity = LootRules.pick(effectiveWeights(luckLevel), random);
        return rarity == null ? null : randomIn(rarity, random);
    }

    public Roll<T> randomIn(Rarity rarity, RandomGenerator random) {
        List<T> tier = items.get(rarity);
        if (tier.isEmpty()) return null;
        return new Roll<>(rarity, tier.get(random.nextInt(tier.size())));
    }

    public Map<Rarity, Double> effectiveWeights(int luckLevel) {
        return rules.effectiveWeights(counts, luckLevel);
    }

    public List<T> items(Rarity rarity) {
        return items.get(rarity);
    }

    public int count(Rarity rarity) {
        return counts.get(rarity);
    }

    public int total() {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public LootRules rules() {
        return rules;
    }
}
