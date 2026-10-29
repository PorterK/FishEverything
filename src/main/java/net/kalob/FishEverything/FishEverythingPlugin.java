package net.kalob.FishEverything;

import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class FishEverythingPlugin extends JavaPlugin {

    private final LootTable lootTable = new LootTable();
    private CatchEffects effects;
    private final Set<String> disabledWorlds = new HashSet<>();
    private double replaceChance = 100;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        effects = new CatchEffects(this);
        reload();

        getServer().getPluginManager().registerEvents(new FishListener(this), this);

        PluginCommand command = getCommand("fisheverything");
        if (command != null) {
            FishEverythingCommand handler = new FishEverythingCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        if (Boolean.getBoolean(SelfTest.PROPERTY)) {
            getLogger().warning("Self-test mode enabled: the server will shut down when the test finishes.");
            new SelfTest(this).start();
        }
    }

    public void reload() {
        reloadConfig();
        replaceChance = Math.max(0, Math.min(100, getConfig().getDouble("replace-chance", 100)));
        disabledWorlds.clear();
        for (String world : getConfig().getStringList("disabled-worlds")) {
            disabledWorlds.add(world.toLowerCase(Locale.ROOT));
        }
        lootTable.load(getConfig(), getLogger());
        effects.load(getConfig());
    }

    /** Swaps a caught item entity for a random roll and plays the celebration. */
    public LootPool.Roll<Material> applyCatch(Item caught, EffectTarget target, int luckLevel) {
        LootPool.Roll<Material> roll = lootTable.roll(luckLevel);
        if (roll == null) return null;

        ItemStack stack = lootTable.createStack(roll);
        caught.setItemStack(stack);
        if (roll.rarity().atLeast(Rarity.EPIC)) {
            caught.setGlowing(true);
        }
        effects.play(target, caught.getLocation(), roll.rarity(), stack);
        return roll;
    }

    public LootTable lootTable() {
        return lootTable;
    }

    public CatchEffects effects() {
        return effects;
    }

    public double replaceChance() {
        return replaceChance;
    }

    public boolean isWorldDisabled(String worldName) {
        return disabledWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }
}
