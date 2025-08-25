package dev.qther.doc_exporter.mixin;

import com.hollingsworth.arsnouveau.api.documentation.export.DocExporter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.file.Path;

@Mixin(value = DocExporter.class, remap = false)
public class DocExporterMixin {
    @WrapOperation(method = "export", at = @At(value = "INVOKE", target = "Ljava/nio/file/Path;of(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;"))
    private static Path wikiPath(String first, String[] more, Operation<Path> original) {
        if (more.length == 0) {
            String baseWiki = dev.qther.doc_exporter.ExportPaths.wikiBase().toString();
            String replaced = first.replaceFirst("^\\.\\./\\.\\./wiki", baseWiki);
            if (replaced.length() != first.length()) {
                return original.call(replaced, more);
            }
        }
        return original.call(first, more);
    }
}
