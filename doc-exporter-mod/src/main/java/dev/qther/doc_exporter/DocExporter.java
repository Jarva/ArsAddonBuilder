package dev.qther.doc_exporter;

import net.minecraft.SharedConstants;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(DocExporter.MODID)
public class DocExporter {
    public static final String MODID = "doc_exporter";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public DocExporter(IEventBus modEventBus, ModContainer modContainer) {
    }
}
