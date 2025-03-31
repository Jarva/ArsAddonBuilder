package dev.qther.doc_exporter.mixin;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.LanguageHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LanguageHook.class)
public interface LanguageHookAccessor {
    @Invoker
    static void invokeLoadLanguage(String langName, MinecraftServer server) {}
}
