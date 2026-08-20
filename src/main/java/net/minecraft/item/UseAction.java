package net.minecraft.item;

public enum UseAction
{
    NONE,
    EAT,
    DRINK,
    BLOCK,
    BOW,
    SPEAR,
    CROSSBOW,

    // ---- Backported from 1.21.11 net/minecraft/world/item/ItemUseAnimation ----
    // Appended at the end on purpose: 1.16.4 never serialises this enum (it is derived client-side from
    // the held stack via Item#getUseAction), so adding constants cannot break protocol compatibility.
    /** Official ItemUseAnimation.SPYGLASS (1.17+). Used by {@link SpyglassItem}. */
    SPYGLASS,
    /** Official ItemUseAnimation.TOOT_HORN (1.19+). Used by {@link InstrumentItem}. */
    TOOT_HORN,
    /** Official ItemUseAnimation.BRUSH (1.20+). Used by {@link BrushItem}. */
    BRUSH;
}
