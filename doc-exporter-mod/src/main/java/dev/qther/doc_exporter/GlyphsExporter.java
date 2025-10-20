package dev.qther.doc_exporter;

import com.google.gson.JsonElement;
import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractAugment;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.SpellSchool;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.qther.doc_exporter.mixin.AugmentCostsAccessor;
import dev.qther.doc_exporter.mixin.AugmentLimitsAccessor;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.util.NeoForgeExtraCodecs;
import org.checkerframework.checker.units.qual.K;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utilities for glyph JSON production.
 * All methods and supporting types are static/pure so they can be used from DocExportHelper.
 */
public final class GlyphsExporter {
    private GlyphsExporter() {}

    public static JsonElement buildGlyphsJson(Map<ResourceLocation, AbstractSpellPart> glyphMap) {
        Codec<SpellSchool> schoolCodec = Codec.recursive(SpellSchool.class.getSimpleName(), rec -> RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(SpellSchool::getId),
                NeoForgeExtraCodecs.setOf(rec).fieldOf("subschools").forGetter(s -> collectToOrderedSet(s.getSubSchools().stream().sorted(Comparator.comparing(SpellSchool::getId))))
        ).apply(instance, (id, subschools) -> {
            SpellSchool school = new SpellSchool(id);
            school.setSubSchools(subschools);
            return school;
        })));

        Codec<AbstractSpellPart> spellPartCodec = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("registryName").forGetter(AbstractSpellPart::getRegistryName),
                Codec.STRING.fieldOf("localizationKey").forGetter(AbstractSpellPart::getLocalizationKey),
                Codec.STRING.fieldOf("name").forGetter(AbstractSpellPart::getName),
                ResourceLocation.CODEC.fieldOf("texture").forGetter(p -> Minecraft.getInstance().getItemRenderer().getItemModelShaper().getItemModel(p.glyphItem).getParticleIcon(ModelData.EMPTY).contents().name()),
                Codec.BOOL.fieldOf("animated").forGetter(p -> Minecraft.getInstance().getItemRenderer().getItemModelShaper().getItemModel(p.glyphItem).getParticleIcon(ModelData.EMPTY).contents().getUniqueFrames().skip(1).anyMatch(i -> true)),
                schoolCodec.listOf().fieldOf("spellSchools").forGetter(p -> p.spellSchools.stream().sorted(Comparator.comparing(SpellSchool::getId)).toList()),
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

        Codec<Map<ResourceLocation, AbstractSpellPart>> spellpartMapCodec = Codec.unboundedMap(ResourceLocation.CODEC, spellPartCodec);

        // Deterministic order by key string
        TreeMap<ResourceLocation, AbstractSpellPart> sortedGlyphMap = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        sortedGlyphMap.putAll(glyphMap);

        return spellpartMapCodec.encodeStart(JsonOps.INSTANCE, sortedGlyphMap).getOrThrow();
    }

    public static <K, V> Map<K, V> collectToOrderedMap(Stream<Map.Entry<K, V>> stream) {
        Object2ObjectLinkedOpenHashMap<K, V> map = new Object2ObjectLinkedOpenHashMap<>();
        stream.forEach(e -> map.put(e.getKey(), e.getValue()));
        return map;
    }

    public static <V> Set<V> collectToOrderedSet(Stream<V> stream) {
        ObjectLinkedOpenHashSet<V> set = new ObjectLinkedOpenHashSet<>();
        stream.forEach(set::add);
        return set;
    }

    public record AugmentDetails(AbstractSpellPart part) {
        public static Codec<AugmentDetails> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                NeoForgeExtraCodecs.setOf(ResourceLocation.CODEC.comapFlatMap(id -> {
                    if (GlyphRegistry.getSpellPart(id) instanceof AbstractAugment augment) {
                        return DataResult.success(augment);
                    }
                    return DataResult.error(() -> id + " is not an augment");
                }, AbstractSpellPart::getRegistryName)).fieldOf("compatible").forGetter(p -> collectToOrderedSet(p.part.compatibleAugments.stream().sorted(Comparator.comparing(a -> a.getRegistryName().toString())))),
                Codec.unboundedMap(ResourceLocation.CODEC.comapFlatMap(id -> {
                    if (GlyphRegistry.getSpellPart(id) instanceof AbstractAugment augment) {
                        return DataResult.success(augment);
                    }
                    return DataResult.error(() -> id + " is not an augment");
                }, AbstractSpellPart::getRegistryName), ComponentSerialization.CODEC).fieldOf("descriptions").forGetter(p -> collectToOrderedMap(p.part.augmentDescriptions.entrySet().stream().filter(e -> p.part.compatibleAugments.contains(e.getKey())).sorted(Comparator.comparing(e -> e.getKey().getRegistryName().toString())))),
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).fieldOf("costs").forGetter(p -> p.part.augmentCosts == null ? new HashMap<>() : collectToOrderedMap(((AugmentCostsAccessor) p.part.augmentCosts).invokeParseAugmentCosts().entrySet().stream().filter(e -> GlyphRegistry.getSpellPart(e.getKey()) instanceof AbstractAugment augment && p.part.compatibleAugments.contains(augment)))),
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).fieldOf("limits").forGetter(p -> p.part.augmentLimits == null ? new HashMap<>() : collectToOrderedMap(((AugmentLimitsAccessor) p.part.augmentLimits).invokeParseAugmentLimits().entrySet().stream().filter(e -> GlyphRegistry.getSpellPart(e.getKey()) instanceof AbstractAugment augment && p.part.compatibleAugments.contains(augment))))
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
                NeoForgeExtraCodecs.setOf(ResourceLocation.CODEC).fieldOf("invalidCombinations").forGetter(p -> collectToOrderedSet(p.part.invalidCombinations.parseComboLimits().stream().sorted(Comparator.comparing(e -> e.toString()))))
        ).apply(instance, (a, b, c, d, e, f, g) -> {
            throw new RuntimeException("cannot decode Defaults");
        }));
    }
}