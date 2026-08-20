package com.mentalfrostbyte.jello.util.game.inventory;

import com.mentalfrostbyte.jello.gui.base.JelloPortal;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.crossversion.ModernRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 把被 ViaBackwards 降级掉的现代物品还原成本地注册表里真正的那一个。
 *
 * <h2>为什么不需要在 Via 之前拦字节流</h2>
 *
 * 方块那侧必须用 {@code ChunkDataInterceptor} 在 Via 之前抓原始包，因为方块状态 id
 * 被改掉之后原始身份就彻底丢了。物品不一样 —— ViaBackwards 在替换物品时会<b>先把原始
 * id 存进 NBT</b>，再改 id：
 *
 * <pre>
 * // com.viaversion.viabackwards.api.rewriters.BackwardsItemRewriter#handleItemToClient
 * item.tag().putInt(nbtTagName("id"), item.identifier());   // 先存
 * item.setIdentifier(mappedItem.id());                      // 后替换
 * </pre>
 *
 * {@code BackwardsStructuredItemRewriter}（1.20.5+ 数据组件那一侧）用同一套 key，
 * 存在 {@code custom_data} 组件里；{@code Protocol1_20_5To1_20_3} 把 {@code custom_data}
 * 落回物品 NBT 根节点，所以标签能一路活到 1.16.4。
 *
 * <p>{@code nbtTagName()} = {@code "VB|" + 协议类简名}，{@code nbtTagName("id")} 再接
 * {@code "|id"}，于是 key 形如 {@code "VB|Protocol1_20_5To1_20_3|id"}。这个 key 本身
 * 就指明了是哪一步做的替换，而那一步的<b>源版本</b> identifiers 表可以把存下来的 id
 * 还原成标识符，再交给 {@link ModernRegistry#itemByModernId(String)} 查本地注册表。
 *
 * <p>实测（{@code docs/registry-diff/ItemTagProbe.java}）：1.16.4→1.21.11 新增的 533 个
 * 物品里 <b>530 个</b>带可还原的标签，剩下 3 个是 {@code short_grass} / {@code iron_chain} /
 * {@code turtle_scute} —— 它们只是改名，Via 直接做 id 重映射，本来就落到正确的本地物品上。
 * <b>没有任何一个物品被整包丢弃。</b>
 *
 * <h2>为什么连 1.8 / 1.12 时零影响</h2>
 *
 * 三层门控，任意一层不满足就原样返回服务器给的物品：
 * <ol>
 *   <li>{@link #isActive()} 要求目标版本 &ge; 1.17。1.8 / 1.12 / 1.16.4 直接短路，
 *       连映射链都不会去加载。</li>
 *   <li>物品必须带 NBT，且根节点里必须有 {@code "VB|...|id"} 形式的 key，且该 key
 *       属于当前降级链中的某一步。ViaRewind（1.16.4→1.8/1.12）不写这种 key。</li>
 *   <li>还原出的标识符必须在本地注册表里真的存在。</li>
 * </ol>
 *
 * <h2>发回服务器时怎么办</h2>
 *
 * 换成本地物品之后，它的 raw id（&ge; 976）对服务器是无意义的。所以
 * {@link #wireId(ItemStack)} 会把出站 id 换回 Via 原本给的那个降级 id ——
 * 与改动前<b>逐字节一致</b>，Via 的 {@code handleItemToServer} 照旧从
 * {@code "VB|...|id"} 标签里恢复真正的物品。
 *
 * <h2>NBT 只动两处，其余一个字节不改</h2>
 *
 * <ol>
 *   <li><b>占位名</b>：Via 给降级物品贴的 {@code display.Name}（{@code "§f1.21 Mace"}）
 *       连同它的「是我加的」标记一起删掉，见 {@link #clearPlaceholderName}。那里详细
 *       论证了为什么删掉之后 Via 的出站 {@code restoreDisplayTag} 结果不变。</li>
 *   <li><b>被转成灰色 lore 文本的现代附魔</b>：还原成真正的 {@code Enchantments} NBT，
 *       见 {@link ExtendedEnchantmentRestorer}。</li>
 * </ol>
 *
 * Damage / CustomModelData / 服务器自己设的名字与 lore / 所有 {@code "VB|...|id"}
 * 记账标签全部原样通过。
 */
public final class ExtendedItemMapper {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String DEBUG_PROPERTY = "sigma.viamcp.debugItemMapping";

    /** ViaBackwards 写入的 key 前缀与后缀，用于在 NBT 根节点里快速筛出候选。 */
    private static final String TAG_PREFIX = "VB|";
    private static final String TAG_SUFFIX = "|id";

    private static final int UNKNOWN = -1;

    private static volatile Chain chain;
    private static volatile ProtocolVersion chainVersion;

    /** 本地 raw item id -> Via 原本给的降级 id。{@link #UNKNOWN} 表示没见过。 */
    private static volatile int[] wireIdCache = new int[0];

    private static boolean globalIdentifiersLoaded;

    private ExtendedItemMapper() {
    }

    // ------------------------------------------------------------------
    // 对外入口
    // ------------------------------------------------------------------

    /**
     * 只在连 1.17+ 服务器时启用。1.8 / 1.12 / 1.16.4 一律返回 {@code false}，
     * 此时 {@link #remap} 与 {@link #wireId} 都是恒等操作。
     */
    public static boolean isActive() {
        try {
            return JelloPortal.getVersion().newerThanOrEqualTo(ProtocolVersion.v1_17);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 由 {@code PacketBuffer#readItemStack()} 调用。如果这一格是被 ViaBackwards 降级过的
     * 现代物品、且本地注册表里有它，就返回换成本地物品的新 stack；否则原样返回。
     *
     * @param stack  按线上 id 构造出来的 stack（NBT 已装好）
     * @param wireId 线上读到的 raw item id（已被 Via 降级）
     */
    public static ItemStack remap(ItemStack stack, int wireId) {
        if (stack == null || stack.isEmpty() || !isActive()) {
            return stack;
        }

        CompoundNBT tag = stack.getTag();
        if (tag == null || tag.isEmpty()) {
            return stack;
        }

        try {
            /*
             * 附魔还原与物品替换是两件独立的事 —— 一把原版钻石剑也可以带 wind_burst，
             * 那种情况下物品本身根本没被降级。所以先无条件走一遍附魔还原（它自己会
             * 检查根节点里有没有 "VB|...|enchantments" 这种 key，没有就是空操作）。
             */
            ExtendedEnchantmentRestorer.restore(tag);

            Resolved resolved = resolve(tag);
            if (resolved == null || resolved.item == stack.getItem()) {
                return stack;
            }

            /*
             * 只接管 1.17+ 的扩展物品。还原出来的标识符如果落在原版 0-975 上，说明 Via
             * 本来就已经映射到正确的物品（例如 dirt_path -> grass_path 只是改了个名），
             * 这时不能动 —— 既没必要，也会让出站 id 与 wireId() 的 >=976 快速路径对不上。
             */
            if (!ModernRegistry.isExtended(resolved.item)) {
                return stack;
            }

            ItemStack remapped = new ItemStack(resolved.item, stack.getCount());
            // NBT 直接沿用同一个引用：Damage、Enchantments、display.Lore、CustomModelData、
            // 以及 Via 自己的 "VB|...|id" 记账标签全部原封不动，出站时 Via 还要靠它还原真正的物品。
            remapped.setTag(tag);
            rememberWireId(resolved.item, wireId);

            // 换回本地物品之后，Via 贴的占位名（"§f1.21 Mace"）就是纯粹的噪音了。
            boolean nameCleared = clearPlaceholderName(tag, resolved.prefix);

            if (isDebugEnabled()) {
                LOGGER.info("[ExtendedItem] {} -> {} (wireId={}, placeholderNameCleared={})",
                        Registry.ITEM.getKey(stack.getItem()), Registry.ITEM.getKey(resolved.item),
                        wireId, nameCleared);
            }

            return remapped;
        } catch (Throwable t) {
            LOGGER.debug("[ExtendedItem] remap failed, keeping downgraded item: {}", t.toString());
            return stack;
        }
    }

    /**
     * 由 {@code PacketBuffer#writeItemStack(...)} 调用，返回该写到线上的 raw item id。
     *
     * <p>被 {@link #remap} 换过的物品要写回 Via 原本给的降级 id —— 本地扩展 id
     * （&ge; 976）服务器不认识，而降级 id 加上 NBT 里的 {@code "VB|...|id"} 标签
     * 正是 Via 期望收到的东西。其余情况一律返回原版 {@link Item#getIdFromItem}。
     */
    public static int wireId(ItemStack stack) {
        int nativeId = Item.getIdFromItem(stack.getItem());
        if (!isActive() || nativeId < ModernRegistry.VANILLA_ITEM_COUNT) {
            return nativeId;
        }

        // 换服换版本时降级 id 会变，这里也要触发一次版本检查，避免用上一个服务器的缓存。
        getChain();

        int[] cache = wireIdCache;
        if (nativeId >= cache.length) {
            return nativeId;
        }

        int wireId = cache[nativeId];
        if (wireId == UNKNOWN) {
            return nativeId;
        }

        // 只有当这一格还带着 Via 的记账标签时才换 —— 否则服务器收到降级 id 却没有
        // 还原标签，会把它当成真的降级物品。没有标签说明这不是服务器发来的那一份
        // （例如创造栏自己造出来的），行为与改动前保持一致。
        CompoundNBT tag = stack.getTag();
        return tag != null && hasViaIdTag(tag) ? wireId : nativeId;
    }

    public static boolean isDebugEnabled() {
        return Boolean.getBoolean(DEBUG_PROPERTY);
    }

    // ------------------------------------------------------------------
    // 还原
    // ------------------------------------------------------------------

    private static Resolved resolve(CompoundNBT tag) {
        Chain current = getChain();
        if (current.isEmpty()) {
            return null;
        }

        Step best = null;
        int bestId = UNKNOWN;

        for (String key : tag.keySet()) {
            if (!key.startsWith(TAG_PREFIX) || !key.endsWith(TAG_SUFFIX)
                    || !tag.contains(key, 99)) {
                continue;
            }

            Step step = current.byTagKey.get(key);
            // 链上越新的步骤越接近服务器真正发出的那个物品：mace 在 1.21→1.20.5 就被换成
            // netherite_axe，之后的步骤只是在搬运 netherite_axe。取 index 最小的那个。
            if (step != null && (best == null || step.index < best.index)) {
                best = step;
                bestId = tag.getInt(key);
            }
        }

        if (best == null) {
            return null;
        }

        String identifier = best.identifier(bestId);
        if (identifier == null) {
            return null;
        }

        Item item = ModernRegistry.itemByModernId(identifier);
        return item == null ? null : new Resolved(item, best.prefix());
    }

    /** {@link #resolve} 的结果：换成哪个本地物品，以及是链上哪一步做的替换。 */
    private static final class Resolved {
        private final Item item;
        /** 那一步的 NBT key 前缀，形如 {@code "VB|Protocol1_21To1_20_5|"}。 */
        private final String prefix;

        private Resolved(Item item, String prefix) {
            this.item = item;
            this.prefix = prefix;
        }
    }

    // ------------------------------------------------------------------
    // 占位名
    // ------------------------------------------------------------------

    /**
     * 清掉 ViaBackwards 给降级物品贴的占位显示名（{@code "§f1.21 Mace"}、
     * {@code "§f1.21.11 Netherite Spear"} 这种）。物品已经换回本地的重锤 / 长矛了，
     * 这个名字纯属噪音，而且会盖掉本地的中文名。
     *
     * <h3>Via 的两套「这个名字是我加的」标记</h3>
     *
     * 语义完全一样，只是分属两个时代，落点不同：
     *
     * <p><b>1) NBT 时代</b>，{@code BackwardsItemRewriter#handleItemToClient}
     * （1.20.3→1.20.2 及更老的步骤）：
     * <pre>
     * if (!display.contains("Name")) {
     *     display.put("Name", new StringTag(mappedItem.jsonName()));
     *     display.put(nbtTagName("customName"), new ByteTag(false));  // 标记在 display 里
     * }
     * </pre>
     * 出站 {@code BackwardsItemRewriterBase#restoreDisplayTag}：
     * <pre>
     * if (display.remove(nbtTagName("customName")) != null) {
     *     display.remove("Name");                     // 是我加的 -> 整个删掉
     * } else {
     *     restoreStringTag(display, "Name");          // 不是我加的 -> 从 VB|...|Name 恢复原名
     * }
     * </pre>
     *
     * <p><b>2) 结构化组件时代</b>，{@code BackwardsStructuredItemRewriter#backupInconvertibleData}
     * （1.20.5+ 的步骤）：
     * <pre>
     * if (!dataContainer.has(StructuredDataKey.CUSTOM_NAME)) {
     *     dataContainer.set(StructuredDataKey.CUSTOM_NAME, mappedItem.tagName());
     *     customTag.putBoolean(nbtTagName("added_custom_name"), true);  // 标记在 custom_data 里
     * }
     * </pre>
     * 出站 {@code StructuredItemRewriter#restoreBackupData}：
     * <pre>
     * if (removeBackupTag(customData, "added_custom_name") != null) {
     *     dataContainer.remove(StructuredDataKey.CUSTOM_NAME);
     * } else {
     *     从 VB|...|custom_name / VB|...|item_name 恢复备份
     * }
     * </pre>
     *
     * <h3>为什么删掉之后出站不会坏</h3>
     *
     * 两个标记都是「<b>存在即代表眼前这个名字是 Via 造的、服务器那边本来没有名字</b>」。
     * 它和「备份原名」是<b>互斥</b>的：备份（{@code VB|...|Name} /
     * {@code VB|...|custom_name}）只在物品<b>本来就有名字</b>、且被 ComponentRewriter
     * 改写过时才写（{@code updateTextComponent} / {@code saveStringTag}），而那种情况下
     * {@code contains("Name")} / {@code has(CUSTOM_NAME)} 为真，加占位名这一支根本不会走。
     *
     * <p>于是「同时删掉标记和名字」出站后与 Via 自己的结果<b>逐字节一致</b>：
     * 标记没了 → 走 else 分支 → 找不到备份 → 什么都不做；而名字我们已经删了。
     * Via 原本的结果是「删掉标记 + 删掉名字」，两条路殊途同归。
     *
     * <p>只清 {@link #resolve} 认定的<b>那一步</b>的标记（{@code prefix} 精确匹配），
     * 别的步骤、别的 key 一个字节都不动。{@code VB|...|id} 尤其要留着 ——
     * {@link #wireId} 和 Via 的 {@code handleItemToServer} 都靠它。
     *
     * @return 真的清掉了才返回 {@code true}
     */
    private static boolean clearPlaceholderName(CompoundNBT tag, String prefix) {
        // 结构化组件那一侧：custom_data 就是 1.20.3 的 NBT 根节点，所以标记在根上。
        String structuredMarker = prefix + "added_custom_name";
        if (tag.contains(structuredMarker)) {
            tag.remove(structuredMarker);
            return removeDisplayName(tag);
        }

        // NBT 那一侧：标记跟 Name 一起躺在 display 里。
        if (tag.contains("display", 10)) {
            CompoundNBT display = tag.getCompound("display");
            String legacyMarker = prefix + "customName";
            if (display.contains(legacyMarker)) {
                display.remove(legacyMarker);
                return removeDisplayName(tag);
            }
        }

        return false;
    }

    /** 删掉 {@code display.Name}，并顺手清掉因此空掉的 {@code display}（与 Via 的清理行为一致）。 */
    private static boolean removeDisplayName(CompoundNBT tag) {
        if (!tag.contains("display", 10)) {
            return false;
        }

        CompoundNBT display = tag.getCompound("display");
        if (!display.contains("Name", 8)) {
            return false;
        }

        display.remove("Name");
        if (display.isEmpty()) {
            tag.remove("display");
        }

        return true;
    }

    private static boolean hasViaIdTag(CompoundNBT tag) {
        for (String key : tag.keySet()) {
            if (key.startsWith(TAG_PREFIX) && key.endsWith(TAG_SUFFIX)) {
                return true;
            }
        }

        return false;
    }

    // ------------------------------------------------------------------
    // 出站 id 缓存
    // ------------------------------------------------------------------

    private static void rememberWireId(Item modern, int wireId) {
        int nativeId = Item.getIdFromItem(modern);
        if (nativeId < 0 || wireId < 0) {
            return;
        }

        synchronized (ExtendedItemMapper.class) {
            if (nativeId >= wireIdCache.length) {
                int capacity = Math.max(nativeId + 1, Registry.ITEM.keySet().size());
                int[] grown = new int[capacity];
                Arrays.fill(grown, UNKNOWN);
                System.arraycopy(wireIdCache, 0, grown, 0, wireIdCache.length);
                wireIdCache = grown;
            }
            wireIdCache[nativeId] = wireId;
        }
    }

    // ------------------------------------------------------------------
    // 降级链
    // ------------------------------------------------------------------

    private static Chain getChain() {
        ProtocolVersion target = targetVersionSafe();
        Chain current = chain;
        if (current != null && target.equals(chainVersion)) {
            return current;
        }

        synchronized (ExtendedItemMapper.class) {
            current = chain;
            if (current != null && target.equals(chainVersion)) {
                return current;
            }

            current = buildChain(target);
            chain = current;
            chainVersion = target;
            // 换服可能换版本，降级 id 是按版本算的，必须跟着重置。
            wireIdCache = new int[0];
            LOGGER.info("[ExtendedItem] Loaded {} item-mapping steps for {}", current.byTagKey.size(),
                    target.getName());
            return current;
        }
    }

    private static ProtocolVersion targetVersionSafe() {
        try {
            return JelloPortal.getVersion();
        } catch (Exception e) {
            return ProtocolVersion.v1_16_4;
        }
    }

    /**
     * 与 {@code ExtendedBlockStateMapper#loadMappings} 同一份链，顺序也一致（新 -&gt; 旧）。
     * 每一步额外记下它的<b>源版本</b>字符串，用来加载 ViaVersion 的
     * {@code identifiers-<版本>.nbt}，把标签里的 id 还原成标识符。
     */
    private static Chain buildChain(ProtocolVersion target) {
        List<Step> steps = new ArrayList<>();
        addStep(steps, target, ProtocolVersion.v1_21_11, "1.21.11",
                "com.viaversion.viabackwards.protocol.v1_21_11to1_21_9.Protocol1_21_11To1_21_9");
        addStep(steps, target, ProtocolVersion.v1_21_9, "1.21.9",
                "com.viaversion.viabackwards.protocol.v1_21_9to1_21_7.Protocol1_21_9To1_21_7");
        addStep(steps, target, ProtocolVersion.v1_21_7, "1.21.7",
                "com.viaversion.viabackwards.protocol.v1_21_7to1_21_6.Protocol1_21_7To1_21_6");
        addStep(steps, target, ProtocolVersion.v1_21_6, "1.21.6",
                "com.viaversion.viabackwards.protocol.v1_21_6to1_21_5.Protocol1_21_6To1_21_5");
        addStep(steps, target, ProtocolVersion.v1_21_5, "1.21.5",
                "com.viaversion.viabackwards.protocol.v1_21_5to1_21_4.Protocol1_21_5To1_21_4");
        addStep(steps, target, ProtocolVersion.v1_21_4, "1.21.4",
                "com.viaversion.viabackwards.protocol.v1_21_4to1_21_2.Protocol1_21_4To1_21_2");
        addStep(steps, target, ProtocolVersion.v1_21_2, "1.21.2",
                "com.viaversion.viabackwards.protocol.v1_21_2to1_21.Protocol1_21_2To1_21");
        addStep(steps, target, ProtocolVersion.v1_21, "1.21",
                "com.viaversion.viabackwards.protocol.v1_21to1_20_5.Protocol1_21To1_20_5");
        addStep(steps, target, ProtocolVersion.v1_20_5, "1.20.5",
                "com.viaversion.viabackwards.protocol.v1_20_5to1_20_3.Protocol1_20_5To1_20_3");
        addStep(steps, target, ProtocolVersion.v1_20_3, "1.20.3",
                "com.viaversion.viabackwards.protocol.v1_20_3to1_20_2.Protocol1_20_3To1_20_2");
        addStep(steps, target, ProtocolVersion.v1_20_2, "1.20.2",
                "com.viaversion.viabackwards.protocol.v1_20_2to1_20.Protocol1_20_2To1_20");
        addStep(steps, target, ProtocolVersion.v1_20, "1.20",
                "com.viaversion.viabackwards.protocol.v1_20to1_19_4.Protocol1_20To1_19_4");
        addStep(steps, target, ProtocolVersion.v1_19_4, "1.19.4",
                "com.viaversion.viabackwards.protocol.v1_19_4to1_19_3.Protocol1_19_4To1_19_3");
        addStep(steps, target, ProtocolVersion.v1_19_3, "1.19.3",
                "com.viaversion.viabackwards.protocol.v1_19_3to1_19_1.Protocol1_19_3To1_19_1");
        addStep(steps, target, ProtocolVersion.v1_19_1, "1.19",
                "com.viaversion.viabackwards.protocol.v1_19_1to1_19.Protocol1_19_1To1_19");
        addStep(steps, target, ProtocolVersion.v1_19, "1.19",
                "com.viaversion.viabackwards.protocol.v1_19to1_18_2.Protocol1_19To1_18_2");
        addStep(steps, target, ProtocolVersion.v1_18_2, "1.18",
                "com.viaversion.viabackwards.protocol.v1_18_2to1_18.Protocol1_18_2To1_18");
        addStep(steps, target, ProtocolVersion.v1_18, "1.18",
                "com.viaversion.viabackwards.protocol.v1_18to1_17_1.Protocol1_18To1_17_1");
        addStep(steps, target, ProtocolVersion.v1_17, "1.17",
                "com.viaversion.viabackwards.protocol.v1_17to1_16_4.Protocol1_17To1_16_4");

        Map<String, Step> byTagKey = new HashMap<>();
        for (Step step : steps) {
            byTagKey.put(step.tagKey, step);
        }

        return new Chain(byTagKey);
    }

    private static void addStep(List<Step> steps, ProtocolVersion target, ProtocolVersion minimum,
            String sourceVersion, String protocolClassName) {
        if (target.olderThan(minimum)) {
            return;
        }

        try {
            Class<?> protocolClass = Class.forName(protocolClassName);
            @SuppressWarnings({ "rawtypes", "unchecked" })
            Object protocol = Via.getManager().getProtocolManager().getProtocol((Class) protocolClass);
            if (protocol == null) {
                return;
            }

            String tagKey = tagKeyOf(protocol, protocolClass);
            if (tagKey != null) {
                steps.add(new Step(steps.size(), tagKey, sourceVersion));
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Exception e) {
            LOGGER.warn("[ExtendedItem] Could not register item-mapping step {}: {}", protocolClassName,
                    e.getMessage());
        }
    }

    /**
     * 优先问 Via 自己要 key（{@code getItemRewriter().nbtTagName("id")}），拿不到再按
     * {@code "VB|" + 类简名 + "|id"} 兜底 —— 这是 ViaBackwards 当前的实现规则。
     */
    private static String tagKeyOf(Object protocol, Class<?> protocolClass) {
        try {
            Object rewriter = protocol.getClass().getMethod("getItemRewriter").invoke(protocol);
            if (rewriter != null) {
                Object key = rewriter.getClass().getMethod("nbtTagName", String.class).invoke(rewriter, "id");
                if (key instanceof String && !((String) key).isEmpty()) {
                    return (String) key;
                }
            }
        } catch (Exception ignored) {
        }

        return TAG_PREFIX + protocolClass.getSimpleName() + TAG_SUFFIX;
    }

    private static final class Chain {
        private final Map<String, Step> byTagKey;

        private Chain(Map<String, Step> byTagKey) {
            this.byTagKey = byTagKey;
        }

        private boolean isEmpty() {
            return this.byTagKey.isEmpty();
        }
    }

    /** 降级链上的一步。{@code identifiers} 按需加载，加载失败就永久禁用这一步。 */
    private static final class Step {
        private final int index;
        private final String tagKey;
        private final String sourceVersion;
        private volatile List<String> identifiers;
        private volatile boolean unavailable;

        private Step(int index, String tagKey, String sourceVersion) {
            this.index = index;
            this.tagKey = tagKey;
            this.sourceVersion = sourceVersion;
        }

        /**
         * 这一步所有 NBT key 的公共前缀，形如 {@code "VB|Protocol1_21To1_20_5|"}。
         * {@code tagKey} 是 {@code 前缀 + "id"}，去掉末尾两个字符即可。
         */
        private String prefix() {
            return this.tagKey.substring(0, this.tagKey.length() - 2);
        }

        private String identifier(int id) {
            if (id < 0) {
                return null;
            }

            List<String> table = this.identifiers;
            if (table == null) {
                if (this.unavailable) {
                    return null;
                }
                table = loadIdentifiers();
                if (table == null) {
                    return null;
                }
            }

            return id < table.size() ? table.get(id) : null;
        }

        private synchronized List<String> loadIdentifiers() {
            if (this.identifiers != null) {
                return this.identifiers;
            }
            if (this.unavailable) {
                return null;
            }

            List<String> table = readIdentifierTable(this.sourceVersion);
            if (table == null || table.isEmpty()) {
                this.unavailable = true;
                LOGGER.warn("[ExtendedItem] No item identifiers for {}, step {} disabled", this.sourceVersion,
                        this.tagKey);
                return null;
            }

            this.identifiers = table;
            return table;
        }
    }

    /**
     * 读 ViaVersion 自带的 {@code assets/viaversion/data/identifiers-<版本>.nbt}，
     * 得到「该版本 raw item id -> 标识符」。
     *
     * <p>不能走 {@code MappingData#getFullItemMappings()}：ViaBackwards 的
     * {@code BackwardsMappingData#loadBiMappings} 对 items 返回的是
     * {@code ItemMappings extends BiMappingsBase}，<b>不是</b> {@code FullMappings}，
     * 所以那个方法在降级协议上恒为 {@code null}。
     */
    private static List<String> readIdentifierTable(String version) {
        try {
            List<String> table = identifiersFromLoader(version);
            if (table != null && !table.isEmpty()) {
                return table;
            }

            // Via 还没建全局索引（正常启动流程里会建）时补一次，然后重试。
            synchronized (ExtendedItemMapper.class) {
                if (!globalIdentifiersLoaded) {
                    com.viaversion.viaversion.api.data.MappingDataLoader.loadGlobalIdentifiers();
                    globalIdentifiersLoaded = true;
                }
            }

            return identifiersFromLoader(version);
        } catch (Throwable t) {
            LOGGER.warn("[ExtendedItem] Could not read identifiers-{}.nbt: {}", version, t.toString());
            return null;
        }
    }

    private static List<String> identifiersFromLoader(String version) {
        com.viaversion.viaversion.api.data.MappingDataLoader loader =
                com.viaversion.viaversion.api.data.MappingDataLoader.INSTANCE;
        com.viaversion.nbt.tag.CompoundTag data = loader.loadNBT("identifiers-" + version + ".nbt");
        return data == null ? null : loader.identifiersFromGlobalIds(data, "items");
    }
}
