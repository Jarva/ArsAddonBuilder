package dev.qther.doc_exporter;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.hollingsworth.arsnouveau.api.documentation.export.DocExporter;
import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
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
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Mod(DocExportHelper.MODID)
public class DocExportHelper {
    public static final String MODID = "doc_exporter";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public boolean exported = false;

    public static final ExecutorService executor = Executors.newCachedThreadPool();

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

        var langExport = new Thread(() -> {
            // Export lang
            try {
                Path langDir = ExportPaths.langBase();
                Files.createDirectories(langDir);

                var languages = Minecraft.getInstance().getLanguageManager().getLanguages();
                var sw = Stopwatch.createStarted();

                for (String langCode : languages.keySet()) {
                    Path langPath = ExportPaths.langFile(langCode);

                    if (!LangExporter.loadLanguage(langCode)) {
                        continue;
                    }

                    LOGGER.info("Exporting language {}", langCode);
                    var langMap = new TreeMap<>(Language.getInstance().getLanguageData());
                    JsonElement jsonEl = LangExporter.buildLangJson(langMap);
                    executor.submit(() -> {
                        try {
                            Files.writeString(langPath, jsonEl.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                        } catch (IOException e) {
                            LOGGER.error("Could not create lang file for {}", langCode, e);
                        }
                    });
                }
                LOGGER.info("Exported {} lang files in {}", languages.size(), DurationFormatUtils.formatDurationHMS(sw.elapsed().toMillis()));
            } catch (IOException | IllegalStateException e) {
                LOGGER.error("could not create lang files", e);
            }

            LangExporter.loadLanguage("en_us");
        });

        var animatedTextureExport = new Thread(() -> {
            // Export animated textures
            try {
                Path animatedTexturesBase = ExportPaths.animatedTexturesBase();
                Files.createDirectories(animatedTexturesBase);

                var sw = Stopwatch.createStarted();
                LOGGER.info("Exporting animated textures");
                AnimatedTextureExporter.exportAnimatedTextures();
                LOGGER.info("Exported animated textures in {}", DurationFormatUtils.formatDurationHMS(sw.elapsed().toMillis()));
            } catch (IOException | IllegalStateException e) {
                LOGGER.error("could not create animated textures", e);
            }
        });

        langExport.start();
        animatedTextureExport.start();
        try {
            langExport.join();
        } catch (InterruptedException e) {
            LOGGER.error("Lang export interrupted", e);
        }
        try {
            animatedTextureExport.join();
        } catch (InterruptedException e) {
            LOGGER.error("Animated texture export interrupted", e);
        }

        // Exit
        Minecraft.getInstance().stop();
    }
}
