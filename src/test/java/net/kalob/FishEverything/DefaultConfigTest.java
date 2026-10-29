package net.kalob.FishEverything;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** Validates the config.yml that ships in the JAR against the real 26.3 item list. */
class DefaultConfigTest {

    private static YamlConfiguration config;
    private static List<String> itemKeys;

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = DefaultConfigTest.class.getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(in, "config.yml missing from resources");
            config = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        itemKeys = Arrays.stream(Material.values())
                .map(Material::name)
                .filter(name -> !name.startsWith("LEGACY_"))
                .map(name -> name.toLowerCase(Locale.ROOT))
                .toList();
        assertTrue(itemKeys.size() > 1000, "expected the full 26.3 material list");
    }

    @Test
    void everyTierIsConfiguredWithPositiveWeight() {
        for (Rarity rarity : Rarity.values()) {
            String path = "rarities." + rarity.configKey();
            assertTrue(config.isConfigurationSection(path), "missing " + path);
            assertTrue(config.getDouble(path + ".weight") > 0, path + ".weight must be > 0");
            assertTrue(config.isString(path + ".display"), path + ".display missing");
        }
    }

    @Test
    void tiersGetRarerAsTheyGoUp() {
        Rarity[] all = Rarity.values();
        for (int i = 1; i < all.length; i++) {
            assertTrue(config.getDouble("rarities." + all[i].configKey() + ".weight")
                            < config.getDouble("rarities." + all[i - 1].configKey() + ".weight"),
                    all[i] + " should be rarer than " + all[i - 1]);
        }
    }

    @Test
    void everyPatternMatchesARealItem() {
        LootRules rules = LootRules.fromConfig(config);
        assertEquals(List.of(), rules.unmatchedPatterns(itemKeys),
                "these config patterns match no Minecraft 26.3 item (typo or renamed item?)");
    }

    @Test
    void knownItemsLandInTheRightTier() {
        LootRules rules = LootRules.fromConfig(config);
        assertEquals(Rarity.COMMON, rules.tierOf("dirt"));
        assertEquals(Rarity.COMMON, rules.tierOf("poppy"));
        assertEquals(Rarity.UNCOMMON, rules.tierOf("iron_ingot"));
        assertEquals(Rarity.RARE, rules.tierOf("gold_ingot"));
        assertEquals(Rarity.RARE, rules.tierOf("coal_ore"));
        assertEquals(Rarity.EPIC, rules.tierOf("diamond_sword"));
        assertEquals(Rarity.EPIC, rules.tierOf("golden_apple"));
        assertEquals(Rarity.EPIC, rules.tierOf("enchanted_book"));
        assertEquals(Rarity.LEGENDARY, rules.tierOf("netherite_sword"));
        assertEquals(Rarity.LEGENDARY, rules.tierOf("elytra"));
        assertEquals(Rarity.LEGENDARY, rules.tierOf("dragon_head"));
        assertEquals(Rarity.MYTHIC, rules.tierOf("dragon_egg"));
        assertEquals(Rarity.MYTHIC, rules.tierOf("enchanted_golden_apple"));
        assertEquals(Rarity.MYTHIC, rules.tierOf("nether_star"));
    }

    @Test
    void dangerousItemsAreExcluded() {
        LootRules rules = LootRules.fromConfig(config);
        for (String item : List.of("command_block", "chain_command_block", "repeating_command_block",
                "command_block_minecart", "structure_block", "jigsaw", "barrier", "debug_stick",
                "wither_spawn_egg", "ender_dragon_spawn_egg")) {
            assertNull(rules.tierOf(item), item + " must be excluded");
        }
    }

    @Test
    void everyTierHasItems() {
        LootPool<String> pool = new LootPool<>(LootRules.fromConfig(config), itemKeys, Function.identity());
        for (Rarity rarity : Rarity.values()) {
            assertFalse(pool.items(rarity).isEmpty(), rarity + " has no items");
        }
        assertTrue(pool.total() > 1000);
    }

    @Test
    void messagesAndTierNamesAreValidMiniMessage() {
        MiniMessage mm = MiniMessage.builder().strict(true).build();
        TagResolver placeholders = TagResolver.resolver(
                Placeholder.component("tier", Component.text("Rare")),
                Placeholder.component("item", Component.text("[Diamond]")),
                Placeholder.unparsed("player", "Steve"));
        for (String path : List.of("messages.action-bar", "messages.broadcast")) {
            String format = config.getString(path);
            assertNotNull(format, path);
            Component c = assertDoesNotThrow(() -> mm.deserialize(format, placeholders), path);
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
            assertFalse(plain.contains("<"), path + " left an unparsed tag: " + plain);
        }
        for (Rarity rarity : Rarity.values()) {
            String display = config.getString("rarities." + rarity.configKey() + ".display");
            assertDoesNotThrow(() -> mm.deserialize(display), rarity + " display");
        }
    }

    @Test
    void fishAreKeptFromTheVanillaLootTable() {
        List<Glob> keep = Glob.compileAll(config.getStringList("keep-vanilla"));
        for (String fish : List.of("cod", "salmon", "tropical_fish", "pufferfish")) {
            assertTrue(Glob.anyMatch(keep, fish), fish + " should stay a vanilla catch");
        }
        for (String junkOrTreasure : List.of("stick", "leather_boots", "bowl", "name_tag", "enchanted_book", "bow")) {
            assertFalse(Glob.anyMatch(keep, junkOrTreasure), junkOrTreasure + " should be swapped");
        }
        for (Glob glob : keep) {
            assertTrue(itemKeys.stream().anyMatch(glob::matches), "keep-vanilla entry matches no item: " + glob.source());
        }
    }

    @Test
    void generalSettingsAreSane() {
        double replace = config.getDouble("replace-chance");
        assertTrue(replace >= 0 && replace <= 100);

        assertTrue(config.getDouble("luck-of-the-sea-bonus") >= 0);
        assertTrue(config.getInt("max-concurrent-animations") > 0);
        assertTrue(config.isList("disabled-worlds"));
    }
}
