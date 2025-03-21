package dev.qther.doc_exporter.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

import java.nio.file.Path;

@Mixin(value = Path.class, remap = false)
public class PathMixin {
    @WrapMethod(method = "of(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;")
    private static Path wikiPath(String first, String[] more, Operation<Path> original) {
        first = first.replaceFirst("^\\.\\./\\.\\./wiki", "./wiki");
        return original.call(first, more);
    }
}
