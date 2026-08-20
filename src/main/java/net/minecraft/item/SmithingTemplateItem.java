package net.minecraft.item;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

/**
 * 1.20+ 的锻造模板。
 *
 * <p>对照官方 {@code world/item/SmithingTemplateItem}（MCP-Reborn）移植。官方那个类除了
 * 一段 {@code appendHoverText} 之外全是给锻造台 GUI 用的空槽图标与槽位说明，
 * <b>没有任何游戏逻辑</b> —— 升级和纹饰是配方与数据组件干的活，模板本身只是材料。
 *
 * <p>1.16.4 的锻造台只有「钻石升下界合金」一种硬编码配方，不读模板，
 * 所以这里只移植 tooltip：模板名后缀、可应用于、所需原材料。三行的排版与配色
 * （标题灰、描述蓝、中间空一行）和官方逐字一致，翻译键也是官方那套，
 * zh_cn 直接生效（「可应用于：」「所需原材料：」）。
 *
 * <p>空槽图标（{@code getBaseSlotEmptyIcons} 等）没有移植：1.16.4 的
 * {@code SmithingTableContainer} 不画空槽提示图，移了也没有调用方。
 */
public class SmithingTemplateItem extends Item
{
    private static final TextFormatting TITLE_FORMAT = TextFormatting.GRAY;
    private static final TextFormatting DESCRIPTION_FORMAT = TextFormatting.BLUE;

    private static final String KEY_SUFFIX = "item.minecraft.smithing_template";
    private static final String KEY_APPLIES_TO = "item.minecraft.smithing_template.applies_to";
    private static final String KEY_INGREDIENTS = "item.minecraft.smithing_template.ingredients";

    /**
     * 模板的用途。官方用两个静态工厂区分
     * （{@code createNetheriteUpgradeTemplate} / {@code createArmorTrimTemplate}），
     * 这里用枚举，好让生成器一个参数就选定。
     *
     * <p>翻译键写成<b>完整字面量</b>而不是拼接，是因为 {@code ExtractAssets} 靠扫源码里的
     * 键字面量来决定该从官方语言文件里捞哪些键。拼接出来的键扫不到，中文和英文就都会缺，
     * tooltip 直接把键名显示给玩家。
     */
    public enum Variant
    {
        /** 官方 {@code createNetheriteUpgradeTemplate}。 */
        NETHERITE_UPGRADE(
                "item.minecraft.smithing_template.netherite_upgrade.applies_to",
                "item.minecraft.smithing_template.netherite_upgrade.ingredients"),
        /** 官方 {@code createArmorTrimTemplate}，18 种纹饰模板共用。 */
        ARMOR_TRIM(
                "item.minecraft.smithing_template.armor_trim.applies_to",
                "item.minecraft.smithing_template.armor_trim.ingredients");

        private final String appliesToKey;
        private final String ingredientsKey;

        Variant(String appliesToKey, String ingredientsKey)
        {
            this.appliesToKey = appliesToKey;
            this.ingredientsKey = ingredientsKey;
        }
    }

    private final Variant variant;

    public SmithingTemplateItem(Variant variant, Item.Properties builder)
    {
        super(builder);
        this.variant = variant;
    }

    public void addInformation(ItemStack stack, @Nullable World worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn)
    {
        // 官方 appendHoverText 的六行，顺序与空行位置照抄。
        tooltip.add(title(KEY_SUFFIX));
        tooltip.add(StringTextComponent.EMPTY);
        tooltip.add(title(KEY_APPLIES_TO));
        tooltip.add(indented(description(this.variant.appliesToKey)));
        tooltip.add(title(KEY_INGREDIENTS));
        tooltip.add(indented(description(this.variant.ingredientsKey)));
    }

    private static ITextComponent title(String key)
    {
        return new TranslationTextComponent(key).mergeStyle(TITLE_FORMAT);
    }

    private static ITextComponent description(String key)
    {
        return new TranslationTextComponent(key).mergeStyle(DESCRIPTION_FORMAT);
    }

    /** 官方是 {@code CommonComponents.space().append(...)}，1.16.4 没有那个工具类。 */
    private static ITextComponent indented(ITextComponent body)
    {
        return new StringTextComponent(" ").append(body);
    }
}
