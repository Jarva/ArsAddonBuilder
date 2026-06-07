package dev.qther.doc_exporter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
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
        var registryAccess = level.registryAccess();
        RegistryOps<JsonElement> ops = registryAccess.createSerializationContext(JsonOps.INSTANCE);
        var recipeManager = level.getRecipeManager();

        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            ResourceLocation id = holder.id();
            try {
                JsonObject json = serializeRecipe(holder.value(), ops);
                if (json == null) {
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
                LOGGER.error("Failed to serialize recipe {}", id, e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Recipe<?>> JsonObject serializeRecipe(T recipe, RegistryOps<JsonElement> ops) {
        RecipeSerializer<T> serializer = (RecipeSerializer<T>) recipe.getSerializer();
        var result = serializer.codec().codec().encodeStart(ops, recipe);
        if (result.isError()) {
            LOGGER.warn("Cannot encode recipe with serializer {}: {}",
                    BuiltInRegistries.RECIPE_SERIALIZER.getKey(serializer),
                    result.error().orElseThrow().message());
            return null;
        }
        JsonObject obj = result.getOrThrow().getAsJsonObject();
        obj.addProperty("type", BuiltInRegistries.RECIPE_SERIALIZER.getKey(serializer).toString());
        return obj;
    }
}
