package dev.qther.doc_exporter.mixin;

import net.neoforged.neoforge.gametest.GameTestHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameTestHooks.class)
public class GameTestHooksMixin {
    @Inject(method = "isGametestEnabled", at = @At("RETURN"), cancellable = true)
    private static void isGametestEnabled(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
