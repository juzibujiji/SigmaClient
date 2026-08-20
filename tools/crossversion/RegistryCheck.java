package verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ModernBlocks;
import net.minecraft.crossversion.ModernRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTier;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.item.ModernItems;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Bootstrap;
import net.minecraft.util.registry.Registry;

/** 验证跨版本扩展没有让任何原版 ID 移位。 */
public class RegistryCheck {
    static int failures = 0;

    public static void main(String[] args) throws Exception {
        Bootstrap.register();

        List<String> baseItems  = Files.readAllLines(Paths.get(args[0]));
        List<String> baseBlocks = Files.readAllLines(Paths.get(args[1]));

        System.out.println("=== 注册表规模 ===");
        System.out.printf("物品   本地=%d  原版基准=%d%n", Registry.ITEM.keySet().size(), baseItems.size());
        System.out.printf("方块   本地=%d  原版基准=%d%n", Registry.BLOCK.keySet().size(), baseBlocks.size());
        System.out.printf("方块状态 本地=%d  原版基准=%d%n",
                Block.BLOCK_STATE_IDS.size(), ModernRegistry.VANILLA_BLOCK_STATE_COUNT);

        System.out.println("\n=== 原版 ID 未移位检查 ===");
        checkOrder("物品", baseItems, id -> {
            Item it = Registry.ITEM.getByValue(id);
            return it == null ? null : Registry.ITEM.getKey(it).getPath();
        });
        checkOrder("方块", baseBlocks, id -> {
            Block b = Registry.BLOCK.getByValue(id);
            return b == null ? null : Registry.BLOCK.getKey(b).getPath();
        });

        System.out.println("\n=== 扩展内容 ===");
        for (Item it : ModernRegistry.extendedItems()) {
            ResourceLocation k = Registry.ITEM.getKey(it);
            int id = Registry.ITEM.getId(it);
            boolean afterVanilla = id >= ModernRegistry.VANILLA_ITEM_COUNT;
            System.out.printf("  物品 %-18s id=%-5d %s%n", k.getPath(), id, afterVanilla ? "OK" : "**在原版区间内**");
            if (!afterVanilla) failures++;
        }
        for (Block b : ModernRegistry.extendedBlocks()) {
            int id = Registry.BLOCK.getId(b);
            boolean afterVanilla = id >= ModernRegistry.VANILLA_BLOCK_COUNT;
            System.out.printf("  方块 %-18s id=%-5d %s", Registry.BLOCK.getKey(b).getPath(), id,
                    afterVanilla ? "OK" : "**在原版区间内**");
            if (!afterVanilla) failures++;
            List<Integer> stateIds = new ArrayList<>();
            for (BlockState st : b.getStateContainer().getValidStates()) stateIds.add(Block.getStateId(st));
            System.out.printf("  方块状态=%s%n", stateIds);
            for (int sid : stateIds)
                if (sid < ModernRegistry.VANILLA_BLOCK_STATE_COUNT) {
                    System.out.println("    ** 方块状态 id " + sid + " 落在原版区间内");
                    failures++;
                }
        }

        System.out.println("\n=== 标识符映射（跨版本用） ===");
        for (String probe : new String[]{"deepslate", "minecraft:mace", "copper_pickaxe", "short_grass", "dirt_path"}) {
            Item it = ModernRegistry.itemByModernId(probe);
            System.out.printf("  %-16s -> %s%n", probe,
                    it == null ? "(未找到)" : Registry.ITEM.getKey(it) + " id=" + Registry.ITEM.getId(it));
        }

        System.out.println("\n=== 方块状态位宽 ===");
        int bits = 32 - Integer.numberOfLeadingZeros(Block.BLOCK_STATE_IDS.size() - 1);
        System.out.printf("  总数=%d 需要 %d bit（原版 17112 为 15 bit，超过 32768 会变 16 bit 并与跨版本区块数据错位）%n",
                Block.BLOCK_STATE_IDS.size(), bits);
        if (Block.BLOCK_STATE_IDS.size() > 32768) failures++;

        checkHardnessAndTools();
        checkNoRenamedDuplicates();
        exportRegisteredLists();

        System.out.println(failures == 0 ? "\n结果：全部通过" : "\n结果：" + failures + " 项失败");
        if (failures != 0) System.exit(1);
    }

    /**
     * 改名项不能被注册成新条目。
     *
     * <p>{@code short_grass} / {@code dirt_path} / {@code turtle_scute} / {@code iron_chain}
     * 是 1.16.4 的 {@code grass} / {@code grass_path} / {@code scute} / {@code chain} 换了名字，
     * 由 {@link ModernRegistry#normalize} 映射回去。真把它们注册成新条目，创造栏里就会出现
     * 两个一模一样的东西 —— 编译期不报错，也不影响进世界，只有把创造栏逐项列出来才看得见。
     * 之前生成器自己维护的改名清单与 {@code ModernRegistry} 脱节，{@code short_grass}
     * 与 {@code turtle_scute} 就是这样漏进去的。
     */
    static void checkNoRenamedDuplicates() {
        System.out.println("\n=== 改名项不得注册成新条目 ===");
        Map<String, String> renamed;
        try {
            renamed = renamedFromSource();
        } catch (IOException e) {
            System.out.println("  ** 读取 ModernRegistry 改名表失败：" + e);
            failures++;
            return;
        }

        List<String> dupes = new ArrayList<>();
        for (Map.Entry<String, String> e : renamed.entrySet()) {
            ResourceLocation key = new ResourceLocation(e.getKey());
            if (Registry.ITEM.keySet().contains(key)) dupes.add("物品 " + e.getKey() + "（应为 " + e.getValue() + "）");
            if (Registry.BLOCK.keySet().contains(key)) dupes.add("方块 " + e.getKey() + "（应为 " + e.getValue() + "）");
        }
        System.out.printf("  检查 %d 项改名映射：%d 个被误注册 %s%n",
                renamed.size(), dupes.size(), dupes.isEmpty() ? "OK" : "**");
        dupes.forEach(d -> System.out.println("    ** " + d));
        if (!dupes.isEmpty()) failures++;
    }

    /** 从 {@code ModernRegistry} 源码解析改名表，避免在检查里再抄一份。 */
    static Map<String, String> renamedFromSource() throws IOException {
        Path src = Paths.get("src/main/java/net/minecraft/crossversion/ModernRegistry.java");
        Map<String, String> out = new LinkedHashMap<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                        "RENAMED_TO_LEGACY\\.put\\(\\s*\"([a-z0-9_]+)\"\\s*,\\s*\"([a-z0-9_]+)\"\\s*\\)")
                .matcher(Files.readString(src, StandardCharsets.UTF_8));
        while (m.find()) out.put(m.group(1), m.group(2));
        return out;
    }

    /**
     * 导出当前实际注册的方块与物品清单，供数据包检查使用。
     *
     * <p><b>必须每次跑都重新导出。</b>这两个文件原先是手工生成的快照，新增一批方块后
     * 忘了更新，数据包检查就拿着过期清单把「已注册」的物品报成「未注册」 ——
     * 68 条假告警，全是清单陈旧导致的。从活的注册表导出才不会再错。
     */
    static void exportRegisteredLists() throws Exception {
        Path dir = Paths.get("target/crossversion-check");
        Files.createDirectories(dir);

        List<String> blocks = new ArrayList<>();
        for (Block block : Registry.BLOCK) {
            blocks.add(Registry.BLOCK.getKey(block).getPath());
        }
        Collections.sort(blocks);
        Files.write(dir.resolve("reg-blocks.txt"), blocks);

        List<String> items = new ArrayList<>();
        for (Item item : Registry.ITEM) {
            items.add(Registry.ITEM.getKey(item).getPath());
        }
        Collections.sort(items);
        Files.write(dir.resolve("registered-items.txt"), items);

        System.out.printf("%n已导出注册清单：%d 个方块、%d 个物品%n", blocks.size(), items.size());
    }

    /**
     * 验证硬度与工具性质是否与官方 1.21.11 一致。
     *
     * <p>这是整个工程的核心诉求：跨版本时深板岩被 ViaBackwards 降级成黑石（硬度 1.5），
     * 导致挖掘速度预测错误。扩展后应当拿到官方的 3.0。
     */
    static void checkHardnessAndTools() {
        System.out.println("\n=== 硬度与工具性质（对照官方 1.21.11） ===");

        BlockState deepslate = ModernBlocks.DEEPSLATE.getDefaultState();
        expect("deepslate 硬度", deepslate.getBlockHardness(null, null), 3.0F);
        expect("deepslate 爆炸抗性", ModernBlocks.DEEPSLATE.getExplosionResistance(), 6.0F);
        expect("blackstone 硬度（对照：降级后会用这个值）",
                Blocks.BLACKSTONE.getDefaultState().getBlockHardness(null, null), 1.5F);

        // 挖掘速度：官方 tool 组件的 speed 值。铜 5.0 介于石头 4.0 与铁 6.0 之间。
        expect("copper_pickaxe 挖 deepslate 速度",
                new ItemStack(ModernItems.COPPER_PICKAXE).getDestroySpeed(deepslate), 5.0F);
        expect("stone_pickaxe 挖 deepslate 速度",
                new ItemStack(Items.STONE_PICKAXE).getDestroySpeed(deepslate), 4.0F);
        expect("iron_pickaxe 挖 deepslate 速度",
                new ItemStack(Items.IRON_PICKAXE).getDestroySpeed(deepslate), 6.0F);

        // 能否挖出掉落物。deepslate 设了 requiresTool，任意等级的镐都应当可以。
        expectBool("copper_pickaxe 可挖出 deepslate",
                new ItemStack(ModernItems.COPPER_PICKAXE).canHarvestBlock(deepslate), true);

        // 铜工具的 tier 参数
        expect("COPPER 耐久", (float) ItemTier.COPPER.getMaxUses(), 190.0F);
        expect("COPPER 挖掘速度", ItemTier.COPPER.getEfficiency(), 5.0F);
        expect("COPPER 附魔能力", (float) ItemTier.COPPER.getEnchantability(), 13.0F);
        expect("COPPER 挖掘等级（与石头同级）", (float) ItemTier.COPPER.getHarvestLevel(), 1.0F);
        expect("copper_pickaxe 耐久", (float) ModernItems.COPPER_PICKAXE.getMaxDamage(), 190.0F);

        // 重锤：攻击力 5.0、攻速 -3.4、耐久 500、附魔 15，全部来自官方 items.json。
        expect("mace 耐久", (float) ModernItems.MACE.getMaxDamage(), 500.0F);
        expect("mace 附魔能力", (float) ModernItems.MACE.getItemEnchantability(), 15.0F);
        expect("mace 攻击力", (float) MaceItem.ATTACK_DAMAGE, 5.0F);
        expect("mace 攻速修正", (float) MaceItem.ATTACK_SPEED, -3.4F);

        // 砸落伤害分段：前 3 格每格 4、之后 5 格每格 2、再往上每格 1。
        expect("mace 砸落 1.0 格（未达阈值）", MaceItem.getSmashDamageBonus(1.0F), 0.0F);
        expect("mace 砸落 3.0 格", MaceItem.getSmashDamageBonus(3.0F), 12.0F);
        expect("mace 砸落 8.0 格", MaceItem.getSmashDamageBonus(8.0F), 22.0F);
        expect("mace 砸落 10.0 格", MaceItem.getSmashDamageBonus(10.0F), 24.0F);

        checkCreativeOrder();
        checkBlockStateModels();
    }

    /**
     * 验证每个扩展方块的实际状态属性与它的 blockstate json 变体<b>逐一对得上</b>。
     *
     * <p>这是紫黑块的直接成因：blockstate json 为每个状态组合指定模型，方块提供不了同一组
     * 属性时，那些变体永远匹配不到，游戏里就渲染成紫黑。之前铜灯与樱花树叶就是这么坏的，
     * 而这种错误编译期完全看不出来。
     *
     * <p>做法：把方块的每个 BlockState 拼成 variant key（属性名按字母序，与 Mojang 的
     * blockstate json 一致），确认它在 json 的 variants 里存在。multipart 形式按条件
     * 匹配而非枚举，跳过。
     */
    static void checkBlockStateModels() {
        System.out.println("\n=== 方块状态与模型变体匹配（紫黑块检查） ===");
        Path assetsRoot = Paths.get("src/main/resources/assets/minecraft/blockstates");
        int checked = 0, missingFile = 0, multipart = 0, mismatch = 0;
        List<String> bad = new ArrayList<>();

        for (Block block : ModernRegistry.extendedBlocks()) {
            String name = Registry.BLOCK.getKey(block).getPath();
            Path json = assetsRoot.resolve(name + ".json");
            if (!Files.exists(json)) {
                missingFile++;
                bad.add(name + " 缺 blockstate 文件");
                continue;
            }
            try {
                com.google.gson.JsonObject root = com.google.gson.JsonParser
                        .parseString(Files.readString(json, java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject();
                if (!root.has("variants")) {
                    multipart++;
                    continue;
                }
                Set<String> keys = root.getAsJsonObject("variants").keySet();
                checked++;

                for (BlockState state : block.getStateContainer().getValidStates()) {
                    if (!hasMatchingVariant(state, keys)) {
                        bad.add(name + " 状态 [" + variantKey(state) + "] 无对应模型变体");
                        mismatch++;
                        break;
                    }
                }
            } catch (Exception e) {
                bad.add(name + " 解析失败: " + e.getMessage());
                mismatch++;
            }
        }

        System.out.printf("  检查 %d 个方块（%d 个 multipart 跳过）：%d 个缺文件，%d 个变体不匹配 %s%n",
                checked, multipart, missingFile, mismatch,
                (missingFile + mismatch) == 0 ? "OK" : "**");
        bad.stream().limit(10).forEach(b -> System.out.println("    ** " + b));
        failures += missingFile + mismatch;
    }

    /**
     * 判断方块状态能否匹配到某个模型变体。
     *
     * <p>Mojang 的 blockstate variant key 是<b>条件子集</b>而不是状态全描述：只列出会改变
     * 模型的属性，其余忽略。例如 {@code tuff_slab.json} 只有 {@code type=bottom/double/top}，
     * 不含 {@code waterlogged}（含水不改变外观）；{@code cherry_fence_gate.json} 是
     * {@code facing=east,in_wall=false,open=false}，不含 {@code powered}。
     * 所以匹配是「key 的每个条件都被状态满足」，不是相等。
     */
    static boolean hasMatchingVariant(BlockState state, Set<String> keys) {
        Map<String, String> actual = new HashMap<>();
        for (net.minecraft.state.Property<?> p : state.getProperties()) {
            actual.put(p.getName(), valueName(state, p));
        }

        for (String key : keys) {
            if (key.isEmpty()) {
                return true; // 空 key 匹配任意状态
            }
            boolean allMatch = true;
            for (String cond : key.split(",")) {
                int eq = cond.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                if (!cond.substring(eq + 1).equals(actual.get(cond.substring(0, eq)))) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                return true;
            }
        }
        return false;
    }

    /** 把方块状态拼成完整描述，仅用于报错时展示。 */
    static String variantKey(BlockState state) {
        List<String> parts = new ArrayList<>();
        for (net.minecraft.state.Property<?> p : state.getProperties()) {
            parts.add(p.getName() + "=" + valueName(state, p));
        }
        Collections.sort(parts);
        return String.join(",", parts);
    }

    @SuppressWarnings("unchecked")
    static <T extends Comparable<T>> String valueName(BlockState state, net.minecraft.state.Property<?> p) {
        net.minecraft.state.Property<T> typed = (net.minecraft.state.Property<T>) p;
        return typed.getName(state.get(typed));
    }

    /**
     * 验证创造栏按<b>官方 1.21.11 创造栏</b>排列，而不是把新物品堆在末尾。
     *
     * <p>注意排序依据已经从 protocol_id（官方注册顺序）换成官方创造栏的
     * tab 顺序 + tab 内序号 —— 两者并不相同，这是「分类对了但位置不对」的根因。
     */
    static void checkCreativeOrder() {
        System.out.println("\n=== 创造栏顺序（应按官方创造栏排列，而非 raw ID） ===");
        List<Item> order = ModernRegistry.creativeOrder();
        System.out.printf("  排序表长度=%d  注册表大小=%d%n", order.size(), Registry.ITEM.keySet().size());
        if (order.size() != Registry.ITEM.keySet().size()) {
            System.out.println("  ** 排序表与注册表数量不一致，会导致创造栏漏物品");
            failures++;
        }

        int deepslateAt = order.indexOf(ModernItems.DEEPSLATE);
        int stoneAt = order.indexOf(Items.STONE);
        System.out.printf("  stone 位置=%d   deepslate 位置=%d   总数=%d%n", stoneAt, deepslateAt, order.size());

        // 官方 building 栏把 deepslate 放在 andesite 家族之后、同一段石料区里。
        // 排序若没生效，deepslate 会落到 raw ID 976 对应的末尾位置（本地扩展区的开头），
        // 与 stone 相隔近千项。阈值不写死成小常数 —— 创造栏顺序里石料区本身就有几十项。
        boolean nearStone = deepslateAt > stoneAt && deepslateAt - stoneAt <= 64;
        System.out.printf("  deepslate 是否与 stone 同处石料区（间隔 %d）：%s %s%n",
                deepslateAt - stoneAt, nearStone, nearStone ? "OK" : "** 仍堆在末尾，排序未生效");
        if (!nearStone) failures++;

        int maceAt = order.indexOf(ModernItems.MACE);
        int tridentAt = order.indexOf(Items.TRIDENT);
        // 官方 combat 栏里 mace 紧跟 trident；这条能挡住「顺序表没跟着 CSV 重新生成」。
        boolean maceAfterTrident = maceAt == tridentAt + 1;
        System.out.printf("  mace 紧跟 trident（trident=%d mace=%d）：%s %s%n",
                tridentAt, maceAt, maceAfterTrident, maceAfterTrident ? "OK" : "** 官方 combat 栏里两者相邻");
        if (!maceAfterTrident) failures++;

        checkOrderMatchesSource(order);
    }

    /**
     * 顺序表必须与源数据 {@code docs/registry-diff/official-creative-tabs-1.21.11.csv} 一致。
     *
     * <p>挡的是「改了 CSV 但没重跑 GenerateItems」这类问题：打进 jar 的
     * {@code crossversion/item-order-1.21.11.txt} 是生成物，跟源 CSV 脱节不会在编译期暴露，
     * 表现只是创造栏顺序悄悄不对。
     */
    static void checkOrderMatchesSource(List<Item> order) {
        Path csv = Paths.get("docs/registry-diff/official-creative-tabs-1.21.11.csv");
        if (!Files.exists(csv)) {
            System.out.println("  跳过与源 CSV 的一致性核对（找不到 " + csv + "）");
            return;
        }
        List<String> expected = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try {
            for (String line : Files.readAllLines(csv, StandardCharsets.UTF_8)) {
                String[] parts = line.trim().split(",");
                if (parts.length != 3) continue;
                // 只保留本客户端真的注册了的，且按首次出现去重（与生成器同一规则）
                if (ModernRegistry.itemByModernId(parts[2]) != null && seen.add(parts[2])) {
                    expected.add(parts[2]);
                }
            }
        } catch (IOException e) {
            System.out.println("  ** 读取源 CSV 失败：" + e);
            failures++;
            return;
        }

        int mismatchAt = -1;
        for (int i = 0; i < expected.size() && i < order.size(); i++) {
            if (order.get(i) != ModernRegistry.itemByModernId(expected.get(i))) {
                mismatchAt = i;
                break;
            }
        }
        boolean ok = mismatchAt < 0 && order.size() >= expected.size();
        System.out.printf("  与源 CSV 一致（前 %d 项来自官方 tab）：%s %s%n", expected.size(), ok,
                ok ? "OK" : "** 第 " + mismatchAt + " 项应为 " + expected.get(Math.max(mismatchAt, 0))
                        + "，实际是 " + Registry.ITEM.getKey(order.get(Math.max(mismatchAt, 0)))
                        + "（顺序表没跟着 CSV 重新生成？）");
        if (!ok) failures++;
    }

    static void expect(String label, float actual, float want) {
        boolean ok = Math.abs(actual - want) < 1.0E-4F;
        System.out.printf("  %-45s %-8s %s%n", label, actual, ok ? "OK" : "** 期望 " + want);
        if (!ok) failures++;
    }

    static void expectBool(String label, boolean actual, boolean want) {
        System.out.printf("  %-45s %-8s %s%n", label, actual, actual == want ? "OK" : "** 期望 " + want);
        if (actual != want) failures++;
    }

    interface Lookup { String get(int id); }

    static void checkOrder(String label, List<String> baseline, Lookup lookup) {
        int mismatch = 0;
        for (int id = 0; id < baseline.size(); id++) {
            String expect = baseline.get(id), actual = lookup.get(id);
            if (!expect.equals(actual)) {
                if (mismatch < 5) System.out.printf("  ** %s id=%d 期望 %s 实际 %s%n", label, id, expect, actual);
                mismatch++;
            }
        }
        System.out.printf("  %s 0-%d：%s%n", label, baseline.size() - 1,
                mismatch == 0 ? "全部一致 OK" : mismatch + " 项不一致");
        failures += mismatch;
    }
}
