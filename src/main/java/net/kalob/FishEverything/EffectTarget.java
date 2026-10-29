package net.kalob.FishEverything;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Who/where a catch animation plays for: a player, or a fixed spot (used by the self-test). */
public sealed interface EffectTarget {

    boolean isValid();

    Location location();

    Audience audience();

    String name();

    void playSound(Sound sound);

    /** Mythic only: gently lift the target into the air. */
    void lift();

    static EffectTarget of(Player player) {
        return new PlayerTarget(player);
    }

    static EffectTarget at(Location location, Audience audience, String name) {
        return new FixedTarget(location.clone(), audience, name);
    }

    record PlayerTarget(Player player) implements EffectTarget {
        @Override public boolean isValid() { return player.isOnline() && player.isValid(); }
        @Override public Location location() { return player.getLocation(); }
        @Override public Audience audience() { return player; }
        @Override public String name() { return player.getName(); }
        @Override public void playSound(Sound sound) { player.playSound(sound, Sound.Emitter.self()); }

        @Override
        public void lift() {
            player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 25, 0, false, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 120, 0, false, false, false));
        }
    }

    record FixedTarget(Location location, Audience audience, String name) implements EffectTarget {
        @Override public boolean isValid() { return location.isWorldLoaded(); }
        @Override public Location location() { return location.clone(); }

        @Override
        public void playSound(Sound sound) {
            location.getWorld().playSound(sound, location.getX(), location.getY(), location.getZ());
        }

        @Override public void lift() { /* nothing to lift */ }
    }
}
