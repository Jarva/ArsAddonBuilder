package dev.qther.doc_exporter;

import java.nio.file.Path;

/**
 * Central place for filesystem output locations.
 * All paths should be resolved from the single BASE path.
 */
public final class ExportPaths {
    private ExportPaths() {}

    public static final Path BASE = Path.of("../output");

    public static Path wikiBase() {
        return BASE.resolve("wiki");
    }

    public static Path wikiForMod(String modid) {
        return wikiBase().resolve(modid);
    }

    public static Path glyphsFile() {
        return BASE.resolve("glyphs.json");
    }

    public static Path langBase() {
        return BASE.resolve("lang");
    }

    public static Path langFile(String langCode) {
        return langBase().resolve(langCode + ".json");
    }
}