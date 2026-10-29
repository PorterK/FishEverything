package net.kalob.FishEverything;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public final class FishListener implements Listener {

    private final FishEverythingPlugin plugin;
    private final AtomicInteger errors = new AtomicInteger();

    public FishListener(FishEverythingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(event.getCaught() instanceof Item caught)) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("fisheverything.use")) return;
        if (plugin.isWorldDisabled(player.getWorld().getName())) return;
        if (ThreadLocalRandom.current().nextDouble(100) >= plugin.replaceChance()) return;

        ItemStack original = caught.getItemStack();
        try {
            plugin.applyCatch(caught, EffectTarget.of(player), luckOfTheSea(player));
        } catch (Exception | LinkageError e) {
            // Never break fishing: fall back to the vanilla catch.
            caught.setItemStack(original);
            int count = errors.incrementAndGet();
            if (count <= 5) {
                plugin.getLogger().log(Level.WARNING, "Failed to roll a FishEverything catch; using the vanilla catch.", e);
            }
        }
    }

    /** Our celebration fireworks are purely cosmetic. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework
                && firework.getPersistentDataContainer().has(plugin.effects().harmlessFireworkKey(), PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    static int luckOfTheSea(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack rod = main.getType() == Material.FISHING_ROD ? main : player.getInventory().getItemInOffHand();
        if (rod.getType() != Material.FISHING_ROD) return 0;
        return rod.getEnchantmentLevel(Enchantment.LUCK_OF_THE_SEA);
    }
}
