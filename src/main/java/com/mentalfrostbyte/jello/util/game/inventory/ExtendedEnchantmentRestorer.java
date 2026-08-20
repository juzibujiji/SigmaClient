package com.mentalfrostbyte.jello.util.game.inventory;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.data.MappingDataLoader;
import com.viaversion.viaversion.api.data.Mappings;
import com.viaversion.viaversion.protocols.v1_20_3to1_20_5.data.Enchantments1_20_5;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 把被 ViaBackwards「转成灰色 lore 文本」的现代附魔还原成真正的附魔 NBT。
 *
 * <h2>Via 到底做了什么</h2>
 *
 * 1.16.4 不认识的附魔不会被静默丢掉，而是被换成一行灰色 lore。干这件事的是
 * {@code com.viaversion.viabackwards.api.rewriters.StructuredEnchantmentRewriter
 * #rewriteEnchantmentsToClient}：
 *
 * <pre>
 * int mappedId = rewriteFunction.rewrite(id);
 * if (mappedId != -1) { ...保留... }
 *
 * if (!removedEnchantments) {                       // 每个组件只备份一次
 *     CompoundTag customData = customData(data);
 *     itemRewriter.saveListTag(customData, asTag(enchantments), key.identifier());
 *     removedEnchantments = true;                   // 备份的是【完整的原始列表】
 * }
 * Tag description = descriptionSupplier.get(id, level);
 * if (description != null &amp;&amp; enchantments.showInTooltip()) {
 *     loreToAdd.add(description);                   // 灰色 lore 行
 * }
 * it.remove();                                      // 从组件里删掉
 * </pre>
 *
 * 关键在 {@code saveListTag(..., key.identifier())}：<b>原始附魔数据被完整存了下来</b>，
 * key 是 {@code "VB|<协议简名>|enchantments"} /
 * {@code "VB|<协议简名>|stored_enchantments"}，内容是
 * {@code [{id:<int>, lvl:<int>}, ...]}（{@code asTag()}，全是 IntTag）。
 * 因为它写在 {@code minecraft:custom_data} 组件里，而 {@code Protocol1_20_5To1_20_3}
 * 把 {@code custom_data} 直接当成旧版 NBT 根节点，所以这些 key 会原样出现在
 * 1.16.4 客户端拿到的物品 NBT <b>根节点</b>上 —— 和 {@code "VB|...|id"} 一样。
 *
 * <h2>链上只有两步会做这件事</h2>
 *
 * 全 jar 检索 {@code StructuredEnchantmentRewriter} 的使用者，只有两处：
 * {@code BlockItemPacketRewriter1_21}（1.21→1.20.5）与
 * {@code BlockItemPacketRewriter1_20_5}（1.20.5→1.20.3）。实测（{@code EnchantProbe}）：
 *
 * <table border="1">
 *   <caption>1.21.11 服务器下的实际归属</caption>
 *   <tr><th>附魔</th><th>丢弃于</th><th>备份 key</th><th>id 空间</th></tr>
 *   <tr><td>density / breach / wind_burst</td><td>1.20.5→1.20.3</td>
 *       <td>{@code VB|Protocol1_20_5To1_20_3|enchantments}</td>
 *       <td>{@code Enchantments1_20_5} 静态表（37/38/39）</td></tr>
 *   <tr><td>lunge（以及任何 1.21 之后新增的）</td><td>1.21→1.20.5</td>
 *       <td>{@code VB|Protocol1_21To1_20_5|enchantments}</td>
 *       <td>服务器数据驱动注册表（{@code EnchantmentsPaintingsStorage}）</td></tr>
 * </table>
 *
 * 灰色文本的名字来源也不同：1.20.5→1.20.3 用的是 ViaBackwards 自带映射数据里
 * {@code mappings-1.20.5to1.20.3.nbt} 的 {@code enchantmentnames} 段，里面写死了
 * 英文 {@code "Density"} / {@code "Breach"} / {@code "Wind Burst"}。<b>所以这三个
 * 附魔显示英文跟语言文件毫无关系</b> —— 客户端收到的本来就是字面文本，不是
 * {@code translate} 组件。
 *
 * <h2>为什么还原之后附魔效果会跟着活过来</h2>
 *
 * 因为我们写回的是<b>真正的 {@code Enchantments} NBT</b>（{@code {id:"minecraft:wind_burst",
 * lvl:1s}}），而不是显示层的补丁。1.16.4 的 {@code EnchantmentHelper#getEnchantments}
 * 从这里读，本项目的 {@code DensityEnchantment} / {@code BreachEnchantment} /
 * {@code WindBurstEnchantment} / {@code LungeEnchantment} 也都是从这里取等级的，
 * 于是中文名、附魔光效、以及砸落加成 / 护甲穿透 / 弹起 / 前冲全部一起生效。
 *
 * <h2>为什么出站不会坏</h2>
 *
 * {@code rewriteEnchantmentsToServer} 出站时是<b>整体覆盖</b>：
 *
 * <pre>
 * ListTag&lt;CompoundTag&gt; backup = removeListTag(customData, key.identifier(), CompoundTag.class);
 * if (backup == null) return;                       // 没备份就什么都不做
 * ...
 * Enchantments enchantments = new Enchantments(showInTooltip);
 * for (CompoundTag t : backup) enchantments.add(t.getInt("id"), t.getInt("lvl"));
 * dataContainer.set(key, enchantments);             // ← 完全按备份重建
 * </pre>
 *
 * lore 与附魔光效同样从 {@code VB|...|lore} / {@code nolore} / {@code glint} /
 * {@code noglint} 整体恢复。也就是说<b>备份标签才是出站时的唯一事实来源</b>，
 * 我们对客户端侧 {@code Enchantments} / {@code display.Lore} 的改动出站时会被
 * Via 原样盖掉。所以只要不动那些备份 key（本类一个都不动），出站字节与改动前一致。
 *
 * <h2>门控</h2>
 *
 * 由 {@link ExtendedItemMapper#remap} 调用，那里已经要求目标版本 &ge; 1.17；
 * 另外本类只在根节点真的存在 {@code "VB|<协议简名>|enchantments"} 这种 key 时才动手。
 * ViaRewind（1.16.4→1.8/1.12）不写这种 key，所以连 1.8 / 1.12 时是纯粹的空操作。
 */
public final class ExtendedEnchantmentRestorer {
    private static final Logger LOGGER = LogManager.getLogger();

    /** 1.21→1.20.5：备份里的 id 是服务器数据驱动注册表的 id。 */
    private static final String PROTOCOL_1_21_TO_1_20_5 =
            "com.viaversion.viabackwards.protocol.v1_21to1_20_5.Protocol1_21To1_20_5";
    /** 1.20.5→1.20.3：备份里的 id 是 {@code Enchantments1_20_5} 静态表的 id。 */
    private static final String PROTOCOL_1_20_5_TO_1_20_3 =
            "com.viaversion.viabackwards.protocol.v1_20_5to1_20_3.Protocol1_20_5To1_20_3";

    private static final String STORAGE_1_21 =
            "com.viaversion.viabackwards.protocol.v1_21to1_20_5.storage.EnchantmentsPaintingsStorage";

    /** Via 侧的组件名 -> 1.16.4 侧的 NBT key。顺序无关，两个都要处理。 */
    private static final String[][] COMPONENTS = {
        { "enchantments", "Enchantments" },
        { "stored_enchantments", "StoredEnchantments" },
    };

    private ExtendedEnchantmentRestorer() {
    }

    /**
     * 由 {@code ExtendedItemMapper#remap} 对每一个入站物品调用一次。
     * 认得出来就原地改 {@code tag}，认不出来一个字节都不动。
     *
     * @return 真的改了 NBT 时返回 {@code true}（只用于调试日志）
     */
    public static boolean restore(CompoundNBT tag) {
        try {
            boolean changed = restoreImpl(tag);
            if (changed && ExtendedItemMapper.isDebugEnabled()) {
                LOGGER.info("[ExtendedEnchant] restored Enchantments={} StoredEnchantments={}",
                        tag.getList("Enchantments", 10), tag.getList("StoredEnchantments", 10));
            }
            return changed;
        } catch (Throwable t) {
            LOGGER.debug("[ExtendedEnchant] restore failed, keeping Via's lore text: {}", t.toString());
            return false;
        }
    }

    private static boolean restoreImpl(CompoundNBT tag) {
        /*
         * 快速门：readItemStack 是热路径，绝大多数物品根本没有 "VB|...|enchantments"。
         * 先扫一遍根节点的 key（通常只有几个），扫不到就直接走掉，一次反射都不做。
         */
        if (!hasEnchantmentBackup(tag)) {
            return false;
        }

        Plan plan = null;

        for (Step step : Step.values()) {
            String prefix = step.prefix();
            if (prefix == null) {
                continue;
            }

            for (String[] component : COMPONENTS) {
                String backupKey = prefix + component[0];
                if (!tag.contains(backupKey, 9)) {
                    continue;
                }

                ListNBT backup = tag.getList(backupKey, 10);
                if (backup.isEmpty()) {
                    continue;
                }

                if (plan == null) {
                    plan = new Plan();
                }
                collect(step, tag, prefix, component[0], component[1], backup, plan);
                if (plan.abort) {
                    return false;
                }
            }
        }

        return plan != null && plan.apply(tag);
    }

    private static boolean hasEnchantmentBackup(CompoundNBT tag) {
        for (String key : tag.keySet()) {
            if (key.startsWith("VB|")
                    && (key.endsWith("|enchantments") || key.endsWith("|stored_enchantments"))) {
                return true;
            }
        }

        return false;
    }

    /**
     * 把一个备份列表里「被 Via 丢掉的」条目挑出来，同时数清 Via 因此往 lore 头部
     * 插了几行。任何一步无法判断或本地注册表里没有，都把整次还原判定为放弃 ——
     * 数不准 lore 行数就不能去删 lore，宁可保持现状。
     */
    private static void collect(Step step, CompoundNBT tag, String prefix, String viaKey, String nbtKey,
            ListNBT backup, Plan plan) {
        /*
         * Via 只在 showInTooltip 为真时才插 lore，也只在那时写这个记账 key。
         * 注意 key 名不是 "<组件名>_show_in_tooltip" 而是 "show_<组件名>" ——
         * 字节码里的字符串拼接 recipe 是 "show_"（StructuredEnchantmentRewriter
         * 的 BootstrapMethods #2），出站 rewriteEnchantmentsToServer 读的也是同一个 key。
         */
        boolean showInTooltip = tag.contains(prefix + "show_" + viaKey);

        for (int i = 0; i < backup.size(); i++) {
            CompoundNBT entry = backup.getCompound(i);
            int viaId = entry.getInt("id");
            int level = entry.getInt("lvl");

            String identifier = step.identifier(viaId);
            if (identifier == null) {
                // 解析不出标识符 —— 连「Via 有没有丢它」都判断不了，放弃。
                plan.abort = true;
                return;
            }

            if (!step.wasDropped(identifier, viaId)) {
                continue; // Via 保留了它，客户端 NBT 里已经有，不能重复添加
            }

            /*
             * 被丢弃的附魔一定对应一行 lore（只要 showInTooltip 为真）：两个
             * descriptionSupplier 都是「全函数」，不会返回 null ——
             *   1.21→1.20.5   拿不到注册表描述时返回 new StringTag("Unknown enchantment")；
             *   1.20.5→1.20.3 直接把 mappedEnchantmentName(id) 拼进 "§7"，
             *                 名字为 null 也只是拼出 "§7null"，Tag 本身非 null。
             */
            if (showInTooltip) {
                plan.loreLines++;
            }

            Enchantment enchantment = localEnchantment(identifier);
            if (enchantment == null) {
                // 本地也没有这个附魔（例如数据包自定义附魔）。Via 的文本是它唯一的
                // 表现形式，必须留着；而留着就意味着不能按行数裁 lore，整次放弃。
                plan.abort = true;
                return;
            }

            plan.add(nbtKey, Registry.ENCHANTMENT.getKey(enchantment).toString(), level);
        }
    }

    /**
     * 按标识符查本地附魔注册表。刻意<b>不</b>走 {@code ModernRegistry.normalize} ——
     * 那张表是物品 / 方块的改名映射，套到附魔标识符上只会引入误伤的可能。
     * 这四个扩展附魔（density / breach / wind_burst / lunge）本来就是官方名字，直接查即可。
     */
    private static Enchantment localEnchantment(String identifier) {
        String qualified = identifier.indexOf(':') < 0 ? "minecraft:" + identifier : identifier;
        ResourceLocation key = ResourceLocation.tryCreate(qualified);
        return key == null ? null : Registry.ENCHANTMENT.getOptional(key).orElse(null);
    }

    // ------------------------------------------------------------------
    // 改 NBT
    // ------------------------------------------------------------------

    private static final class Plan {
        private final List<String> nbtKeys = new ArrayList<>();
        private final List<String> identifiers = new ArrayList<>();
        private final List<Integer> levels = new ArrayList<>();
        private int loreLines;
        private boolean abort;

        private void add(String nbtKey, String identifier, int level) {
            this.nbtKeys.add(nbtKey);
            this.identifiers.add(identifier);
            this.levels.add(level);
        }

        private boolean apply(CompoundNBT tag) {
            if (this.abort || this.identifiers.isEmpty()) {
                return false;
            }

            // 先把 Via 插进 lore 的那几行摘掉。摘不干净就整次放弃，绝不留个半截状态。
            if (this.loreLines > 0 && !trimLore(tag, this.loreLines)) {
                return false;
            }

            for (int i = 0; i < this.identifiers.size(); i++) {
                putEnchantment(tag, this.nbtKeys.get(i), this.identifiers.get(i), this.levels.get(i));
            }

            return true;
        }
    }

    /**
     * Via 的 lore 行永远是<b>插在最前面</b>的（{@code loreToAdd} 先装描述，
     * 再 {@code addAll(原有 lore)}，最后整体 set 回 LORE 组件），所以按行数从头删即可。
     * 行数对不上就返回 {@code false}，一行都不动。
     */
    private static boolean trimLore(CompoundNBT tag, int lines) {
        if (!tag.contains("display", 10)) {
            return false;
        }

        CompoundNBT display = tag.getCompound("display");
        if (!display.contains("Lore", 9)) {
            return false;
        }

        ListNBT lore = display.getList("Lore", 8);
        if (lore.size() < lines) {
            return false;
        }

        for (int i = 0; i < lines; i++) {
            lore.remove(0);
        }

        if (lore.isEmpty()) {
            display.remove("Lore");
            if (display.isEmpty()) {
                tag.remove("display");
            }
        }

        return true;
    }

    private static void putEnchantment(CompoundNBT tag, String nbtKey, String identifier, int level) {
        ListNBT list;
        INBT existing = tag.get(nbtKey);
        if (existing == null) {
            list = new ListNBT();
            tag.put(nbtKey, list);
        } else if (existing instanceof ListNBT
                && (((ListNBT) existing).isEmpty() || ((ListNBT) existing).getTagType() == 10)) {
            list = (ListNBT) existing;
        } else {
            return; // 形状不是「compound 列表」，不是我们认识的东西，别碰
        }

        for (int i = list.size() - 1; i >= 0; i--) {
            String present = list.getCompound(i).getString("id");
            if (present.isEmpty()) {
                // StructuredDataConverter 为了在 1.16.4 上保住附魔光效，会在附魔被清空时
                // 塞一个 {id:""} 占位项（enchantment_glint_override 的降级实现）。
                // 现在有真附魔了，占位项要去掉，否则会多出一行空附魔名。
                list.remove(i);
            } else if (present.equals(identifier)) {
                return; // 已经有了
            }
        }

        CompoundNBT entry = new CompoundNBT();
        entry.putString("id", identifier);
        entry.putShort("lvl", (short) level);
        list.add(entry);
    }

    // ------------------------------------------------------------------
    // 降级链上的两步
    // ------------------------------------------------------------------

    private enum Step {
        /** 1.21→1.20.5：id 属于服务器发来的数据驱动附魔注册表。 */
        V1_21_TO_1_20_5(PROTOCOL_1_21_TO_1_20_5) {
            @Override
            String identifier(int viaId) {
                Object storage = storage();
                if (storage == null) {
                    return null;
                }

                try {
                    Object keyMappings = storage.getClass().getMethod("enchantments").invoke(storage);
                    if (keyMappings == null) {
                        return null;
                    }
                    Object key = keyMappings.getClass().getMethod("idToKey", int.class)
                            .invoke(keyMappings, viaId);
                    return key instanceof String ? (String) key : null;
                } catch (Exception e) {
                    return null;
                }
            }

            @Override
            boolean wasDropped(String identifier, int viaId) {
                // Via 的判定就是这一句：Enchantments1_20_5.keyToId(服务器给的标识符) == -1
                return staticEnchantmentId(identifier) == -1;
            }
        },

        /** 1.20.5→1.20.3：id 属于 {@code Enchantments1_20_5} 静态表。 */
        V1_20_5_TO_1_20_3(PROTOCOL_1_20_5_TO_1_20_3) {
            @Override
            String identifier(int viaId) {
                return Enchantments1_20_5.idToKey(viaId);
            }

            @Override
            boolean wasDropped(String identifier, int viaId) {
                // Via 在这一步的判定就是这一句：getEnchantmentMappings().getNewId(id) == -1
                Mappings mappings = Data1_20_5To1_20_3.enchantmentMappings();
                // 映射表读不出来就当「没丢」—— collect() 因此不会还原它，比乱猜安全。
                return mappings != null && mappings.getNewId(viaId) == -1;
            }
        };

        private final String protocolClassName;
        /** key 前缀是进程内的常量，第一次算出来之后缓存，避免热路径反复反射。 */
        private volatile String cachedPrefix;

        Step(String protocolClassName) {
            this.protocolClassName = protocolClassName;
        }

        abstract String identifier(int viaId);

        abstract boolean wasDropped(String identifier, int viaId);

        /**
         * 优先问 Via 自己要 key 前缀（{@code getItemRewriter().nbtTagName("")}），
         * 拿不到就按 {@code "VB|" + 协议类简名 + "|"} 兜底 —— 这就是 ViaBackwards
         * {@code nbtTagName()} 的实现（{@code BackwardsItemRewriterBase} 与
         * {@code BackwardsStructuredItemRewriter} 都是 {@code "VB|" + 协议简名}，
         * 字节码里的 recipe 常量是 {@code "VB|"}）。
         *
         * <p>刻意<b>不</b>因为「协议没加载」就返回 {@code null}：兜底前缀拼出来的 key
         * 只要 NBT 里没有就什么都不会发生，反倒省掉一个对 Via 初始化时机的依赖。
         */
        String prefix() {
            String cached = this.cachedPrefix;
            if (cached != null) {
                return cached;
            }

            String prefix = null;
            try {
                Object protocol = Via.getManager().getProtocolManager()
                        .getProtocol(protocolClass(this.protocolClassName));
                if (protocol != null) {
                    Object rewriter = protocol.getClass().getMethod("getItemRewriter").invoke(protocol);
                    if (rewriter != null) {
                        Object key = rewriter.getClass().getMethod("nbtTagName", String.class)
                                .invoke(rewriter, "");
                        if (key instanceof String && ((String) key).endsWith("|")) {
                            prefix = (String) key;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }

            if (prefix == null) {
                int dot = this.protocolClassName.lastIndexOf('.');
                prefix = "VB|" + this.protocolClassName.substring(dot + 1) + "|";
            }

            this.cachedPrefix = prefix;
            return prefix;
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Class protocolClass(String name) throws ClassNotFoundException {
        return Class.forName(name);
    }

    /**
     * {@code mappings-1.20.5to1.20.3.nbt} 里的 {@code enchantments} 映射 —— 和
     * {@code BackwardsMappingData#getEnchantmentMappings()} 读的<b>完全是同一个文件、
     * 同一个段</b>（{@code MappingDataBase#loadMappings(data, "enchantments")}）。
     *
     * <p>刻意直接读文件而不去问活着的协议实例：数据一致，而且不依赖 Via 的初始化时机
     * （这也让 {@code RestorerHarness} 那套用例能脱离 Via 运行时跑起来）。
     */
    private static final class Data1_20_5To1_20_3 {
        private static volatile boolean loaded;
        private static volatile Mappings enchantments;

        private Data1_20_5To1_20_3() {
        }

        static Mappings enchantmentMappings() {
            load();
            return enchantments;
        }

        private static synchronized void load() {
            if (loaded) {
                return;
            }
            loaded = true;

            try {
                MappingDataLoader loader = new MappingDataLoader(
                        Class.forName("com.viaversion.viabackwards.api.ViaBackwardsPlatform"),
                        "assets/viabackwards/data/");
                CompoundTag data = loader.loadNBT("mappings-1.20.5to1.20.3.nbt");
                if (data == null) {
                    return;
                }
                enchantments = loader.loadMappings(data, "enchantments");
            } catch (Throwable t) {
                LOGGER.warn("[ExtendedEnchant] Could not read mappings-1.20.5to1.20.3.nbt: {}", t.toString());
            }
        }
    }

    /** {@code Enchantments1_20_5.keyToId}，找不到返回 -1。 */
    private static int staticEnchantmentId(String identifier) {
        String path = identifier.startsWith("minecraft:")
                ? identifier.substring("minecraft:".length()) : identifier;
        return Enchantments1_20_5.keyToId(path);
    }

    /** 当前那一条 Via 连接上的 {@code EnchantmentsPaintingsStorage}。 */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Object storage() {
        try {
            Class storageClass = Class.forName(STORAGE_1_21);
            Set<UserConnection> connections = Via.getManager().getConnectionManager().getConnections();
            for (UserConnection connection : connections) {
                Object storage = connection.get(storageClass);
                if (storage != null) {
                    return storage;
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }
}
