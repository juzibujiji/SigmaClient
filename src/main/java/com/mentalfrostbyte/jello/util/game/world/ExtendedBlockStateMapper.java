package com.mentalfrostbyte.jello.util.game.world;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.crossversion.ModernBlockStateMap;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ExtendedBlockStateMapper {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int AIR_ID = Block.getStateId(Blocks.AIR.getDefaultState());
    private static final int STONE_ID = Block.getStateId(Blocks.STONE.getDefaultState());
    private static volatile MappingChain mappingChain;
    private static volatile ProtocolVersion mappingVersion;
    /**
     * rawStateId -> native 1.16 block-state id. -1 means "not computed yet".
     * Grown lazily (never shrinks) and swapped atomically.
     */
    private static volatile int[] nativeIdCache = new int[0];
    private static final AtomicBoolean WARMUP_STARTED = new AtomicBoolean();

    /**
     * {@link #nativeIdCache} 里的值是按<b>哪个</b>目标版本算出来的。
     *
     * <p>与 {@link #mappingVersion} 是两回事：后者管 Via 降级链的重建，前者管缓存的失效。
     * 换服重连（1.21.11 → 1.8）时如果不清缓存，会拿上一个服务器的映射结果去解释
     * 新服务器的状态 ID，出来的是随机方块。
     */
    private static volatile ProtocolVersion cacheVersion;

    private static final int UNCOMPUTED = -1;
    private static final int WARMUP_BOUND = 1 << 16;

    private ExtendedBlockStateMapper() {
    }

    public static BlockState mapToBlockState(int rawStateId) {
        int mappedId = mapToNativeId(rawStateId);
        BlockState state = Block.BLOCK_STATE_IDS.getByValue(mappedId);
        return state != null ? state : Blocks.STONE.getDefaultState();
    }

    public static int mapToNativeId(int rawStateId) {
        if (rawStateId == AIR_ID) {
            return AIR_ID;
        }

        // 换服重连后目标版本会变，而缓存是<b>按版本</b>算出来的：1.21.11 的状态 ID 拿到
        // 1.8 服务器上是完全不同的东西。getMappingChain 早就按 mappingVersion 做失效了，
        // 缓存这边原来漏了，会把上一个服务器的结果一路用下去。
        invalidateCacheOnVersionChange();

        int[] cache = nativeIdCache;
        if (rawStateId >= 0 && rawStateId < cache.length) {
            int cached = cache[rawStateId];
            if (cached != UNCOMPUTED) {
                return cached;
            }
        }

        return slowMapToNativeId(rawStateId);
    }

    /**
     * 当前是否处在「现代透传权威可用」的状态：目标版本正好是 1.21.11，
     * <b>且</b>状态表已经建好。
     *
     * <p>两个条件都必须成立才允许把结果写进缓存 —— 这是本类踩过的最贵的一个坑，
     * 见 {@link #slowMapToNativeId} 的说明。
     */
    private static boolean modernPassthroughReady() {
        return ProtocolVersion.v1_21_11.equals(WorldHeightHelper.getTargetVersionSafe())
                && ModernBlockStateMap.isReady();
    }

    private static void invalidateCacheOnVersionChange() {
        ProtocolVersion target = WorldHeightHelper.getTargetVersionSafe();
        if (target.equals(cacheVersion)) {
            return;
        }

        synchronized (ExtendedBlockStateMapper.class) {
            if (target.equals(cacheVersion)) {
                return;
            }

            Arrays.fill(nativeIdCache, UNCOMPUTED);
            cacheVersion = target;
            // 允许换版本后重新预热。
            WARMUP_STARTED.set(false);
            LOGGER.info("[ExtendedHeight] Target version changed to {}, block-state cache invalidated",
                    target.getName());
        }
    }

    private static int slowMapToNativeId(int rawStateId) {
        // 先按「标识符 + 属性」直接对上本地方块 —— 深板岩就是深板岩。
        //
        // 【为什么必须在 Via 链之前】原来这里只有下面那条 chain.map 路径，而
        // ViaBackwards 在 1.21→1.16.4 这条链上<b>本来就把深板岩降级成石头</b>。
        // 也就是说映射是「成功」的、下面那个 STONE_ID 兜底根本没被走到 ——
        // 所以「改兜底」不解决任何问题，必须在进链之前截住。
        //
        // 【版本门是硬性的，不能去掉】这张表是按 1.21.11 的 blockstate ID 建的索引。
        // ChunkDataInterceptor 装在 Via 解码器之前（MCPVLBPipeline 用的是
        // addBefore(VIA_DECODER_HANDLER_NAME, ...)），所以 rawStateId 是<b>服务器那个版本
        // 原生的</b> ID。连 1.21.9 服务器时它的 ID 空间和 1.21.11 不同
        // （正因为不同，Protocol1_21_11To1_21_9 才需要带 blockstate 映射），
        // 拿错的键查表出来的是随机方块 —— 比降级成石头糟得多，而且完全不报错。
        //
        // 其它版本要透传就得各自生成一张表（拿那个版本的 server.jar 跑 --reports），
        // 而且没法让老版本的 ID 先升到 1.21.11 再查 —— 降级链只往一个方向走。
        boolean ready = modernPassthroughReady();

        if (ready) {
            int direct = ModernBlockStateMap.toNativeId(rawStateId);
            if (direct != ModernBlockStateMap.NO_MAPPING) {
                cacheNativeId(rawStateId, direct);
                return direct;
            }
        }

        MappingChain chain = getMappingChain();
        int mappedId = chain.map(rawStateId);
        if (mappedId < 0) {
            return STONE_ID;
        }

        int nativeId = Block.BLOCK_STATE_IDS.getByValue(mappedId) != null ? mappedId : STONE_ID;

        // 【绝对不要无条件缓存 Via 的结果】这里曾经无条件 cacheNativeId，症状是
        // 「进服务器时区块里的方块全是降级的，但手动放置的方块是对的」——
        // 因为 warmupAsync 会在后台把 65536 个 ID 全部预计算。如果预热跑的那一刻
        // 版本还没确定、或者状态表还没建好，整个缓存就被 Via 的降级值填满，
        // 而 mapToNativeId 之后永远命中缓存、<b>版本门再也不会重新评估</b>。
        // 手动放置之所以是对的，是因为那条路（applyModernStateOverride）直接调
        // ModernBlockStateMap，绕过了这个缓存。
        //
        // 所以只有在权威状态下才落盘。还没就绪时算出来的值直接返回、不缓存，
        // 等表建好后自然会重新走一遍拿到正确结果。
        if (ready) {
            cacheNativeId(rawStateId, nativeId);
        }

        return nativeId;
    }

    /**
     * Populates the fast mapping cache on a background thread so the first
     * chunk data packet does not pay the per-block reflection cost on the
     * Netty event loop. Idempotent.
     *
     * <p><b>必须等到权威状态才填。</b>本方法被 {@code ChunkDataInterceptor} 和
     * {@code ClientPlayNetHandler} 两处调用，谁先到谁生效（{@code WARMUP_STARTED} 是一次性的）。
     * 如果在版本还没确定、或 {@link ModernBlockStateMap} 的表还没建好时就把 65536 个 ID
     * 全部算完存进缓存，那一整份缓存就是 Via 的降级值，而且之后永远不会重算 ——
     * 表现就是「进服务器区块全是降级的，手动放置却是对的」。
     *
     * <p>所以这里改成先确保表建好（{@link ModernBlockStateMap#warmupAsync()} 之后轮询等待），
     * 只有 1.21.11 才等；其它版本没有表可等，直接按 Via 链填就是正确行为。
     */
    public static void warmupAsync() {
        if (WARMUP_STARTED.getAndSet(true)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                ProtocolVersion target = WorldHeightHelper.getTargetVersionSafe();

                if (ProtocolVersion.v1_21_11.equals(target)) {
                    ModernBlockStateMap.warmupAsync();

                    // 建表在别的线程上跑（实测 43 ms）。这里最多等 10 秒；等不到就放弃预热，
                    // 让 slowMapToNativeId 走「不就绪就不缓存」那条路懒填 —— 慢一点，但是对的。
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10L);
                    while (!ModernBlockStateMap.isReady() && System.nanoTime() < deadline) {
                        Thread.sleep(20L);
                    }

                    if (!ModernBlockStateMap.isReady()) {
                        LOGGER.warn("[ExtendedHeight] Block-state table not ready after 10s, "
                                + "skipping warmup so lazy fill can pick it up later");
                        WARMUP_STARTED.set(false);
                        return;
                    }
                }

                ensureCacheCapacity(WARMUP_BOUND);
                cacheVersion = target;
                int[] cache = nativeIdCache;
                for (int rawStateId = 0; rawStateId < cache.length; ++rawStateId) {
                    if (cache[rawStateId] == UNCOMPUTED) {
                        cache[rawStateId] = slowMapToNativeId(rawStateId);
                    }
                }
                LOGGER.info("[ExtendedHeight] Warmed up {} block-state mappings for {} (modern table: {})",
                        cache.length, target.getName(),
                        ModernBlockStateMap.isReady() ? ModernBlockStateMap.coveredStates() + " states" : "not used");
            } catch (Throwable t) {
                // 预热失败不能把标志留在 true —— 否则懒填之外再也没有第二次机会。
                WARMUP_STARTED.set(false);
                LOGGER.warn("[ExtendedHeight] Block-state warmup failed, falling back to lazy fill", t);
            }
        });
    }

    private static void cacheNativeId(int rawStateId, int nativeId) {
        if (rawStateId < 0) {
            return;
        }

        int[] cache = nativeIdCache;
        if (rawStateId < cache.length) {
            cache[rawStateId] = nativeId;
            return;
        }

        synchronized (ExtendedBlockStateMapper.class) {
            cache = nativeIdCache;
            if (rawStateId >= cache.length) {
                ensureCacheCapacity(Math.max(rawStateId + 1, cache.length * 2));
            }
            nativeIdCache[rawStateId] = nativeId;
        }
    }

    private static void ensureCacheCapacity(int capacity) {
        int[] current = nativeIdCache;
        if (capacity <= current.length) {
            return;
        }

        int[] grown = new int[capacity];
        java.util.Arrays.fill(grown, UNCOMPUTED);
        System.arraycopy(current, 0, grown, 0, current.length);
        nativeIdCache = grown;
    }

    private static MappingChain getMappingChain() {
        ProtocolVersion target = WorldHeightHelper.getTargetVersionSafe();
        MappingChain current = mappingChain;
        if (current != null && target.equals(mappingVersion)) {
            return current;
        }

        synchronized (ExtendedBlockStateMapper.class) {
            current = mappingChain;
            if (current != null && target.equals(mappingVersion)) {
                return current;
            }

            current = new MappingChain(loadMappings(target));
            mappingChain = current;
            mappingVersion = target;
            LOGGER.info("[ExtendedHeight] Loaded {} block-state mapping steps for {}", current.size(), target.getName());
            return current;
        }
    }

    private static List<Object> loadMappings(ProtocolVersion target) {
        List<Object> mappings = new ArrayList<>();
        addIfNeeded(mappings, target, ProtocolVersion.v1_21_11,
                "com.viaversion.viabackwards.protocol.v1_21_11to1_21_9.Protocol1_21_11To1_21_9");
        addIfNeeded(mappings, target, ProtocolVersion.v1_21_9,
                "com.viaversion.viabackwards.protocol.v1_21_9to1_21_7.Protocol1_21_9To1_21_7");
        addIfNeeded(mappings, target, ProtocolVersion.v1_21_7,
                "com.viaversion.viabackwards.protocol.v1_21_7to1_21_6.Protocol1_21_7To1_21_6");
        addIfNeeded(mappings, target, ProtocolVersion.v1_21_6,
                "com.viaversion.viabackwards.protocol.v1_21_6to1_21_5.Protocol1_21_6To1_21_5");
        addIfNeeded(mappings, target, ProtocolVersion.v1_21_5,
                "com.viaversion.viabackwards.protocol.v1_21_5to1_21_4.Protocol1_21_5To1_21_4");
        addIfNeeded(mappings, target, ProtocolVersion.v1_21_4,
                "com.viaversion.viabackwards.protocol.v1_21_4to1_21_2.Protocol1_21_4To1_21_2");
        addIfNeeded(mappings, target, ProtocolVersion.v1_21_2,
                "com.viaversion.viabackwards.protocol.v1_21_2to1_21.Protocol1_21_2To1_21");
        addIfNeeded(mappings, target, ProtocolVersion.v1_21,
                "com.viaversion.viabackwards.protocol.v1_21to1_20_5.Protocol1_21To1_20_5");
        addIfNeeded(mappings, target, ProtocolVersion.v1_20_5,
                "com.viaversion.viabackwards.protocol.v1_20_5to1_20_3.Protocol1_20_5To1_20_3");
        addIfNeeded(mappings, target, ProtocolVersion.v1_20_3,
                "com.viaversion.viabackwards.protocol.v1_20_3to1_20_2.Protocol1_20_3To1_20_2");
        addIfNeeded(mappings, target, ProtocolVersion.v1_20_2,
                "com.viaversion.viabackwards.protocol.v1_20_2to1_20.Protocol1_20_2To1_20");
        addIfNeeded(mappings, target, ProtocolVersion.v1_20,
                "com.viaversion.viabackwards.protocol.v1_20to1_19_4.Protocol1_20To1_19_4");
        addIfNeeded(mappings, target, ProtocolVersion.v1_19_4,
                "com.viaversion.viabackwards.protocol.v1_19_4to1_19_3.Protocol1_19_4To1_19_3");
        addIfNeeded(mappings, target, ProtocolVersion.v1_19_3,
                "com.viaversion.viabackwards.protocol.v1_19_3to1_19_1.Protocol1_19_3To1_19_1");
        addIfNeeded(mappings, target, ProtocolVersion.v1_19_1,
                "com.viaversion.viabackwards.protocol.v1_19_1to1_19.Protocol1_19_1To1_19");
        addIfNeeded(mappings, target, ProtocolVersion.v1_19,
                "com.viaversion.viabackwards.protocol.v1_19to1_18_2.Protocol1_19To1_18_2");
        addIfNeeded(mappings, target, ProtocolVersion.v1_18_2,
                "com.viaversion.viabackwards.protocol.v1_18_2to1_18.Protocol1_18_2To1_18");
        addIfNeeded(mappings, target, ProtocolVersion.v1_18,
                "com.viaversion.viabackwards.protocol.v1_18to1_17_1.Protocol1_18To1_17_1");
        addIfNeeded(mappings, target, ProtocolVersion.v1_17,
                "com.viaversion.viabackwards.protocol.v1_17to1_16_4.Protocol1_17To1_16_4");
        return mappings;
    }

    private static void addIfNeeded(List<Object> mappings, ProtocolVersion target, ProtocolVersion minimum,
            String protocolClassName) {
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

            Object mappingData = protocol.getClass().getMethod("getMappingData").invoke(protocol);
            if (mappingData == null) {
                return;
            }

            Object blockStateMappings = mappingData.getClass().getMethod("getBlockStateMappings").invoke(mappingData);
            if (blockStateMappings != null) {
                mappings.add(blockStateMappings);
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            LOGGER.warn("[ExtendedHeight] Could not load block-state mapping {}: {}", protocolClassName, e.getMessage());
        }
    }

    private static final class MappingChain {
        private final List<MappingStep> steps;

        private MappingChain(List<Object> rawSteps) {
            this.steps = new ArrayList<>(rawSteps.size());
            for (Object rawStep : rawSteps) {
                try {
                    this.steps.add(new MappingStep(rawStep, rawStep.getClass().getMethod("getNewId", int.class)));
                } catch (Exception e) {
                    LOGGER.warn("[ExtendedHeight] Ignoring unusable block-state mapping: {}", e.getMessage());
                }
            }
        }

        private int size() {
            return this.steps.size();
        }

        private int map(int id) {
            int mapped = id;

            for (MappingStep step : this.steps) {
                mapped = step.map(mapped);
                if (mapped < 0) {
                    return -1;
                }
            }

            return mapped;
        }
    }

    private static final class MappingStep {
        private final Object mappings;
        private final Method getNewId;

        private MappingStep(Object mappings, Method getNewId) {
            this.mappings = mappings;
            this.getNewId = getNewId;
        }

        private int map(int id) {
            try {
                return (int) this.getNewId.invoke(this.mappings, id);
            } catch (Exception e) {
                return -1;
            }
        }
    }
}
