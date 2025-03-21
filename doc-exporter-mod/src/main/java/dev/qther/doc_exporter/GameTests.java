package dev.qther.doc_exporter;

import net.minecraft.client.Minecraft;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(DocExporter.MODID)
public class GameTests {
    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty", batch = "defaultBatch")
    public static void exportDocs(GameTestHelper helper) {
        for (var mod : ModList.get().getMods()) {
            DocExporter.LOGGER.info("Exporting docs for {} @ {}", mod.getModId(), mod.getVersion());
            Minecraft.getInstance().getConnection().sendCommand("ars-doc-export " + mod.getModId());
        }

        helper.runAfterDelay(10 * 20, helper::succeed);
    }
}
