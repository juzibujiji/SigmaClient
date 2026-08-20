package verify;

import com.google.gson.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 从官方语言文件里挑出跨版本新增物品与方块的翻译，输出成本项目的补充语言文件。
 *
 * <p>为什么不直接把整份官方语言文件塞进 assets：本客户端的语言加载走资源包体系，
 * 而资源包里的语言 JSON <b>不可靠</b>（见 {@code MmdSkinLangInjector} 的注释，那里为了
 * 同样的原因改成了编译期嵌入）。可靠的通道是 classpath 直读 —— {@code LanguageMap} 加载
 * en_us 就是这么做的。所以这里把补充翻译写到 {@code resources/crossversion/lang/}，
 * 由 {@link net.minecraft.crossversion.CrossVersionLang} 在语言重载时注入。
 *
 * <p>官方语言文件（除 en_us）不在 client.jar 里，需要按 asset index 从
 * {@code resources.download.minecraft.net} 下载后作为输入传进来。
 *
 * <p>用法：ExtractLang &lt;物品清单&gt; &lt;方块清单&gt; &lt;输出目录&gt; &lt;语言代码=文件路径&gt;...
 */
public class ExtractLang {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("用法: ExtractLang <物品清单> <方块清单> <输出目录> <语言代码=文件路径>...");
            System.exit(2);
        }

        List<String> prefixes = new ArrayList<>();
        for (String id : readList(args[1])) prefixes.add("block.minecraft." + id);
        for (String id : readList(args[0])) prefixes.add("item.minecraft." + id);

        Path outDir = Paths.get(args[2]);
        Files.createDirectories(outDir);

        for (int i = 3; i < args.length; i++) {
            int eq = args[i].indexOf('=');
            if (eq < 0) {
                System.err.println("跳过格式错误的参数: " + args[i]);
                continue;
            }
            String code = args[i].substring(0, eq);
            Path source = Paths.get(args[i].substring(eq + 1));
            extract(code, source, prefixes, outDir);
        }
    }

    private static void extract(String code, Path source, List<String> prefixes, Path outDir) throws Exception {
        if (!Files.exists(source)) {
            System.err.println(code + ": 找不到 " + source + "，跳过");
            return;
        }
        JsonObject root = JsonParser.parseString(Files.readString(source, StandardCharsets.UTF_8)).getAsJsonObject();

        // 输出保持插入顺序，便于 diff。
        JsonObject out = new JsonObject();
        int count = 0;
        for (Map.Entry<String, JsonElement> e : root.entrySet()) {
            String key = e.getKey();
            if (!e.getValue().isJsonPrimitive()) continue;
            for (String p : prefixes) {
                if (key.equals(p) || key.startsWith(p + ".")) {
                    out.add(key, e.getValue());
                    count++;
                    break;
                }
            }
        }

        Path target = outDir.resolve(code + ".json");
        Files.writeString(target, GSON.toJson(out) + "\n", StandardCharsets.UTF_8);
        System.out.printf("%s: 提取 %d 个键 -> %s%n", code, count, target);
    }

    private static List<String> readList(String path) throws Exception {
        List<String> out = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get(path))) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith("#")) out.add(t.replace("minecraft:", ""));
        }
        return out;
    }
}
