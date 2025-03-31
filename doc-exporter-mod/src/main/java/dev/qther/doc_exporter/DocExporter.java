package dev.qther.doc_exporter;

import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.SpellSchool;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectBreak;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.qther.doc_exporter.mixin.AugmentCostsAccessor;
import dev.qther.doc_exporter.mixin.AugmentLimitsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.entity.animation.json.AnimationLoader;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.NeoForgeExtraCodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Mod(DocExporter.MODID)
public class DocExporter {
    public static final String MODID = "doc_exporter";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public boolean exported = false;

    public DocExporter(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::postTick);
    }

    public void postTick(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        if (level == null || level.getGameTime() < 20 || exported) {
            return;
        }
        exported = true;

        // Export docs
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

        // Export glyphs
        Codec<SpellSchool> schoolCodec = Codec.recursive(SpellSchool.class.getSimpleName(), rec -> RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(SpellSchool::getId),
                NeoForgeExtraCodecs.setOf(rec).fieldOf("subschools").forGetter(SpellSchool::getSubSchools)
        ).apply(instance, (id, subschools) -> {
            var school = new SpellSchool(id);
            school.setSubSchools(subschools);
            return school;
        })));

        Codec<AbstractSpellPart> spellPartCodec = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("registryName").forGetter(AbstractSpellPart::getRegistryName),
                Codec.STRING.fieldOf("localizationKey").forGetter(AbstractSpellPart::getLocalizationKey),
                Codec.STRING.fieldOf("name").forGetter(AbstractSpellPart::getName),
                ResourceLocation.CODEC.fieldOf("texture").forGetter(p -> Minecraft.getInstance().getItemRenderer().getItemModelShaper().getItemModel(p.glyphItem).getParticleIcon(ModelData.EMPTY).contents().name()),
                Codec.BOOL.fieldOf("animated").forGetter(p -> Minecraft.getInstance().getItemRenderer().getItemModelShaper().getItemModel(p.glyphItem).getParticleIcon(ModelData.EMPTY).contents().getUniqueFrames().skip(1).anyMatch(i -> true)),
                schoolCodec.listOf().fieldOf("spellSchools").forGetter(p -> p.spellSchools),
                Defaults.CODEC.fieldOf("defaults").forGetter(Defaults::new),
                ComponentSerialization.CODEC.fieldOf("typeName").forGetter(AbstractSpellPart::getTypeName),
                Codec.INT.fieldOf("typeIndex").forGetter(AbstractSpellPart::getTypeIndex),
                Codec.STRING.listOf().fieldOf("classes").forGetter(p -> {
                    List<String> classes = new ArrayList<>();
                    Class<?> clazz = p.getClass();
                    while (clazz != AbstractSpellPart.class) {
                        classes.add(clazz.getCanonicalName());
                        clazz = clazz.getSuperclass();
                    }
                    return classes;
                })
        ).apply(instance, (a, b, c, d, e, f, g, h, i, j) -> {
            throw new RuntimeException("cannot decode AbstractSpellPart");
        }));

        var spellpartMapCodec = Codec.unboundedMap(ResourceLocation.CODEC, spellPartCodec);

        try {
            var glyphsPath = Path.of("../glyphs.json");
            try (var writer = Files.newBufferedWriter(glyphsPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                var json = spellpartMapCodec.encodeStart(JsonOps.INSTANCE, GlyphRegistry.getSpellpartMap());
                writer.append(json.getOrThrow().toString());
            }
        } catch (IOException | IllegalStateException e) {
            LOGGER.error("could not create glyphs file", e);
        }

        // Export lang
        var s2sMapCodec = Codec.unboundedMap(Codec.STRING, Codec.STRING);
        try {
            Files.createDirectories(Path.of("../lang/"));

            for (var langCode : Minecraft.getInstance().getLanguageManager().getLanguages().keySet()) {
                var langPath = Path.of("../lang/" + langCode + ".json");

                LOGGER.info("Exporting language {}", langCode);
                try {
                    loadLanguage(langCode);
                } catch (Exception e) {
                    LOGGER.error("could not load language {}", langCode, e);
                    continue;
                }
                try (var writer = Files.newBufferedWriter(langPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    var json = s2sMapCodec.encodeStart(JsonOps.INSTANCE, Language.getInstance().getLanguageData());
                    writer.append(json.getOrThrow().toString());
                }
                LOGGER.info("Exported language {}", langCode);
            }
        } catch (IOException | IllegalStateException e) {
            LOGGER.error("could not create lang files", e);
        }

        try {
            loadLanguage("en_us");
        } catch (Exception ignored) {
        }

        // Exit
        Minecraft.getInstance().stop();
    }

    static void deleteDirIfEmpty(String pathStr) throws IOException {
        var path = Path.of(pathStr);
        try (var files = Files.list(path)) {
            if (files.findAny().isEmpty()) {
                Files.deleteIfExists(path);
            }
        }
    }

    public record AugmentDetails(AbstractSpellPart part) {
        public static Codec<AugmentDetails> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                NeoForgeExtraCodecs.setOf(ResourceLocation.CODEC.comapFlatMap(id -> {
                    if (GlyphRegistry.getSpellPart(id) instanceof AbstractAugment augment) {
                        return DataResult.success(augment);
                    }
                    return DataResult.error(() -> id + " is not an augment");
                }, AbstractSpellPart::getRegistryName)).fieldOf("compatible").forGetter(p -> p.part.compatibleAugments),
                Codec.unboundedMap(ResourceLocation.CODEC.comapFlatMap(id -> {
                    if (GlyphRegistry.getSpellPart(id) instanceof AbstractAugment augment) {
                        return DataResult.success(augment);
                    }
                    return DataResult.error(() -> id + " is not an augment");
                }, AbstractSpellPart::getRegistryName), ComponentSerialization.CODEC).fieldOf("descriptions").forGetter(p -> p.part.augmentDescriptions.entrySet().stream().filter(e -> p.part.compatibleAugments.contains(e.getKey())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))),
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).fieldOf("costs").forGetter(p -> p.part.augmentCosts == null ? new HashMap<>() : ((AugmentCostsAccessor) p.part.augmentCosts).invokeParseAugmentCosts().entrySet().stream().filter(e -> GlyphRegistry.getSpellPart(e.getKey()) instanceof AbstractAugment augment && p.part.compatibleAugments.contains(augment)).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))),
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).fieldOf("limits").forGetter(p -> p.part.augmentLimits == null ? new HashMap<>() : ((AugmentLimitsAccessor) p.part.augmentLimits).invokeParseAugmentLimits().entrySet().stream().filter(e -> GlyphRegistry.getSpellPart(e.getKey()) instanceof AbstractAugment augment && p.part.compatibleAugments.contains(augment)).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
        ).apply(instance, (a, b, c, d) -> {
            throw new RuntimeException("cannot decode AugmentDetails");
        }));
    }

    public record Defaults(AbstractSpellPart part) {
        public static Codec<Defaults> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("tier").forGetter(p -> p.part.defaultTier().value),
                Codec.INT.fieldOf("cost").forGetter(p -> p.part.getCastingCost()),
                Codec.BOOL.fieldOf("enabled").forGetter(p -> p.part.isEnabled()),
                Codec.BOOL.fieldOf("starter").forGetter(p -> p.part.defaultedStarterGlyph()),
                Codec.INT.fieldOf("perSpellLimit").forGetter(p -> p.part.PER_SPELL_LIMIT == null ? Integer.MAX_VALUE : p.part.PER_SPELL_LIMIT.get()),
                AugmentDetails.CODEC.fieldOf("augments").forGetter(p -> new AugmentDetails(p.part)),
                NeoForgeExtraCodecs.setOf(ResourceLocation.CODEC).fieldOf("invalidCombinations").forGetter(p -> p.part.invalidCombinations.parseComboLimits())
        ).apply(instance, (a, b, c, d, e, f, g) -> {
            throw new RuntimeException("cannot decode Defaults");
        }));
    }

    static void loadLanguage(String langCode) throws ExecutionException, InterruptedException {
        var mc = Minecraft.getInstance();
        mc.getLanguageManager().setSelected(langCode);
        mc.options.languageCode = langCode;
        mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
    }
}