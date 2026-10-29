package net.kalob.FishEverything;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FishEverythingCommand implements TabExecutor {

    private final FishEverythingPlugin plugin;

    public FishEverythingCommand(FishEverythingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "odds" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "odds" -> odds(sender);
            case "reload" -> {
                if (!checkAdmin(sender)) return true;
                plugin.reload();
                sender.sendMessage(Component.text("FishEverything config reloaded.", NamedTextColor.GREEN));
            }
            case "test" -> test(sender, label, args);
            default -> sender.sendMessage(Component.text(
                    "Usage: /" + label + " <odds|reload|test <rarity>>", NamedTextColor.RED));
        }
        return true;
    }

    private void odds(CommandSender sender) {
        if (!sender.hasPermission("fisheverything.odds")) {
            sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
            return;
        }
        int luck = 0;
        if (sender instanceof Player player) {
            luck = FishListener.luckOfTheSea(player);
        }
        Map<Rarity, Double> weights = plugin.lootTable().effectiveWeights(luck);
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();

        double replace = plugin.replaceChance() / 100.0;
        sender.sendMessage(Component.text("FishEverything odds per cast"
                + (luck > 0 ? " (Luck of the Sea " + luck + ")" : "") + ":", NamedTextColor.AQUA));
        sender.sendMessage(Component.text()
                .append(Component.text("Vanilla catch", NamedTextColor.BLUE))
                .append(Component.text("  " + format((1 - replace) * 100) + "%  ", NamedTextColor.WHITE))
                .append(Component.text("(mostly fish)", NamedTextColor.GRAY))
                .build());
        for (Rarity rarity : Rarity.values()) {
            int count = plugin.lootTable().count(rarity);
            double chance = total <= 0 ? 0 : weights.get(rarity) / total * 100 * replace;
            String perItem = count == 0 ? "-" : format(chance / count);
            sender.sendMessage(Component.text()
                    .append(plugin.effects().tierName(rarity))
                    .append(Component.text("  " + format(chance) + "%  ", NamedTextColor.WHITE))
                    .append(Component.text("(" + count + " items, ~" + perItem + "% each)", NamedTextColor.GRAY))
                    .build());
        }
    }

    private void test(CommandSender sender, String label, String[] args) {
        if (!checkAdmin(sender)) return;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can test catches.", NamedTextColor.RED));
            return;
        }
        Rarity rarity = args.length >= 2 ? Rarity.parse(args[1]) : null;
        if (rarity == null) {
            sender.sendMessage(Component.text("Usage: /" + label + " test <common|uncommon|rare|epic|legendary|mythic>",
                    NamedTextColor.RED));
            return;
        }
        LootPool.Roll<Material> roll = plugin.lootTable().randomIn(rarity);
        if (roll == null) {
            sender.sendMessage(Component.text("No items are in the " + rarity.configKey() + " tier.", NamedTextColor.RED));
            return;
        }
        ItemStack stack = plugin.lootTable().createStack(roll);
        player.getInventory().addItem(stack).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        plugin.effects().play(EffectTarget.of(player), player.getLocation().add(player.getLocation().getDirection().multiply(3)), rarity, stack);
    }

    private static boolean checkAdmin(CommandSender sender) {
        if (sender.hasPermission("fisheverything.admin")) return true;
        sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
        return false;
    }

    private static String format(double value) {
        if (value >= 1) return String.format(Locale.ROOT, "%.1f", value);
        if (value >= 0.01) return String.format(Locale.ROOT, "%.2f", value);
        return String.format(Locale.ROOT, "%.4f", value);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("odds");
            if (sender.hasPermission("fisheverything.admin")) {
                options.add("reload");
                options.add("test");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("test") && sender.hasPermission("fisheverything.admin")) {
            for (Rarity rarity : Rarity.values()) options.add(rarity.configKey());
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.startsWith(prefix));
        return options;
    }
}
