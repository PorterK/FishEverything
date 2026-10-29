package net.kalob.FishEverything;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/** The in-game loot table: every obtainable {@link Material}, sorted by {@link LootRules}. */
public final class LootTable {

    private LootPool<Material> pool;
    private boolean randomEnchantedBooks;

    public void load(FileConfiguration config, Logger logger) {
        LootRules rules = LootRules.fromConfig(config);
        randomEnchantedBooks = config.getBoolean("random-enchanted-books", true);

        List<Material> materials = obtainableMaterials();
        pool = new LootPool<>(rules, materials, LootTable::key);

        List<String> unmatched = rules.unmatchedPatterns(materials.stream().map(LootTable::key).toList());
        if (!unmatched.isEmpty()) {
            logger.warning("These config patterns match no items (typo?): " + unmatched);
        }

        StringBuilder summary = new StringBuilder("Loaded ").append(pool.total()).append(" fishable items:");
        for (Rarity rarity : Rarity.values()) {
            summary.append(' ').append(rarity.configKey()).append('=').append(pool.count(rarity));
        }
        logger.info(summary.toString());
    }

    public static String key(Material material) {
        return material.name().toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("deprecation")
    public static List<Material> obtainableMaterials() {
        List<Material> result = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.name().startsWith("LEGACY_")) continue;
            if (!material.isItem() || material.isAir()) continue;
            result.add(material);
        }
        return result;
    }

    public LootPool.Roll<Material> roll(int luckLevel) {
        return pool.roll(luckLevel, ThreadLocalRandom.current());
    }

    public LootPool.Roll<Material> randomIn(Rarity rarity) {
        return pool.randomIn(rarity, ThreadLocalRandom.current());
    }

    public Map<Rarity, Double> effectiveWeights(int luckLevel) {
        return pool.effectiveWeights(luckLevel);
    }

    public int count(Rarity rarity) {
        return pool.count(rarity);
    }

    public List<Material> items(Rarity rarity) {
        return pool.items(rarity);
    }

    public LootPool<Material> pool() {
        return pool;
    }

    public ItemStack createStack(LootPool.Roll<Material> roll) {
        Material material = roll.item();
        int max = Math.min(pool.rules().maxAmount(roll.rarity()), Math.max(1, material.getMaxStackSize()));
        int amount = max <= 1 ? 1 : ThreadLocalRandom.current().nextInt(1, max + 1);
        ItemStack stack = new ItemStack(material, amount);

        if (randomEnchantedBooks && material == Material.ENCHANTED_BOOK) {
            addRandomStoredEnchant(stack);
        }
        return stack;
    }

    private static void addRandomStoredEnchant(ItemStack stack) {
        if (!(stack.getItemMeta() instanceof EnchantmentStorageMeta meta)) return;
        Registry<Enchantment> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        List<Enchantment> all = registry.stream().toList();
        if (all.isEmpty()) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Enchantment enchantment = all.get(random.nextInt(all.size()));
        int level = random.nextInt(1, Math.max(1, enchantment.getMaxLevel()) + 1);
        meta.addStoredEnchant(enchantment, level, true);
        stack.setItemMeta(meta);
    }
}
