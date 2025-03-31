package dev.qther.doc_exporter.mixin;

import com.hollingsworth.arsnouveau.common.util.SpellPartConfigUtil;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(SpellPartConfigUtil.AugmentCosts.class)
public interface AugmentCostsAccessor {
    @Invoker
    Map<ResourceLocation, Integer> invokeParseAugmentCosts();
}
