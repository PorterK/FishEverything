package net.kalob.FishEverything;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PluginDescriptorTest {

    private static YamlConfiguration pluginYml() throws Exception {
        try (InputStream in = PluginDescriptorTest.class.getClassLoader().getResourceAsStream("plugin.yml")) {
            assertNotNull(in, "plugin.yml missing");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void mainClassExistsAndIsAPlugin() throws Exception {
        String main = pluginYml().getString("main");
        assertEquals("net.kalob.FishEverything.FishEverythingPlugin", main);
        Class<?> cls = Class.forName(main, false, getClass().getClassLoader());
        assertTrue(JavaPlugin.class.isAssignableFrom(cls));
    }

    @Test
    void targetsMinecraft26_3() throws Exception {
        assertEquals("26.3", pluginYml().getString("api-version"));
    }

    @Test
    void versionWasFilledInByTheBuild() throws Exception {
        String version = pluginYml().getString("version");
        assertNotNull(version);
        assertFalse(version.contains("${"), "version placeholder was not expanded: " + version);
    }

    @Test
    void commandAndPermissionsAreDeclared() throws Exception {
        YamlConfiguration yml = pluginYml();
        assertTrue(yml.isConfigurationSection("commands.fisheverything"));
        assertTrue(yml.getStringList("commands.fisheverything.aliases").contains("fe"));
        for (String perm : new String[]{"fisheverything.use", "fisheverything.odds", "fisheverything.admin"}) {
            assertTrue(yml.isConfigurationSection("permissions." + perm), perm);
        }
        assertEquals("op", yml.getString("permissions.fisheverything.admin.default"));
    }
}
