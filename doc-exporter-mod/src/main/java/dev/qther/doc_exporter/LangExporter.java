package dev.qther.doc_exporter;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.TreeMap;

/**
 * Utilities for language loading and JSON production.
 */
public final class LangExporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocExportHelper.MODID + ":LangExporter");

    private LangExporter() {}

    public static boolean loadLanguage(String langCode) {
        try {
            Minecraft mc = Minecraft.getInstance();
            mc.getLanguageManager().setSelected(langCode);
            mc.options.languageCode = langCode;
            mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
            return true;
        } catch (Exception e) {
            LOGGER.error("could not load language {}", langCode, e);
            return false;
        }
    }

    public static JsonElement buildLangJson(Map<String, String> langData) {
        Codec<Map<String, String>> s2sMapCodec = Codec.unboundedMap(Codec.STRING, Codec.STRING);
        TreeMap<String, String> sorted = new TreeMap<>(langData);
        return s2sMapCodec.encodeStart(JsonOps.INSTANCE, sorted).getOrThrow();
    }

    public static JsonElement buildCurrentLanguageJson() {
        return buildLangJson(Language.getInstance().getLanguageData());
    }
}