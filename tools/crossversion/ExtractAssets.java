package verify;

import com.google.gson.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.*;

/**
 * 从官方 1.21.11 client.jar 提取新物品与方块所需的资源，合并进本项目的 assets。
 *
 * <p>1.16.4 的 {@code ModelBakery} 会遍历所有注册项加载 {@code models/item/<name>.json}
 * 与 {@code blockstates/<name>.json}，缺失就 log warn 并渲染成紫黑块，所以每个新注册项
 * 都必须补齐这些文件。
 *
 * <p><b>关于 1.21.4+ 的模型新格式：</b>官方 jar 里 {@code assets/minecraft/items/*.json}
 * 是新的 item model definition，1.16.4 不认识 —— 但同时 {@code models/item/*.json} 仍然
 * 是旧格式且内容完整，所以这里<b>直接忽略 items/ 目录</b>，不需要做任何格式转换。
 *
 * <p>依赖是递归的：blockstates 引用 models/block，模型有 parent 链，模型引用材质，
 * 材质可能带 .mcmeta 动画定义。本工具跟随全部引用。
 *
 * <p><b>只写入项目中尚不存在的文件</b>，绝不覆盖 1.16.4 原版资源 —— 高版本同名文件可能
 * 用了本客户端不支持的特性，覆盖会打坏原版渲染。
 */
public class ExtractAssets {
    private static final String PREFIX = "assets/minecraft/";
    private static final Gson GSON = new Gson();

    private final ZipFile jar;
    private final Path outRoot;
    private final boolean dryRun;

    /** 待处理队列与已处理集合，用于递归跟随依赖。 */
    private final Deque<String> pending = new ArrayDeque<>();
    private final Set<String> seen = new HashSet<>();

    private final List<String> written = new ArrayList<>();
    private final List<String> skippedExisting = new ArrayList<>();
    private final List<String> missing = new ArrayList<>();
    /** 从 1.21.4+ item model definition 转换出来的物品模型。 */
    private final List<String> converted = new ArrayList<>();
    /** 用了按状态切换模型的物品，1.16.4 表达不了，只取了第一个分支，需人工复核。 */
    private final List<String> statefulModels = new ArrayList<>();

    /**
     * 需要裁剪 blockstate 变体的方块：方块名 -> 要裁掉的属性名（逗号分隔）。
     *
     * <p>1.19 给树叶、1.21.9 给铜格栅加了 {@code waterlogged}，1.16.4 的对应方块类没有
     * 这个属性。官方 blockstate 会为 {@code waterlogged=true/false} 各指定一个模型，
     * 直接照搬会有一半变体匹配不上、渲染成紫黑块。裁掉 {@code =true} 的变体、
     * 并把 {@code =false} 从 variant key 里去掉，方块就能正常渲染。
     * 清单由 GenerateBlocks 写出。
     */
    private final Map<String, Set<String>> trimProps = new HashMap<>();

    ExtractAssets(Path jarPath, Path outRoot, boolean dryRun) throws IOException {
        this.jar = new ZipFile(jarPath.toFile());
        this.outRoot = outRoot;
        this.dryRun = dryRun;
    }

    /** 载入裁剪清单，格式每行 {@code 方块名:属性,属性}。 */
    void loadTrimList(Path path) throws IOException {
        if (!Files.exists(path)) return;
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String t = line.trim();
            int colon = t.indexOf(':');
            if (t.isEmpty() || colon < 0) continue;
            Set<String> props = new HashSet<>(Arrays.asList(t.substring(colon + 1).split(",")));
            trimProps.put(t.substring(0, colon), props);
        }
        if (!trimProps.isEmpty()) {
            System.out.println("需要裁剪 blockstate 变体的方块：" + trimProps.size() + " 个");
        }
    }

    /**
     * @param args [0]=client.jar [1]=输出的 assets 根目录 [2]=物品清单 [3]=方块清单
     *             [4]=可选 --dry-run
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("用法: ExtractAssets <client.jar> <assets根目录> <物品清单> <方块清单> [--dry-run]");
            System.exit(2);
        }
        boolean dryRun = args.length > 4 && "--dry-run".equals(args[4]);
        ExtractAssets tool = new ExtractAssets(Paths.get(args[0]), Paths.get(args[1]), dryRun);
        try {
            List<String> items = readList(args[2]);
            List<String> blocks = readList(args[3]);
            System.out.printf("待提取：%d 个物品、%d 个方块%s%n", items.size(), blocks.size(),
                    dryRun ? "（试运行，不写文件）" : "");
            tool.run(items, blocks);
        } finally {
            tool.jar.close();
        }
    }

    private static List<String> readList(String path) throws IOException {
        List<String> out = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get(path))) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith("#")) out.add(t.replace("minecraft:", ""));
        }
        return out;
    }

    void run(List<String> items, List<String> blocks) throws IOException {
        for (String block : blocks) enqueue("blockstates/" + block + ".json");
        for (String item : items) enqueue("models/item/" + item + ".json");

        while (!pending.isEmpty()) {
            process(pending.poll());
        }

        mergeLang(items, blocks);
        mergeChineseLang(items, blocks);

        System.out.printf("%n写入 %d 个文件（其中 %d 个由新格式转换），跳过 %d 个已存在%n",
                written.size(), converted.size(), skippedExisting.size());

        if (!statefulModels.isEmpty()) {
            System.out.printf("%n%d 个物品用了按状态切换的模型，已取第一个分支，建议人工复核：%n",
                    statefulModels.size());
            statefulModels.stream().limit(20).forEach(s -> System.out.println("  " + s));
            if (statefulModels.size() > 20) {
                System.out.println("  ... 另有 " + (statefulModels.size() - 20) + " 项");
            }
        }

        if (!missing.isEmpty()) {
            System.out.printf("%n缺失 %d 项（jar 里找不到，需要人工确认）：%n", missing.size());
            missing.stream().limit(40).forEach(m -> System.out.println("  " + m));
            if (missing.size() > 40) System.out.println("  ... 另有 " + (missing.size() - 40) + " 项");
        }
    }

    private void enqueue(String relPath) {
        if (seen.add(relPath)) pending.add(relPath);
    }

    private void process(String relPath) throws IOException {
        ZipEntry entry = jar.getEntry(PREFIX + relPath);
        if (entry == null) {
            handleMissing(relPath);
            return;
        }

        byte[] data = read(entry);

        // 先跟随依赖，再写文件：即使写入被跳过（原版已有），依赖也要补齐。
        if (relPath.endsWith(".json")) {
            String json = new String(data, StandardCharsets.UTF_8);
            try {
                if (relPath.startsWith("blockstates/")) {
                    followBlockstate(json);
                    // 裁剪 1.16.4 表达不了的状态属性
                    String name = relPath.substring("blockstates/".length(),
                            relPath.length() - ".json".length());
                    Set<String> trim = trimProps.get(name);
                    if (trim != null) {
                        data = trimBlockstate(json, trim, name).getBytes(StandardCharsets.UTF_8);
                    }
                } else if (relPath.startsWith("models/")) {
                    followModel(json);
                }
            } catch (JsonParseException e) {
                System.err.println("解析失败 " + relPath + ": " + e.getMessage());
            }
        }

        emit(relPath, data);

        // 材质的动画定义与材质本体成对出现，但多数材质没有动画，缺失属正常。
        if (relPath.endsWith(".png")) enqueue(relPath + ".mcmeta");
    }

    /**
     * jar 里找不到该资源时的处理。
     *
     * <p>方块物品的模型在 1.21.4+ 只以新格式存在（{@code items/<name>.json}），
     * 旧的 {@code models/item/<name>.json} 已被移除，这里从新格式转换出来。
     * {@code .mcmeta} 与 {@code models/builtin/*} 的缺失是正常的，不报告。
     */
    private void handleMissing(String relPath) throws IOException {
        if (relPath.startsWith("models/item/") && relPath.endsWith(".json")
                && convertFromItemDefinition(relPath)) {
            return;
        }
        if (relPath.endsWith(".mcmeta") || relPath.startsWith("models/builtin/")) {
            return;
        }
        missing.add(relPath);
    }

    /**
     * 读取 1.21.4+ 的 item model definition，转换成 1.16.4 认识的
     * {@code {"parent": "<模型>"}}。
     *
     * @return 是否成功转换
     */
    private boolean convertFromItemDefinition(String relPath) throws IOException {
        String name = relPath.substring("models/item/".length(), relPath.length() - ".json".length());
        ZipEntry def = jar.getEntry(PREFIX + "items/" + name + ".json");
        if (def == null) return false;

        JsonObject root;
        try {
            root = GSON.fromJson(new String(read(def), StandardCharsets.UTF_8), JsonObject.class);
        } catch (JsonParseException e) {
            return false;
        }
        if (root == null) return false;

        String modelRef = findFirstModelRef(root.get("model"));
        if (modelRef == null) return false;

        // 新格式支持按物品状态切换模型（condition / select / range_dispatch）。1.16.4 的
        // 模型系统表达不了，这里取第一个分支，并记录下来供人工复核。
        if (!isPlainModel(root.get("model"))) {
            statefulModels.add(name + " -> " + modelRef);
        }

        enqueue("models/" + strip(modelRef) + ".json");
        emit(relPath, ("{\n  \"parent\": \"" + modelRef + "\"\n}\n").getBytes(StandardCharsets.UTF_8));
        converted.add(relPath);
        return true;
    }

    private static boolean isPlainModel(JsonElement model) {
        return model != null && model.isJsonObject()
                && model.getAsJsonObject().has("type")
                && "minecraft:model".equals(model.getAsJsonObject().get("type").getAsString());
    }

    /** 在 item model definition 里递归找出第一个具体模型引用。 */
    private static String findFirstModelRef(JsonElement node) {
        if (node == null) return null;
        if (node.isJsonObject()) {
            JsonObject obj = node.getAsJsonObject();
            JsonElement model = obj.get("model");
            if (model != null && model.isJsonPrimitive()) return model.getAsString();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                String found = findFirstModelRef(e.getValue());
                if (found != null) return found;
            }
        } else if (node.isJsonArray()) {
            for (JsonElement e : node.getAsJsonArray()) {
                String found = findFirstModelRef(e);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** 写出资源，已存在则跳过——绝不覆盖 1.16.4 原版资源。 */
    private void emit(String relPath, byte[] data) throws IOException {
        Path target = outRoot.resolve(relPath);
        if (Files.exists(target)) {
            skippedExisting.add(relPath);
            return;
        }
        if (!dryRun) {
            Files.createDirectories(target.getParent());
            Files.write(target, data);
        }
        written.add(relPath);
    }

    /**
     * 裁掉 blockstate 里 1.16.4 表达不了的状态属性。
     *
     * <p>做法：只保留该属性为 {@code false} 的变体，并把这一段从 variant key 里删掉。
     * 例如 {@code distance=1,persistent=false,waterlogged=false} 变成
     * {@code distance=1,persistent=false}，而 {@code waterlogged=true} 的整条丢弃。
     *
     * <p>multipart 形式（栅栏、墙那类）不动：它按条件匹配而非枚举全部状态组合，
     * 多余的 {@code when} 条件不会导致匹配失败。
     */
    private String trimBlockstate(String json, Set<String> propsToTrim, String name) {
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        if (root == null || !root.has("variants")) return json;

        JsonObject variants = root.getAsJsonObject("variants");
        JsonObject trimmed = new JsonObject();
        int dropped = 0;

        for (Map.Entry<String, JsonElement> e : variants.entrySet()) {
            List<String> keep = new ArrayList<>();
            boolean drop = false;

            for (String part : e.getKey().split(",")) {
                int eq = part.indexOf('=');
                if (eq > 0 && propsToTrim.contains(part.substring(0, eq))) {
                    // 只保留 false 分支；true 分支整条丢弃
                    if (!"false".equals(part.substring(eq + 1))) {
                        drop = true;
                        break;
                    }
                } else if (!part.isEmpty()) {
                    keep.add(part);
                }
            }

            if (drop) {
                dropped++;
                continue;
            }
            trimmed.add(String.join(",", keep), e.getValue());
        }

        JsonObject out = new JsonObject();
        for (Map.Entry<String, JsonElement> e : root.entrySet()) {
            out.add(e.getKey(), "variants".equals(e.getKey()) ? trimmed : e.getValue());
        }
        System.out.printf("  裁剪 %s：去掉 %s 的 %d 个变体，剩 %d 个%n",
                name, propsToTrim, dropped, trimmed.size());
        return GSON.toJson(out) + "\n";
    }

    /** blockstates：variants / multipart 里的每个 model 引用。 */
    private void followBlockstate(String json) {        JsonObject root = GSON.fromJson(json, JsonObject.class);
        if (root == null) return;

        if (root.has("variants")) {
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("variants").entrySet()) {
                JsonElement v = e.getValue();
                if (v.isJsonArray()) v.getAsJsonArray().forEach(x -> enqueueModel(x.getAsJsonObject()));
                else if (v.isJsonObject()) enqueueModel(v.getAsJsonObject());
            }
        }
        if (root.has("multipart")) {
            for (JsonElement part : root.getAsJsonArray("multipart")) {
                JsonElement apply = part.getAsJsonObject().get("apply");
                if (apply == null) continue;
                if (apply.isJsonArray()) apply.getAsJsonArray().forEach(x -> enqueueModel(x.getAsJsonObject()));
                else enqueueModel(apply.getAsJsonObject());
            }
        }
    }

    private void enqueueModel(JsonObject holder) {
        JsonElement model = holder.get("model");
        if (model != null && model.isJsonPrimitive()) {
            enqueue("models/" + strip(model.getAsString()) + ".json");
        }
    }

    /** 模型：parent 链 + textures 里的每个材质引用。 */
    private void followModel(String json) {
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        if (root == null) return;

        JsonElement parent = root.get("parent");
        if (parent != null && parent.isJsonPrimitive()) {
            enqueue("models/" + strip(parent.getAsString()) + ".json");
        }

        JsonElement textures = root.get("textures");
        if (textures != null && textures.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : textures.getAsJsonObject().entrySet()) {
                if (!e.getValue().isJsonPrimitive()) continue;
                String ref = e.getValue().getAsString();
                // 以 # 开头的是变量引用（指向同一模型的其他 texture 槽），不是文件。
                if (ref.startsWith("#")) continue;
                enqueue("textures/" + strip(ref) + ".png");
            }
        }
    }

    /** 去掉 minecraft: 命名空间前缀。非 minecraft 命名空间不处理（原版资源不会有）。 */
    private static String strip(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    /**
     * 把新物品与方块的显示名合并进 {@code lang/en_us.json}。
     *
     * <p>用文本插入而不是重新序列化整个文件：项目的 en_us.json 有 29 万字符，整体重写会
     * 产生一个无法审阅的巨大 diff。只在末尾追加缺失的键，已有的键一律不动。
     */
    private void mergeLang(List<String> items, List<String> blocks) throws IOException {
        ZipEntry entry = jar.getEntry(PREFIX + "lang/en_us.json");
        if (entry == null) {
            System.err.println("jar 里没有 lang/en_us.json，跳过语言合并");
            return;
        }
        JsonObject source = GSON.fromJson(new String(read(entry), StandardCharsets.UTF_8), JsonObject.class);

        Path langPath = outRoot.resolve("lang/en_us.json");
        if (!Files.exists(langPath)) {
            System.err.println("项目里没有 " + langPath + "，跳过语言合并");
            return;
        }
        String content = Files.readString(langPath, StandardCharsets.UTF_8);
        JsonObject existing = GSON.fromJson(content, JsonObject.class);

        // 收集所有相关键：精确键与其子键（例如 item.minecraft.mace.desc）。
        List<String> prefixes = new ArrayList<>();
        for (String id : blocks) prefixes.add("block.minecraft." + id);
        for (String id : items) prefixes.add("item.minecraft." + id);
        prefixes.addAll(Arrays.asList(EXTRA_LANG_KEYS));
        Set<String> referenced = referencedLangKeys();

        Map<String, String> toAdd = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : source.entrySet()) {
            String key = e.getKey();
            if (existing.has(key) || !e.getValue().isJsonPrimitive()) continue;
            if (matches(key, prefixes, referenced)) {
                toAdd.put(key, e.getValue().getAsString());
            }
        }

        if (toAdd.isEmpty()) {
            System.out.println("语言文件无需改动");
            return;
        }

        int lastBrace = content.lastIndexOf('}');
        if (lastBrace < 0) {
            System.err.println("语言文件格式异常，跳过合并");
            return;
        }

        StringBuilder sb = new StringBuilder(content.substring(0, lastBrace).stripTrailing());
        sb.append(",\n");
        int i = 0;
        for (Map.Entry<String, String> e : toAdd.entrySet()) {
            sb.append("  ").append(GSON.toJson(e.getKey())).append(": ").append(GSON.toJson(e.getValue()));
            sb.append(++i < toAdd.size() ? ",\n" : "\n");
        }
        sb.append(content.substring(lastBrace));

        if (!dryRun) {
            Files.writeString(langPath, sb.toString(), StandardCharsets.UTF_8);
        }
        System.out.printf("语言文件新增 %d 个键%s%n", toAdd.size(), dryRun ? "（试运行）" : "");
        toAdd.entrySet().stream().limit(8)
                .forEach(e -> System.out.println("  " + e.getKey() + " = " + e.getValue()));
        if (toAdd.size() > 8) System.out.println("  ... 另有 " + (toAdd.size() - 8) + " 项");
    }

    /** MCP-Reborn 解出的官方中文语言文件。 */
    private static final String MCP_REBORN_ZH =
            "F:/HCMLNew/MCP-Reborn-release/extracted-assets/assets/minecraft/lang/zh_cn.json";

    /**
     * 不属于「方块 / 物品」两类、但必须一起收进来的官方翻译键。
     *
     * <p>为什么需要这张表：{@link #mergeChineseLang} 只按已注册方块和物品的名字前缀筛选，
     * 而且它是<b>整体重写</b> {@code crossversion/lang/zh_cn.json}（见 §9.15）。所以任何
     * 不长成 {@code block.minecraft.*} / {@code item.minecraft.*} 的键，手工写进那个文件
     * 都会在下一次跑本工具时被静默删掉。
     *
     * <p>{@link #referencedLangKeys()} 那条兜底路径也救不了附魔：它靠扫源码里的键<b>字面量</b>，
     * 而 1.16.4 的 {@code Enchantment.getName()} 是把注册名拼出来的，源码里没有字面量。
     *
     * <p>目前是 1.20.5+ 的四个新附魔（致密 / 破甲 / 风爆 / 突进）。
     */
    private static final String[] EXTRA_LANG_KEYS = {
            "enchantment.minecraft.density",
            "enchantment.minecraft.breach",
            "enchantment.minecraft.wind_burst",
            "enchantment.minecraft.lunge",
    };

    /** 一个官方键要不要收：命中注册对象的名字前缀，或者被本项目源码直接引用。 */
    private static boolean matches(String key, List<String> prefixes, Set<String> referenced) {
        if (referenced.contains(key)) return true;
        for (String p : prefixes) {
            if (key.equals(p) || key.startsWith(p + ".")) return true;
        }
        return false;
    }

    /**
     * 扫本项目源码，收集所有被直接引用的 {@code item.minecraft.*} / {@code block.minecraft.*} 翻译键。
     *
     * <p>为什么需要这一步：按注册对象名筛选只能捞到「物品自己的名字」及其子键，
     * 捞不到<b>多个物品共用的键</b>。锻造模板就栽在这里 —— 官方
     * {@code SmithingTemplateItem} 的 tooltip 用的是 {@code item.minecraft.smithing_template}、
     * {@code .applies_to}、{@code .ingredients}、{@code .armor_trim.*}、
     * {@code .netherite_upgrade.*} 这一族键，而注册表里根本没有叫 {@code smithing_template}
     * 的物品（19 个都叫 {@code xxx_armor_trim_smithing_template}），所以前缀匹配全部落空，
     * tooltip 会直接把键名显示给玩家。
     *
     * <p>改成扫源码，是因为「代码里写了哪个键」这件事本身就是唯一可靠的依据 ——
     * 以后任何移植类引用新的共享键，都会被自动带上，不需要有人记得来这里补一条白名单。
     */
    private static Set<String> referencedLangKeys() throws IOException {
        Path src = Paths.get("src/main/java");
        if (!Files.isDirectory(src)) return Collections.emptySet();

        Pattern keyPat = Pattern.compile("\"((?:item|block)\\.minecraft\\.[a-z0-9_.]+)\"");
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(src)) {
            for (Path p : (Iterable<Path>) walk.filter(f -> f.toString().endsWith(".java"))::iterator) {
                Matcher m = keyPat.matcher(Files.readString(p, StandardCharsets.UTF_8));
                while (m.find()) keys.add(m.group(1));
            }
        }
        System.out.printf("源码里直接引用的翻译键：%d 个%n", keys.size());
        return keys;
    }

    /**
     * 从官方 {@code zh_cn.json} 生成中文补充翻译，整体重写
     * {@code src/main/resources/crossversion/lang/zh_cn.json}。
     *
     * <p>为什么整体重写而不是像 en_us 那样只追加：这个文件完全由本工具产出，
     * 没有需要保护的手工内容，重写能保证它始终与当前注册的集合一致。之前它是手工维护的，
     * 新增一批方块后中文没跟上，表现为「一部分有中文、新加的还是英文」。
     *
     * <p>这个文件由 {@code CrossVersionLang} 在语言表构建时注入 —— 本客户端的资源包
     * lang 通道不可靠，必须走 classpath 直读，原因见那个类的注释。
     */
    private void mergeChineseLang(List<String> items, List<String> blocks) throws IOException {
        Path sourcePath = Paths.get(MCP_REBORN_ZH);
        if (!Files.exists(sourcePath)) {
            System.err.println("找不到官方中文文件，跳过中文生成：" + MCP_REBORN_ZH);
            return;
        }
        JsonObject source = GSON.fromJson(
                Files.readString(sourcePath, StandardCharsets.UTF_8), JsonObject.class);

        List<String> prefixes = new ArrayList<>();
        for (String id : blocks) prefixes.add("block.minecraft." + id);
        for (String id : items) prefixes.add("item.minecraft." + id);
        prefixes.addAll(Arrays.asList(EXTRA_LANG_KEYS));
        Set<String> referenced = referencedLangKeys();

        Map<String, String> zh = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : source.entrySet()) {
            String key = e.getKey();
            if (!e.getValue().isJsonPrimitive()) continue;
            if (matches(key, prefixes, referenced)) {
                zh.put(key, e.getValue().getAsString());
            }
        }

        if (zh.isEmpty()) {
            System.err.println("没从官方中文文件匹配到任何键，跳过");
            return;
        }

        StringBuilder sb = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<String, String> e : zh.entrySet()) {
            sb.append("  ").append(GSON.toJson(e.getKey())).append(": ").append(GSON.toJson(e.getValue()));
            sb.append(++i < zh.size() ? ",\n" : "\n");
        }
        sb.append("}\n");

        Path out = Paths.get("src/main/resources/crossversion/lang/zh_cn.json");
        if (!dryRun) {
            Files.createDirectories(out.getParent());
            Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        }
        System.out.printf("中文翻译写入 %d 个键%s%n", zh.size(), dryRun ? "（试运行）" : "");
    }

    private byte[] read(ZipEntry entry) throws IOException {
        try (InputStream in = jar.getInputStream(entry); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}
