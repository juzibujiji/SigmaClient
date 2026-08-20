package net.minecraft.block;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.Set;
import java.util.stream.Stream;

public class WoodType
{
    private static final Set<WoodType> VALUES = new ObjectArraySet<>();
    public static final WoodType OAK = register(new WoodType("oak"));
    public static final WoodType SPRUCE = register(new WoodType("spruce"));
    public static final WoodType BIRCH = register(new WoodType("birch"));
    public static final WoodType ACACIA = register(new WoodType("acacia"));
    public static final WoodType JUNGLE = register(new WoodType("jungle"));
    public static final WoodType DARK_OAK = register(new WoodType("dark_oak"));
    public static final WoodType CRIMSON = register(new WoodType("crimson"));
    public static final WoodType WARPED = register(new WoodType("warped"));
    // 1.17+ 新增的木种，取自官方
    // world/level/block/state/properties/WoodType.java 的登记顺序
    // （CHERRY 在 ACACIA 之后、PALE_OAK 在 DARK_OAK 之后、MANGROVE/BAMBOO 在 WARPED 之后）。
    // 这里放在末尾追加，因为 VALUES 只用于遍历取材质，顺序不参与协议。
    //
    // 悬挂告示牌与告示牌的材质路径由木种名拼出
    // （entity/signs/<name>、entity/signs/hanging/<name>），所以名字必须与官方逐字一致。
    public static final WoodType CHERRY = register(new WoodType("cherry"));
    public static final WoodType PALE_OAK = register(new WoodType("pale_oak"));
    public static final WoodType MANGROVE = register(new WoodType("mangrove"));
    public static final WoodType BAMBOO = register(new WoodType("bamboo"));
    private final String name;

    protected WoodType(String nameIn)
    {
        this.name = nameIn;
    }

    private static WoodType register(WoodType woodTypeIn)
    {
        VALUES.add(woodTypeIn);
        return woodTypeIn;
    }

    public static Stream<WoodType> getValues()
    {
        return VALUES.stream();
    }

    /**
     * 按名字查木种，找不到返回 {@code null}。
     *
     * <p>官方 1.21 的 {@code WoodType} 自带 {@code TYPES} 名字表（供 {@code WoodType.CODEC} 用），
     * 1.16.4 只有一个 {@code Set}，所以这里补一个线性查找 —— 木种一共 12 个，
     * 且只在方块构造时各调用一次，不需要建索引。
     *
     * <p>用途：告示牌方块可以只用 {@code (Properties)} 构造，木种由注册名反推
     * （{@code cherry_hanging_sign} -> {@code cherry}），这样跨版本注册生成器
     * 不必为每个木种传不同的构造参数。
     */
    public static WoodType byName(String nameIn)
    {
        for (WoodType woodtype : VALUES)
        {
            if (woodtype.name.equals(nameIn))
            {
                return woodtype;
            }
        }

        return null;
    }

    /**
     * 告示牌方块名的木种后缀，按长度从长到短排列 —— 必须先试
     * {@code _wall_hanging_sign} 再试 {@code _hanging_sign}，否则
     * {@code oak_wall_hanging_sign} 会被剥成 {@code oak_wall}。
     */
    private static final String[] SIGN_SUFFIXES = {"_wall_hanging_sign", "_hanging_sign", "_wall_sign", "_sign"};

    /**
     * 从告示牌方块的注册名反推木种，例如 {@code cherry_wall_hanging_sign} -> {@code CHERRY}。
     * 推不出来时兜底 {@link #OAK}（与官方 {@code SignBlock.getWoodType} 的兜底一致）。
     *
     * <p><b>为什么需要它。</b>官方每个告示牌方块的构造都显式传木种，
     * 但跨版本注册生成器只能按<b>类</b>附加固定构造参数，没法给 32 个告示牌各传一个木种。
     * 让方块只需要 {@code (Properties)} 构造、木种延迟从注册名解析，生成器就不必特殊处理。
     *
     * <p>只能延迟调用：方块构造时还没进注册表，必须等到第一次真正需要木种
     * （渲染取材质）的时候才查。
     */
    public static WoodType fromSignBlockName(Block blockIn)
    {
        net.minecraft.util.ResourceLocation id = net.minecraft.util.registry.Registry.BLOCK.getKey(blockIn);

        if (id != null)
        {
            String path = id.getPath();

            for (String suffix : SIGN_SUFFIXES)
            {
                if (path.endsWith(suffix))
                {
                    WoodType woodtype = byName(path.substring(0, path.length() - suffix.length()));

                    if (woodtype != null)
                    {
                        return woodtype;
                    }

                    break;
                }
            }
        }

        return OAK;
    }

    public String getName()
    {
        return this.name;
    }
}
