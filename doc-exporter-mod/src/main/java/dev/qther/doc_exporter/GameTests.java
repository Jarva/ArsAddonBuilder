package dev.qther.doc_exporter;

import net.minecraft.client.Minecraft;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@GameTestHolder(DocExporter.MODID)
public class GameTests {
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty", timeoutTicks = Integer.MAX_VALUE)
    public static void exportDocs(GameTestHelper helper) throws IOException {
        for (var mod : ModList.get().getMods()) {
            DocExporter.LOGGER.info("Exporting docs for {} @ {}", mod.getModId(), mod.getVersion());
            Files.createDirectories(Path.of("../wiki/" + mod.getModId() + "/categories"));
            Files.createDirectories(Path.of("../wiki/" + mod.getModId() + "/entries"));
            Minecraft.getInstance().getConnection().sendCommand("ars-doc-export " + mod.getModId());
        }

        helper.runAfterDelay(20, () -> {
            for (var mod : ModList.get().getMods()) {
                DocExporter.LOGGER.info("Cleaning docs for {} @ {}", mod.getModId(), mod.getVersion());
                try {
                    deleteDirIfEmpty("../wiki/" + mod.getModId() + "/categories");
                    deleteDirIfEmpty("../wiki/" + mod.getModId() + "/entries");
                    deleteDirIfEmpty("../wiki/" + mod.getModId());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            helper.succeed();
        });
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
