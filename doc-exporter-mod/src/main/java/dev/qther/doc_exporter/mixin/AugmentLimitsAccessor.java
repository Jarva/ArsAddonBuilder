package dev.qther.doc_exporter.mixin;

import com.hollingsworth.arsnouveau.common.util.SpellPartConfigUtil;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(SpellPartConfigUtil.AugmentLimits.class)
public interface AugmentLimitsAccessor {
    @Invoker
    Map<ResourceLocation, Integer> invokeParseAugmentLimits();
}
