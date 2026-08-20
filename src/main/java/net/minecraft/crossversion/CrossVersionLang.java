package net.minecraft.crossversion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.resources.Language;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 跨版本新增物品与方块的翻译注入。
 *
 * <p>本客户端的语言加载走资源包体系，而资源包里的语言 JSON <b>不可靠</b> —— 项目里
 * {@code MmdSkinLangInjector} 为了同样的原因改成了编译期嵌入，其注释写得很明确。
 * 唯一可靠的通道是 classpath 直读：{@code LanguageMap} 加载 en_us 就是用
 * {@code getResourceAsStream} 硬编码读的，这也解释了为什么往 assets 的 en_us.json
 * 里加键能生效，而其他语言不行。
 *
 * <p>因此补充翻译放在 {@code /crossversion/lang/<code>.json}，由
 * {@code ClientLanguageMap} 在构建语言表时调用 {@link #inject} 合入。
 * 按传入的语言顺序依次覆盖，与原版的语言优先级一致。
 */
public final class CrossVersionLang {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String BASE = "/crossversion/lang/";

    private CrossVersionLang() {
    }

    /**
     * 把补充翻译合入语言表。按传入的语言顺序依次叠加，与原版行为一致
     * （后面的语言优先级更高，通常列表是 [en_us, 当前语言]）。
     *
     * @param languages 本次加载的语言列表
     * @param target    正在构建的键值表，原地修改
     */
    public static void inject(List<Language> languages, Map<String, String> target) {
        for (Language language : languages) {
            load(language.getCode(), target);
        }
    }

    private static void load(String code, Map<String, String> target) {
        if (code == null) {
            return;
        }

        String path = BASE + code.toLowerCase(java.util.Locale.ROOT) + ".json";
        try (InputStream in = CrossVersionLang.class.getResourceAsStream(path)) {
            if (in == null) {
                // 该语言没有补充翻译是正常情况，不是错误。
                return;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }

            JsonObject root = JsonParser.parseString(sb.toString()).getAsJsonObject();
            int added = 0;
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                if (!e.getValue().isJsonPrimitive()) {
                    continue;
                }
                // 必须覆盖而不是「只补不覆盖」：语言列表是 [en_us, 当前语言]，按顺序叠加，
                // 后面的优先级更高。en_us 的键此时已经在表里了（assets 里的 en_us.json
                // 走 LanguageMap 的 classpath 直读通道），如果这里用 putIfAbsent，
                // 中文等语言的值会被英文挡住，表现为「翻译不生效、名字仍是英文」。
                // 本文件只含跨版本新增物品与方块的键，不会碰到原版或其他模块的翻译。
                target.put(e.getKey(), e.getValue().getAsString());
                added++;
            }
            if (added > 0) {
                LOGGER.info("[CrossVersion] 语言 {} 注入 {} 个翻译", code, added);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("[CrossVersion] 读取补充翻译 {} 失败: {}", path, e.toString());
        }
    }
}
