package net.kalob.FishEverything;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * In-server integration test, run by CI against a real Paper server:
 * {@code java -Dfisheverything.selftest=true -jar paper.jar}.
 *
 * <p>It exercises every code path that runs during real fishing (except the
 * PlayerFishEvent itself, which needs a connected client), records failures,
 * writes {@code plugins/FishEverything/selftest-result.txt}, and shuts the
 * server down.</p>
 */
final class SelfTest {

    static final String PROPERTY = "fisheverything.selftest";
    private static final int MAX_WAIT_TICKS = 20 * 60;

    private final FishEverythingPlugin plugin;
    private final List<String> failures = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private final List<Item> testItems = new ArrayList<>();
    private Location origin;
    private int waited;

    SelfTest(FishEverythingPlugin plugin) {
        this.plugin = plugin;
    }

    void start() {
        // Wait until the server has fully started and is ticking.
        Bukkit.getScheduler().runTaskLater(plugin, this::runChecks, 40L);
    }

    private void runChecks() {
        log("Starting self-test");
        step("prepare world", this::prepareWorld);
        step("config reload", this::checkReload);
        step("every item can be created", this::checkEveryItem);
        step("enchanted books", this::checkEnchantedBooks);
        step("roll distribution", this::checkRollDistribution);
        step("commands", this::checkCommands);
        step("every animation", this::playEveryAnimation);
        step("rapid catches", this::simulateRapidCatches);
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            waited += 20;
            if (plugin.effects().activeAnimations() == 0 || waited >= MAX_WAIT_TICKS) {
                task.cancel();
                // Give the last fireworks time to detonate.
                Bukkit.getScheduler().runTaskLater(plugin, this::finish, 100L);
            }
        }, 20L, 20L);
    }

    private void prepareWorld() {
        World world = Bukkit.getWorlds().getFirst();
        origin = world.getSpawnLocation().add(0.5, 1, 0.5);
        Chunk chunk = origin.getChunk();
        world.setChunkForceLoaded(chunk.getX(), chunk.getZ(), true);
        notes.add("world=" + world.getName() + " origin=" + fmt(origin));
    }

    private void checkReload() {
        for (int i = 0; i < 3; i++) plugin.reload();
        int total = plugin.lootTable().pool().total();
        expect(total > 500, "expected > 500 fishable items, got " + total);
        for (Rarity rarity : Rarity.values()) {
            expect(plugin.lootTable().count(rarity) > 0, "tier " + rarity.configKey() + " has no items");
        }
        expect(plugin.lootTable().items(Rarity.MYTHIC).contains(Material.DRAGON_EGG), "dragon_egg should be mythic");
        for (Rarity rarity : Rarity.values()) {
            expect(!plugin.lootTable().items(rarity).contains(Material.COMMAND_BLOCK), "command_block must be excluded");
            expect(!plugin.lootTable().items(rarity).contains(Material.AIR), "air must not be fishable");
        }
        notes.add("fishable items=" + total);
    }

    private void checkEveryItem() {
        int created = 0;
        for (Rarity rarity : Rarity.values()) {
            for (Material material : plugin.lootTable().items(rarity)) {
                try {
                    ItemStack stack = plugin.lootTable().createStack(new LootPool.Roll<>(rarity, material));
                    expect(stack != null && stack.getType() == material, "wrong stack for " + material);
                    expect(stack.getAmount() >= 1 && stack.getAmount() <= Math.max(1, material.getMaxStackSize()),
                            "bad amount " + stack.getAmount() + " for " + material);
                    expect(stack.displayName() != null, "no display name for " + material);
                    created++;
                } catch (Exception | LinkageError e) {
                    fail("creating " + material + " threw " + e);
                }
            }
        }
        notes.add("items created=" + created);
    }

    private void checkEnchantedBooks() {
        LootPool.Roll<Material> roll = new LootPool.Roll<>(Rarity.EPIC, Material.ENCHANTED_BOOK);
        for (int i = 0; i < 200; i++) {
            ItemStack book = plugin.lootTable().createStack(roll);
            if (!(book.getItemMeta() instanceof EnchantmentStorageMeta meta) || !meta.hasStoredEnchants()) {
                fail("enchanted book had no stored enchantment");
                return;
            }
        }
    }

    private void checkRollDistribution() {
        LootPool<Material> pool = plugin.lootTable().pool();
        Random random = new Random(42);
        for (int luck : new int[]{0, 3}) {
            int n = 200_000;
            Map<Rarity, Integer> seen = new EnumMap<>(Rarity.class);
            for (int i = 0; i < n; i++) {
                LootPool.Roll<Material> roll = pool.roll(luck, random);
                if (roll == null) {
                    fail("roll returned null (luck " + luck + ")");
                    return;
                }
                seen.merge(roll.rarity(), 1, Integer::sum);
            }
            Map<Rarity, Double> weights = pool.effectiveWeights(luck);
            double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
            StringBuilder line = new StringBuilder("luck " + luck + ":");
            for (Rarity rarity : Rarity.values()) {
                double p = weights.get(rarity) / total;
                double expected = n * p;
                int actual = seen.getOrDefault(rarity, 0);
                double tolerance = 6 * Math.sqrt(n * p * (1 - p)) + 5;
                expect(Math.abs(actual - expected) <= tolerance, String.format(Locale.ROOT,
                        "luck %d %s: expected ~%.0f got %d", luck, rarity.configKey(), expected, actual));
                line.append(' ').append(rarity.configKey()).append('=').append(actual);
            }
            notes.add(line.toString());
        }
    }

    private void checkCommands() {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        for (String command : new String[]{
                "fisheverything", "fe odds", "fe reload", "fe test epic", "fe test nope", "fe test", "fe bogus"}) {
            try {
                boolean handled = Bukkit.dispatchCommand(console, command);
                expect(handled, "command not handled: /" + command);
            } catch (Exception | LinkageError e) {
                fail("/" + command + " threw " + e);
            }
        }
    }

    private void playEveryAnimation() {
        int i = 0;
        for (Rarity rarity : Rarity.values()) {
            Location spot = origin.clone().add(i++ * 3, 0, 0);
            EffectTarget target = EffectTarget.at(spot, Bukkit.getConsoleSender(), "SelfTest-" + rarity.configKey());
            LootPool.Roll<Material> roll = plugin.lootTable().randomIn(rarity);
            ItemStack stack = plugin.lootTable().createStack(roll);
            plugin.effects().play(target, spot, rarity, stack);
        }
    }

    /** 60 catches in a single tick, like a busy AFK fishing farm. */
    private void simulateRapidCatches() {
        World world = origin.getWorld();
        Map<Rarity, Integer> tiers = new EnumMap<>(Rarity.class);
        for (int i = 0; i < 60; i++) {
            Item item = world.dropItem(origin, new ItemStack(Material.COD));
            testItems.add(item);
            EffectTarget target = EffectTarget.at(origin, Bukkit.getConsoleSender(), "SelfTest-farm");
            LootPool.Roll<Material> roll = plugin.applyCatch(item, target, i % 4);
            if (roll == null) {
                fail("applyCatch returned null");
                continue;
            }
            expect(item.getItemStack().getType() == roll.item(), "caught item was not replaced");
            tiers.merge(roll.rarity(), 1, Integer::sum);
        }
        // Force every tier through applyCatch too.
        for (Rarity rarity : Rarity.values()) {
            Item item = world.dropItem(origin, new ItemStack(Material.COD));
            testItems.add(item);
            LootPool.Roll<Material> roll = plugin.lootTable().randomIn(rarity);
            ItemStack stack = plugin.lootTable().createStack(roll);
            item.setItemStack(stack);
            plugin.effects().play(EffectTarget.at(origin, Bukkit.getConsoleSender(), "SelfTest-tier"), origin, rarity, stack);
        }
        notes.add("rapid catch tiers=" + tiers);
    }

    private void finish() {
        CatchEffects effects = plugin.effects();
        expect(effects.errorCount() == 0, effects.errorCount() + " catch effect error(s) were logged");
        expect(effects.activeAnimations() == 0,
                effects.activeAnimations() + " animation(s) still running after " + waited / 20 + "s");
        expect(effects.peakAnimations() <= effects.maxConcurrentAnimations() + Rarity.values().length,
                "animation cap not respected: peak " + effects.peakAnimations());
        notes.add("peak concurrent animations=" + effects.peakAnimations()
                + " (cap " + effects.maxConcurrentAnimations() + "), skipped=" + effects.skippedAnimations());

        int leftover = 0;
        for (Firework firework : origin.getWorld().getEntitiesByClass(Firework.class)) {
            if (firework.getPersistentDataContainer().has(effects.harmlessFireworkKey(), PersistentDataType.BYTE)) {
                leftover++;
                firework.remove();
            }
        }
        expect(leftover == 0, leftover + " celebration firework(s) were left in the world");
        testItems.forEach(Item::remove);

        double[] tps = Bukkit.getTPS();
        notes.add(String.format(Locale.ROOT, "tps(1m)=%.2f", tps[0]));

        boolean passed = failures.isEmpty();
        StringBuilder report = new StringBuilder(passed ? "PASS" : "FAIL").append('\n');
        failures.forEach(f -> report.append("FAIL: ").append(f).append('\n'));
        notes.forEach(n -> report.append("note: ").append(n).append('\n'));

        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
            Files.writeString(plugin.getDataFolder().toPath().resolve("selftest-result.txt"), report, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not write self-test result: " + e);
        }
        for (String line : report.toString().split("\n")) log(line);
        log(passed ? "SELFTEST PASS" : "SELFTEST FAIL");
        Bukkit.shutdown();
    }

    // --------------------------------------------------------------- helpers

    private void step(String name, Runnable body) {
        try {
            body.run();
            log("ok: " + name);
        } catch (Exception | LinkageError e) {
            fail(name + " threw " + e);
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Self-test step failed: " + name, e);
        }
    }

    private void expect(boolean condition, String message) {
        if (!condition) fail(message);
    }

    private void fail(String message) {
        if (failures.size() < 200) failures.add(message);
    }

    private void log(String message) {
        plugin.getLogger().info("[selftest] " + message);
    }

    private static String fmt(Location l) {
        return String.format(Locale.ROOT, "%.1f,%.1f,%.1f", l.getX(), l.getY(), l.getZ());
    }
}
