package dev.qther.doc_exporter;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod(DocExporter.MODID)
public class DocExporter {
    public static final String MODID = "doc_exporter";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public boolean exported = false;

    public DocExporter(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::onDocFinish);
    }

    public void onDocFinish(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        if (level == null || level.getGameTime() < 5 || exported) {
            return;
        }
        exported = true;

        for (var mod : ModList.get().getMods()) {
            DocExporter.LOGGER.info("Exporting docs for {} @ {} to {}", mod.getModId(), mod.getVersion(), Path.of("../wiki/" + mod.getModId()).toAbsolutePath());
            try {
                Files.createDirectories(Path.of("../wiki/" + mod.getModId() + "/categories"));
                Files.createDirectories(Path.of("../wiki/" + mod.getModId() + "/entries"));
            } catch (IOException e) {
                LOGGER.error("could not create wiki directories", e);
                return;
            }
            com.hollingsworth.arsnouveau.api.documentation.export.DocExporter.export(mod.getModId());

            LOGGER.info("Cleaning docs for {} @ {}", mod.getModId(), mod.getVersion());
            try {
                deleteDirIfEmpty("../wiki/" + mod.getModId() + "/categories");
                deleteDirIfEmpty("../wiki/" + mod.getModId() + "/entries");
                deleteDirIfEmpty("../wiki/" + mod.getModId());
            } catch (IOException e) {
                LOGGER.error("could not create wiki directories", e);
                return;
            }
        }
    }

    static void deleteDirIfEmpty(String pathStr) throws IOException {
        var path = Path.of(pathStr);
        try (var files = Files.list(path)) {
            if (files.findAny().isEmpty()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
