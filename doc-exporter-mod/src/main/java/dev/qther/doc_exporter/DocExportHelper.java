package dev.qther.doc_exporter;

import com.hollingsworth.arsnouveau.api.documentation.export.DocExporter;
import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforgespi.language.IModInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

@Mod(DocExportHelper.MODID)
public class DocExportHelper {
    public static final String MODID = "doc_exporter";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public boolean exported = false;

    public DocExportHelper(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::postTick);
    }

    public void postTick(ClientTickEvent.Post event) {
        Level level = Minecraft.getInstance().level;
        if (level == null || level.getGameTime() < 20 || exported) {
            return;
        }
        exported = true;

        // Export docs
        for (IModInfo mod : ModList.get().getMods()) {
            Path modWiki = ExportPaths.wikiForMod(mod.getModId());
            DocExportHelper.LOGGER.info("Exporting docs for {} @ {} to {}", mod.getModId(), mod.getVersion(), modWiki.toAbsolutePath());
            try {
                Files.createDirectories(modWiki.resolve("categories"));
                Files.createDirectories(modWiki.resolve("entries"));
            } catch (IOException e) {
                LOGGER.error("could not create wiki directories", e);
                return;
            }
            DocExporter.export(mod.getModId());
        }

        // Export glyphs
        try {
            Path glyphsPath = ExportPaths.glyphsFile();
            try (BufferedWriter writer = Files.newBufferedWriter(glyphsPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                JsonElement jsonEl = GlyphsExporter.buildGlyphsJson(GlyphRegistry.getSpellpartMap());
                com.google.gson.Gson gson = new GsonBuilder().setPrettyPrinting().create();
                writer.append(gson.toJson(jsonEl));
            }
        } catch (IOException | IllegalStateException e) {
            LOGGER.error("could not create glyphs file", e);
        }

        // Export lang
        try {
            Path langDir = ExportPaths.langBase();
            Files.createDirectories(langDir);

            for (String langCode : Minecraft.getInstance().getLanguageManager().getLanguages().keySet()) {
                Path langPath = ExportPaths.langFile(langCode);

                LOGGER.info("Exporting language {}", langCode);
                if (!LangExporter.loadLanguage(langCode)) {
                    continue;
                }
                try (java.io.BufferedWriter writer = Files.newBufferedWriter(langPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    JsonElement jsonEl = LangExporter.buildLangJson(Language.getInstance().getLanguageData());
                    writer.append(jsonEl.toString());
                }
                LOGGER.info("Exported language {}", langCode);
            }
        } catch (IOException | IllegalStateException e) {
            LOGGER.error("could not create lang files", e);
        }

        LangExporter.loadLanguage("en_us");

        // Exit
        Minecraft.getInstance().stop();
    }
}
