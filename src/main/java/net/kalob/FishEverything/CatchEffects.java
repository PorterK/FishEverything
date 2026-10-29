package net.kalob.FishEverything;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.logging.Level;

/**
 * The fun part: a different celebration for each rarity tier, escalating from a
 * little sparkle (uncommon) to a full lightning-and-fireworks light show (mythic).
 */
public final class CatchEffects {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final FishEverythingPlugin plugin;
    private final NamespacedKey harmlessFireworkKey;

    private final Map<Rarity, String> displayNames = new EnumMap<>(Rarity.class);
    private final Map<Rarity, Boolean> broadcast = new EnumMap<>(Rarity.class);
    private final Map<Rarity, Boolean> animate = new EnumMap<>(Rarity.class);
    private String actionBarFormat;
    private String broadcastFormat;
    private boolean mythicLevitation;
    private int maxConcurrentAnimations;

    private final AtomicInteger activeAnimations = new AtomicInteger();
    private final AtomicInteger peakAnimations = new AtomicInteger();
    private final AtomicInteger errors = new AtomicInteger();
    private final AtomicInteger skippedAnimations = new AtomicInteger();

    public CatchEffects(FishEverythingPlugin plugin) {
        this.plugin = plugin;
        this.harmlessFireworkKey = new NamespacedKey(plugin, "harmless_firework");
    }

    public int activeAnimations() { return activeAnimations.get(); }
    public int peakAnimations() { return peakAnimations.get(); }
    public int errorCount() { return errors.get(); }
    public int skippedAnimations() { return skippedAnimations.get(); }
    public int maxConcurrentAnimations() { return maxConcurrentAnimations; }

    public NamespacedKey harmlessFireworkKey() {
        return harmlessFireworkKey;
    }

    public void load(FileConfiguration config) {
        actionBarFormat = config.getString("messages.action-bar", "<tier> <gray>catch:</gray> <item>");
        broadcastFormat = config.getString("messages.broadcast",
                "<tier> <white><player></white> <gray>just fished up</gray> <item><gray>!</gray>");
        mythicLevitation = config.getBoolean("mythic-levitation", true);
        maxConcurrentAnimations = Math.max(1, config.getInt("max-concurrent-animations", 30));
        for (Rarity rarity : Rarity.values()) {
            String base = "rarities." + rarity.configKey() + ".";
            displayNames.put(rarity, config.getString(base + "display", rarity.name()));
            broadcast.put(rarity, config.getBoolean(base + "broadcast", rarity.atLeast(Rarity.LEGENDARY)));
            animate.put(rarity, config.getBoolean(base + "animation", rarity != Rarity.COMMON));
        }
    }

    public Component tierName(Rarity rarity) {
        return MM.deserialize(displayNames.getOrDefault(rarity, rarity.name()));
    }

    /**
     * Plays the celebration for a catch.
     *
     * @param player who caught it (or a fixed spot)
     * @param hook   where the item came out of the water
     * @param rarity tier of the catch
     * @param stack  the caught item
     */
    public void play(EffectTarget player, Location hook, Rarity rarity, ItemStack stack) {
        guard("play " + rarity.configKey(), () -> playUnsafe(player, hook, rarity, stack));
    }

    private void playUnsafe(EffectTarget player, Location hook, Rarity rarity, ItemStack stack) {
        TagResolver placeholders = TagResolver.resolver(
                Placeholder.component("tier", tierName(rarity)),
                Placeholder.component("item", stack.displayName()),
                Placeholder.unparsed("player", player.name()));

        if (rarity != Rarity.COMMON) {
            player.audience().sendActionBar(MM.deserialize(actionBarFormat, placeholders));
        }
        if (broadcast.getOrDefault(rarity, false)) {
            Bukkit.getServer().sendMessage(MM.deserialize(broadcastFormat, placeholders));
        }
        if (!animate.getOrDefault(rarity, true)) return;
        if (rarity.atLeast(Rarity.RARE) && activeAnimations.get() >= maxConcurrentAnimations) {
            // Too many big animations running at once (AFK fishing farms etc.) - keep the server smooth.
            skippedAnimations.incrementAndGet();
            uncommon(player, hook);
            return;
        }

        switch (rarity) {
            case COMMON -> common(player, hook);
            case UNCOMMON -> uncommon(player, hook);
            case RARE -> rare(player, hook);
            case EPIC -> epic(player, hook);
            case LEGENDARY -> legendary(player, hook, placeholders);
            case MYTHIC -> mythic(player, hook, placeholders);
        }
    }

    // ---------------------------------------------------------------- tiers

    private void common(EffectTarget player, Location hook) {
        hook.getWorld().spawnParticle(Particle.SPLASH, hook, 20, 0.3, 0.1, 0.3, 0.1);
    }

    /** A puff of green sparkles and a happy pling. */
    private void uncommon(EffectTarget player, Location hook) {
        World world = hook.getWorld();
        world.spawnParticle(Particle.HAPPY_VILLAGER, hook.clone().add(0, 0.5, 0), 25, 0.5, 0.5, 0.5, 0);
        world.spawnParticle(Particle.HAPPY_VILLAGER, player.location().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0);
        sound(player, "block.note_block.pling", 1f, 1.6f);
        later(4, () -> sound(player, "block.note_block.pling", 1f, 2.0f));
    }

    /** A blue spiral climbs around the player while chimes play a rising arpeggio. */
    private void rare(EffectTarget player, Location hook) {
        Color blue = Color.fromRGB(0x3FA9F5);
        hook.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, hook.clone().add(0, 0.5, 0), 30, 0.4, 0.4, 0.4, 0.2);
        float[] notes = {1.0f, 1.26f, 1.5f, 2.0f};
        for (int i = 0; i < notes.length; i++) {
            float pitch = notes[i];
            later(i * 3L, () -> sound(player, "block.amethyst_block.chime", 1.2f, pitch));
        }
        repeat(30, tick -> {
            if (!player.isValid()) return;
            Location base = player.location();
            double angle = tick * 0.45;
            double y = tick * 0.08;
            for (int arm = 0; arm < 2; arm++) {
                double a = angle + arm * Math.PI;
                Location point = base.clone().add(Math.cos(a) * 0.9, y, Math.sin(a) * 0.9);
                dust(point, blue, 1.3f, 2);
            }
            if (tick == 29) {
                base.getWorld().spawnParticle(Particle.END_ROD, base.clone().add(0, 2.5, 0), 25, 0.2, 0.2, 0.2, 0.08);
                sound(player, "entity.player.levelup", 0.8f, 1.4f);
            }
        });
    }

    /** A purple double helix, enchanting glyphs swirling in, then a purple firework pop overhead. */
    private void epic(EffectTarget player, Location hook) {
        Color purple = Color.fromRGB(0xA855F7);
        Color pink = Color.fromRGB(0xF472B6);
        sound(player, "block.enchantment_table.use", 1f, 0.8f);
        sound(player, "entity.illusioner.cast_spell", 0.8f, 1.2f);
        hook.getWorld().spawnParticle(Particle.WITCH, hook.clone().add(0, 0.5, 0), 40, 0.4, 0.4, 0.4, 0.05);

        repeat(40, tick -> {
            if (!player.isValid()) return;
            Location base = player.location();
            double y = (tick % 40) * 0.07;
            double angle = tick * 0.35;
            for (int strand = 0; strand < 2; strand++) {
                double a = angle + strand * Math.PI;
                Location point = base.clone().add(Math.cos(a) * 1.1, y, Math.sin(a) * 1.1);
                dust(point, strand == 0 ? purple : pink, 1.5f, 2);
            }
            if (tick % 4 == 0) {
                base.getWorld().spawnParticle(Particle.ENCHANT, base.clone().add(0, 1.2, 0), 30, 1.5, 1.0, 1.5, 1.0);
            }
            if (tick == 39) {
                firework(base.clone().add(0, 2.6, 0), FireworkEffect.Type.BALL_LARGE, 0, purple, pink);
                player.audience().showTitle(Title.title(
                        tierName(Rarity.EPIC),
                        Component.empty(),
                        Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1200), Duration.ofMillis(400))));
            }
        });
    }

    /** Lightning (harmless), a golden totem fountain, three rockets and a title card. */
    private void legendary(EffectTarget player, Location hook, TagResolver placeholders) {
        World world = hook.getWorld();
        Color gold = Color.fromRGB(0xFFC83D);
        Color white = Color.WHITE;
        Color orange = Color.fromRGB(0xFF8A00);

        world.strikeLightningEffect(hook);
        sound(player, "ui.toast.challenge_complete", 1f, 1f);
        player.audience().showTitle(Title.title(
                tierName(Rarity.LEGENDARY),
                MM.deserialize("<gold>You caught</gold> <item><gold>!</gold>", placeholders),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2500), Duration.ofMillis(600))));

        repeat(50, tick -> {
            if (!player.isValid()) return;
            Location base = player.location();
            // golden totem fountain
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, base.clone().add(0, 0.2, 0), 12, 0.3, 0.1, 0.3, 0.6);
            // spinning gold ring at waist height
            for (int i = 0; i < 3; i++) {
                double a = tick * 0.3 + i * (Math.PI * 2 / 3);
                dust(base.clone().add(Math.cos(a) * 1.4, 1.0, Math.sin(a) * 1.4), gold, 1.6f, 1);
            }
            if (tick == 10 || tick == 22 || tick == 34) {
                double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                Location launch = base.clone().add(Math.cos(a) * 2.5, 0.2, Math.sin(a) * 2.5);
                firework(launch, FireworkEffect.Type.STAR, 22, gold, white, orange);
            }
        });
    }

    /**
     * The big one: thunder ring, a beam of light, rainbow shockwaves, a fireworks
     * finale, and the player is gently lifted off the ground in awe.
     */
    private void mythic(EffectTarget player, Location hook, TagResolver placeholders) {
        World world = hook.getWorld();
        Location center = player.location();

        // Everyone nearby hears it.
        world.playSound(Sound.sound(Key.key("entity.ender_dragon.growl"), Sound.Source.MASTER, 2f, 1.2f),
                center.getX(), center.getY(), center.getZ());
        sound(player, "block.beacon.activate", 1.5f, 0.7f);
        sound(player, "item.totem.use", 1f, 1f);

        player.audience().showTitle(Title.title(
                tierName(Rarity.MYTHIC),
                MM.deserialize("<light_purple>The sea gives up a treasure:</light_purple> <item>", placeholders),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(3500), Duration.ofMillis(800))));

        // Ring of six harmless lightning strikes, one every 3 ticks.
        for (int i = 0; i < 6; i++) {
            double a = i * (Math.PI * 2 / 6);
            long delay = i * 3L;
            later(delay, () -> {
                if (!player.isValid()) return;
                world.strikeLightningEffect(player.location().add(Math.cos(a) * 4, 0, Math.sin(a) * 4));
            });
        }

        if (mythicLevitation) {
            later(10, () -> {
                if (!player.isValid()) return;
                player.lift();
            });
        }

        repeat(80, tick -> {
            if (!player.isValid()) return;
            Location base = player.location();

            // Beam of light straight up.
            for (double y = 0; y < 12; y += 0.6) {
                world.spawnParticle(Particle.END_ROD, base.clone().add(0, y, 0), 1, 0.05, 0.05, 0.05, 0);
            }

            // Rainbow shockwave ring every 10 ticks, expanding outward.
            int waveAge = tick % 10;
            double radius = 0.5 + waveAge * 0.6;
            int points = 36;
            for (int i = 0; i < points; i++) {
                double a = i * (Math.PI * 2 / points);
                Color c = rainbow((i / (double) points + tick / 80.0) % 1.0);
                dust(base.clone().add(Math.cos(a) * radius, 0.15, Math.sin(a) * radius), c, 1.8f, 1);
            }

            // Orbiting soul flames and sparkles.
            double a = tick * 0.25;
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, base.clone().add(Math.cos(a) * 1.6, 1.4, Math.sin(a) * 1.6), 2, 0, 0, 0, 0.01);
            world.spawnParticle(Particle.FLAME, base.clone().add(Math.cos(a + Math.PI) * 1.6, 1.4, Math.sin(a + Math.PI) * 1.6), 2, 0, 0, 0, 0.01);
            if (tick % 5 == 0) {
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, base.clone().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.5);
            }

            // Fireworks finale: a rocket every 8 ticks in rainbow colors.
            if (tick >= 20 && tick % 8 == 0) {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                double fa = r.nextDouble(Math.PI * 2);
                Location launch = base.clone().add(Math.cos(fa) * 3, 0.2, Math.sin(fa) * 3);
                FireworkEffect.Type type = FireworkEffect.Type.values()[r.nextInt(FireworkEffect.Type.values().length)];
                firework(launch, type, 15 + r.nextInt(12),
                        rainbow(r.nextDouble()), rainbow(r.nextDouble()), Color.WHITE);
            }
            if (tick == 79) {
                sound(player, "entity.player.levelup", 1f, 0.6f);
                world.spawnParticle(Particle.SONIC_BOOM, base.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
            }
        });
    }

    // -------------------------------------------------------------- helpers

    private void sound(EffectTarget player, String key, float volume, float pitch) {
        player.playSound(Sound.sound(Key.key(key), Sound.Source.PLAYER, volume, pitch));
    }

    private static void dust(Location location, Color color, float size, int count) {
        location.getWorld().spawnParticle(Particle.DUST, location, count, 0.02, 0.02, 0.02, 0,
                new Particle.DustOptions(color, size));
    }

    /**
     * Launches a harmless firework that detonates after {@code fuseTicks}
     * (0 = burst right where it spawns).
     */
    private void firework(Location location, FireworkEffect.Type type, int fuseTicks, Color... colors) {
        Firework firework = location.getWorld().spawn(location, Firework.class, fw -> {
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .with(type)
                    .withColor(colors)
                    .withFade(Color.WHITE)
                    .trail(true)
                    .flicker(true)
                    .build());
            meta.setPower(1);
            fw.setFireworkMeta(meta);
            fw.getPersistentDataContainer().set(harmlessFireworkKey, PersistentDataType.BYTE, (byte) 1);
            fw.setVelocity(new Vector(0, 0.6, 0));
        });
        later(Math.max(1, fuseTicks), () -> {
            if (firework.isValid()) firework.detonate();
        });
    }

    private static Color rainbow(double hue) {
        double h = (hue % 1.0) * 6;
        int sector = (int) Math.floor(h);
        double f = h - sector;
        double q = 1 - f;
        double r, g, b;
        switch (sector) {
            case 0 -> { r = 1; g = f; b = 0; }
            case 1 -> { r = q; g = 1; b = 0; }
            case 2 -> { r = 0; g = 1; b = f; }
            case 3 -> { r = 0; g = q; b = 1; }
            case 4 -> { r = f; g = 0; b = 1; }
            default -> { r = 1; g = 0; b = q; }
        }
        return Color.fromRGB((int) (r * 255), (int) (g * 255), (int) (b * 255));
    }

    private void later(long ticks, Runnable task) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> guard("delayed effect", task), ticks);
    }

    /**
     * Runs an effect step, swallowing and logging any failure. A broken animation
     * should never break fishing or spam the console.
     */
    private void guard(String what, Runnable task) {
        try {
            task.run();
        } catch (Exception | LinkageError e) {
            reportError(what, e);
        }
    }

    private void reportError(String what, Throwable error) {
        int count = errors.incrementAndGet();
        if (count <= 5) {
            plugin.getLogger().log(Level.WARNING, "Catch effect failed (" + what + "); skipping it.", error);
        } else if (count == 6) {
            plugin.getLogger().warning("Further catch effect errors will not be logged.");
        }
    }

    /** Runs {@code body} once per tick for {@code ticks} ticks, passing the tick index. */
    private void repeat(int ticks, IntConsumer body) {
        int now = activeAnimations.incrementAndGet();
        peakAnimations.accumulateAndGet(now, Math::max);
        new BukkitRunnable() {
            int tick = 0;
            boolean done = false;

            @Override
            public void run() {
                if (done) return;
                if (tick >= ticks) {
                    finish();
                    return;
                }
                int current = tick++;
                try {
                    body.accept(current);
                } catch (Exception | LinkageError e) {
                    reportError("animation tick", e);
                    finish();
                }
            }

            private void finish() {
                done = true;
                activeAnimations.decrementAndGet();
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
