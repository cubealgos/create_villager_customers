package villager_customers;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Two claims about the source tree, checked against the files themselves: the mod opens no socket
 * of its own (COMP-REQ-001: no networking type is referenced outside Minecraft's own packet API),
 * and every translation key the code names has an {@code en_us} entry.
 */
final class SourceSurfaceTest {
    private static final Path MAIN = Path.of("src/main/java");
    private static final Path LANG = Path.of("src/main/resources/assets/villager_customers/lang/en_us.json");
    private static final Pattern NETWORKING = Pattern.compile(
        "java\\.net\\.|java\\.nio\\.channels\\.|HttpClient|Socket|URLConnection|HttpURLConnection");
    private static final Pattern KEY = Pattern.compile(
        "\"((?:item|block|screen|tooltip|command)\\.villager_customers\\.[a-z_.]+)\"");

    @Test
    void noNetworkingTypeIsReferencedByTheMod() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : sources()) {
            String text = Files.readString(file);
            Matcher m = NETWORKING.matcher(text);
            if (m.find()) {
                offenders.add(file + " mentions " + m.group());
            }
        }
        assertTrue(offenders.isEmpty(), "COMP-REQ-001: no outbound networking, but " + offenders);
    }

    @Test
    void everyTranslationKeyNamedInCodeHasAnEnglishEntry() throws IOException {
        String lang = Files.readString(LANG);
        List<String> missing = new ArrayList<>();
        for (Path file : sources()) {
            Matcher m = KEY.matcher(Files.readString(file));
            while (m.find()) {
                String key = m.group(1);
                // Keys ending in a dot are prefixes completed at runtime.
                if (!key.endsWith(".") && !lang.contains("\"" + key + "\"")) {
                    missing.add(key + " in " + file.getFileName());
                }
            }
        }
        assertTrue(missing.isEmpty(), "every key has an en_us entry, but " + missing);
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> walk = Files.walk(MAIN)) {
            return walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }
}
