package dev.qther.doc_exporter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Exports all resolved recipes from the recipe manager into JSON files.
 */
public final class RecipeExporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocExportHelper.MODID + ":RecipeExporter");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RecipeExporter() {}

    public static void exportAll(Level level) throws IOException {
        var recipeManager = level.getRecipeManager();

        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            LOGGER.error("No integrated server available, cannot export recipes");
            return;
        }
        ResourceManager resourceManager = server.getResourceManager();

        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            ResourceLocation id = holder.id();
            try {
                JsonObject json = readRawRecipeJson(id, resourceManager);
                if (json == null) {
                    LOGGER.warn("Cannot export recipe {} (no data pack JSON found)", id);
                    continue;
                }
                Path outputFile = ExportPaths.recipesBase()
                        .resolve(id.getNamespace())
                        .resolve(id.getPath() + ".json");

                DocExportHelper.executor.submit(() -> {
                    try {
                        Files.createDirectories(outputFile.getParent());
                        Files.writeString(outputFile, GSON.toJson(json),
                                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    } catch (IOException e) {
                        LOGGER.error("Could not write recipe file {}", outputFile, e);
                    }
                });
            } catch (Exception e) {
                LOGGER.error("Failed to export recipe {}", id, e);
            }
        }
    }

    private static JsonObject readRawRecipeJson(ResourceLocation id, ResourceManager resourceManager) {
        var fileLoc = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "recipe/" + id.getPath() + ".json");
        try {
            var resource = resourceManager.getResource(fileLoc);
            if (resource.isPresent()) {
                try (var reader = resource.get().openAsReader()) {
                    return JsonParser.parseReader(reader).getAsJsonObject();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not read raw recipe JSON for {}: {}", id, e.getMessage());
        }
        return null;
    }
}
