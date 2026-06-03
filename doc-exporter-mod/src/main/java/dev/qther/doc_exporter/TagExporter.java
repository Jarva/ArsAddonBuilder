package dev.qther.doc_exporter;

import com.google.gson.JsonArray;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Exports all resolved tag values from every registry into JSON files.
 */
public final class TagExporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocExportHelper.MODID + ":TagExporter");

    private TagExporter() {}

    public static void exportAll(Level level) throws IOException {
        var registryAccess = level.registryAccess();
        registryAccess.registries().forEach(entry -> {
            try {
                exportRegistry(entry.value());
            } catch (Exception e) {
                LOGGER.error("Failed to export tags for registry {}", entry.key().location(), e);
            }
        });
    }

    private static <T> void exportRegistry(Registry<T> registry) {
        ResourceLocation registryId = registry.key().location();
        String registryNs = registryId.getNamespace();
        String registryPath = registryId.getPath();

        registry.getTags().forEach(pair -> {
            var tagKey = pair.getFirst();
            var holderSet = pair.getSecond();

            ResourceLocation tagId = tagKey.location();
            String tagNs = tagId.getNamespace();
            String tagPath = tagId.getPath();

            List<String> entries = new ArrayList<>();
            holderSet.forEach(holder ->
                    holder.unwrapKey().ifPresent(key -> entries.add(key.location().toString()))
            );
            entries.sort(Comparator.naturalOrder());

            JsonArray jsonArray = new JsonArray();
            entries.forEach(jsonArray::add);

            Path outputFile = ExportPaths.tagsBase()
                    .resolve(registryNs)
                    .resolve(registryPath)
                    .resolve(tagNs)
                    .resolve(tagPath + ".json");

            DocExportHelper.executor.submit(() -> {
                try {
                    Files.createDirectories(outputFile.getParent());
                    Files.writeString(outputFile, jsonArray.toString(),
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                } catch (IOException e) {
                    LOGGER.error("Could not write tag file {}", outputFile, e);
                }
            });
        });
    }
}
