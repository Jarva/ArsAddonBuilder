package dev.qther.doc_exporter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.qther.doc_exporter.DocExporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.file.Path;

@Mixin(targets = "com.hollingsworth.arsnouveau.api.documentation.export.DocExporter", remap = false)
public class DocExporterMixin {
    @WrapOperation(method = "export", at = @At(value = "INVOKE", target = "Ljava/nio/file/Path;of(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;"))
    private static Path wikiPath(String first, String[] more, Operation<Path> original) {
        if (more.length == 0) {
            var replaced = first.replaceFirst("^\\.\\./\\.\\./wiki", "../wiki");
            if (replaced.length() != first.length()) {
                return original.call(replaced, more);
            }
        }
        return original.call(first, more);
    }
}
