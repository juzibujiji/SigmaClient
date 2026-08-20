package net.minecraft.client.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourcePackType;
import net.minecraft.resources.VanillaPack;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class VirtualAssetsPack extends VanillaPack
{
    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * 启动器 asset index 里<b>确实存在</b> {@code minecraft/sounds.json}（已用真实 index 验证：
     * 其 objects 里有 {@code "minecraft/sounds.json"} 这个键）。而下面 getInputStreamVanilla
     * 的查找顺序是「先 index、命中就直接返回」，所以我们打进
     * {@code src/main/resources/assets/minecraft/sounds.json} 的内容会被<b>完全遮蔽</b>，
     * 新注册的 SoundEvent 绑不到任何音频，播出来是静音。
     *
     * <p><b>不能整体替换成官方 1.21 那份</b> —— 格式与内容都是 1.21 的，会破坏 1.16.4 全部原版音效，
     * 而且 1.16.4 的 {@link net.minecraft.client.audio.SoundListSerializer} 对 volume/pitch/weight
     * 有 {@code Validate.isTrue(>0)} 的强校验，1.21 的条目未必都能过。
     *
     * <p>正确做法是<b>运行时合并</b>：原版那份仍旧从启动器 index 读（永远不用维护副本，原版零风险），
     * 我们的新增条目单独放在 {@link #SOUNDS_OVERLAY}，读出来并入后再喂给资源系统。
     *
     * <p>注意 overlay <b>必须换个文件名</b>：叫 sounds.json 就会重新踩进同一个遮蔽陷阱。
     * {@code sounds-modern.json} 不在任何 asset index 里，因此只会从 classpath 命中。
     * （对比：{@code minecraft/lang/en_us.json} 不在 index 里所以语言文件「只追加」有效，
     * 但 {@code minecraft/lang/zh_cn.json} <b>在</b> index 里 —— 别把这两件事搞混。）
     */
    private static final String SOUNDS_JSON = "sounds.json";
    private static final String SOUNDS_OVERLAY = "sounds-modern.json";

    private final ResourceIndex field_195785_b;

    public VirtualAssetsPack(ResourceIndex p_i48115_1_)
    {
        // mmdskin：注册 mmdskin 命名空间，使 assets/mmdskin/lang 等经资源管理器加载。
        // YSM：同理注册 yes_steve_model —— 没有它 SimpleReloadableResourceManager 不会为该命名空间
        // 建 FallbackResourceManager，于是 lang/*.json（ClientLanguageMap 只遍历已注册命名空间）与
        // yes_steve_model:texture/roulette.png（轮盘 GUI 的滑条/复选框贴图）全部解析失败。
        // 已用 VanillaPack 探针实测：声明命名空间后两者均可从 classpath 命中。
        super("minecraft", "mmdskin", "yes_steve_model");
        this.field_195785_b = p_i48115_1_;
    }

    @Nullable
    protected InputStream getInputStreamVanilla(ResourcePackType type, ResourceLocation location)
    {
        // sounds.json 走合并分支，否则我们的新增音效会被启动器 index 里的原版那份完全遮蔽。
        if (type == ResourcePackType.CLIENT_RESOURCES && SOUNDS_JSON.equals(location.getPath()))
        {
            InputStream inputstream = this.getMergedSounds(location);

            if (inputstream != null)
            {
                return inputstream;
            }
        }

        return this.getInputStreamRaw(type, location);
    }

    /**
     * 原本 getInputStreamVanilla 的查找逻辑：先启动器 index，再 classpath/jar。
     * 抽出来是为了让 sounds.json 的合并分支能复用「读原版那份」这一步，
     * 同时保证其余资源的行为与改动前<b>逐字节一致</b>。
     */
    @Nullable
    private InputStream getInputStreamRaw(ResourcePackType type, ResourceLocation location)
    {
        if (type == ResourcePackType.CLIENT_RESOURCES)
        {
            File file1 = this.field_195785_b.getFile(location);

            if (file1 != null && file1.exists())
            {
                try
                {
                    return new FileInputStream(file1);
                }
                catch (FileNotFoundException filenotfoundexception)
                {
                }
            }
        }

        return super.getInputStreamVanilla(type, location);
    }

    /**
     * 把原版 sounds.json 与我们的 sounds-modern.json 合并成一份。
     *
     * <p>结构按 1.16.4 {@link net.minecraft.client.audio.SoundListSerializer} 的要求：顶层是
     * {@code 事件名 -> {replace, subtitle, sounds[]}}，条目里的 {@code sounds} 元素可以是字符串
     * 简写，也可以是带 {@code name}/{@code type}(file|event)/{@code volume}/{@code pitch}/
     * {@code weight}/{@code preload}/{@code stream}/{@code attenuation_distance} 的对象。
     * 因为合并只在<b>顶层按事件名</b>做，条目内部原样搬运，所以不需要理解条目细节。
     *
     * <p><b>为什么不会破坏原版音效：</b>以原版那份为基底，<b>只追加原版没有的键</b>
     * （见下面的 {@code jsonobject.has} 判断）。overlay 本身在生成阶段就已按 1.16.4
     * SoundEvents.java 过滤掉 block.stone.* / block.wood.* 这类原版事件，运行时再兜一层，
     * 因此原版条目在任何情况下都不会被改写。任一侧解析失败就整体退回原版那份，
     * 宁可新音效静音也不牵连原版。
     *
     * @return 合并后的流；没有 overlay 或解析失败时返回 null，由调用方退回原始行为。
     */
    @Nullable
    private InputStream getMergedSounds(ResourceLocation location)
    {
        JsonObject jsonobject = this.readJson(this.getInputStreamRaw(ResourcePackType.CLIENT_RESOURCES, location), location.toString());

        if (jsonobject == null)
        {
            return null;
        }

        ResourceLocation resourcelocation = new ResourceLocation(location.getNamespace(), SOUNDS_OVERLAY);
        JsonObject jsonobject1 = this.readJson(super.getInputStreamVanilla(ResourcePackType.CLIENT_RESOURCES, resourcelocation), resourcelocation.toString());

        if (jsonobject1 == null)
        {
            return null;
        }

        int i = 0;
        int j = 0;

        for (Entry<String, JsonElement> entry : jsonobject1.entrySet())
        {
            // 只追加，不覆盖：原版已有的键一律跳过。overlay 由
            // tools/crossversion/extract-sounds.js 生成时就已按 1.16.4 SoundEvents.java 过滤过，
            // 这里再兜一层，保证「原版音效零风险」是结构性成立的，而不是靠生成器守规矩。
            if (jsonobject.has(entry.getKey()))
            {
                ++j;
                continue;
            }

            jsonobject.add(entry.getKey(), entry.getValue());
            ++i;
        }

        if (j > 0)
        {
            LOGGER.warn("{} modern sound event(s) collided with vanilla and were skipped; "
                    + "regenerate sounds-modern.json via tools/crossversion/extract-sounds.js", j);
        }

        LOGGER.info("Merged {} modern sound event(s) into {}", i, location);
        return new ByteArrayInputStream(jsonobject.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Nullable
    private JsonObject readJson(@Nullable InputStream streamIn, String nameIn)
    {
        if (streamIn == null)
        {
            return null;
        }

        try (Reader reader = new InputStreamReader(streamIn, StandardCharsets.UTF_8))
        {
            return JSONUtils.fromJson(reader);
        }
        catch (Exception exception)
        {
            LOGGER.warn("Failed to read {} for sound merging, falling back to vanilla", nameIn, exception);
            return null;
        }
    }

    public boolean resourceExists(ResourcePackType type, ResourceLocation location)
    {
        // sounds.json 的存在性必须和 getInputStreamVanilla 判断一致：合并分支会额外
        // 从 classpath 取 overlay，所以这里也得把 classpath 算进去，否则
        // FallbackResourceManager 先 resourceExists 后 getInputStream 的两步会不自洽。
        if (type == ResourcePackType.CLIENT_RESOURCES)
        {
            File file1 = this.field_195785_b.getFile(location);

            if (file1 != null && file1.exists())
            {
                return true;
            }
        }

        return super.resourceExists(type, location);
    }

    @Nullable
    protected InputStream getInputStreamVanilla(String pathIn)
    {
        File file1 = this.field_195785_b.getFile(pathIn);

        if (file1 != null && file1.exists())
        {
            try
            {
                return new FileInputStream(file1);
            }
            catch (FileNotFoundException filenotfoundexception)
            {
            }
        }

        return super.getInputStreamVanilla(pathIn);
    }

    public Collection<ResourceLocation> getAllResourceLocations(ResourcePackType type, String namespaceIn, String pathIn, int maxDepthIn, Predicate<String> filterIn)
    {
        Collection<ResourceLocation> collection = super.getAllResourceLocations(type, namespaceIn, pathIn, maxDepthIn, filterIn);
        collection.addAll(this.field_195785_b.getFiles(pathIn, namespaceIn, maxDepthIn, filterIn));
        return collection;
    }
}
