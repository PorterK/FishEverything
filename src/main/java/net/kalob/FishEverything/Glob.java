package net.kalob.FishEverything;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** An item-ID pattern from config.yml, e.g. {@code "netherite_*"} or {@code "*_ore"}. */
public record Glob(String source, Pattern pattern) {

    /** Returns null for blank input. Strips a leading {@code minecraft:} namespace; case-insensitive. */
    public static Glob of(String glob) {
        if (glob == null) return null;
        String cleaned = glob.trim().toLowerCase(Locale.ROOT);
        if (cleaned.startsWith("minecraft:")) cleaned = cleaned.substring("minecraft:".length());
        if (cleaned.isEmpty()) return null;

        StringBuilder regex = new StringBuilder();
        String[] parts = cleaned.split("\\*", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) regex.append(".*");
            if (!parts[i].isEmpty()) regex.append(Pattern.quote(parts[i]));
        }
        return new Glob(glob.trim(), Pattern.compile(regex.toString()));
    }

    public static List<Glob> compileAll(Collection<String> globs) {
        List<Glob> result = new ArrayList<>(globs.size());
        for (String glob : globs) {
            Glob compiled = of(glob);
            if (compiled != null) result.add(compiled);
        }
        return result;
    }

    public boolean matches(String itemKey) {
        return pattern.matcher(itemKey.toLowerCase(Locale.ROOT)).matches();
    }

    public static boolean anyMatch(List<Glob> globs, String itemKey) {
        for (Glob glob : globs) {
            if (glob.matches(itemKey)) return true;
        }
        return false;
    }
}
