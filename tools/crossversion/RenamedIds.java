package verify;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * 高版本改名的注册项清单，从唯一来源 {@code ModernRegistry.RENAMED_TO_LEGACY} 解析。
 *
 * <p>这些标识符<b>不是新增内容</b>，只是 1.16.4 的同一个东西换了名字
 * （{@code short_grass} 就是 {@code grass}、{@code turtle_scute} 就是 {@code scute}）。
 * 注册它们会在创造栏里出现两个一模一样的条目。
 *
 * <p>为什么要解析而不是各自写一份：生成器原先自己维护一个
 * {@code RENAMED_NOT_NEW = {"iron_chain"}}，与 {@code ModernRegistry} 的四项脱节，
 * 结果 {@code short_grass} 和 {@code turtle_scute} 被当成新内容注册了 ——
 * 编译期不报错，回归也没查，直到把创造栏分类逐项列出来才看见。
 * 现在只有一个来源，不会再漂。
 */
final class RenamedIds {

    private RenamedIds() {
    }

    private static final Path SOURCE = Paths.get("src/main/java/net/minecraft/crossversion/ModernRegistry.java");

    /** 高版本标识符 -> 1.16.4 的标识符。 */
    static Map<String, String> load(Path repo) throws Exception {
        String src = Files.readString(repo.resolve(SOURCE), StandardCharsets.UTF_8);
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = Pattern.compile(
                "RENAMED_TO_LEGACY\\.put\\(\\s*\"([a-z0-9_]+)\"\\s*,\\s*\"([a-z0-9_]+)\"\\s*\\)").matcher(src);
        while (m.find()) out.put(m.group(1), m.group(2));
        if (out.isEmpty()) {
            throw new IllegalStateException("没从 ModernRegistry 解析到任何改名项，正则可能失配：" + SOURCE);
        }
        return out;
    }
}
