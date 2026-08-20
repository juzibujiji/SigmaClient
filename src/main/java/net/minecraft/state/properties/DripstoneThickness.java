package net.minecraft.state.properties;

import net.minecraft.util.IStringSerializable;

/**
 * 1.21.11 backport of {@code net.minecraft.world.level.block.state.properties.DripstoneThickness}.
 *
 * <p>Constant order is copied verbatim from the official enum so that the serialized value order
 * matches {@code thickness=[tip_merge,tip,frustum,middle,base]} in the official blocks.json.
 */
public enum DripstoneThickness implements IStringSerializable
{
    TIP_MERGE("tip_merge"),
    TIP("tip"),
    FRUSTUM("frustum"),
    MIDDLE("middle"),
    BASE("base");

    private final String name;

    private DripstoneThickness(String name)
    {
        this.name = name;
    }

    public String toString()
    {
        return this.name;
    }

    public String getString()
    {
        return this.name;
    }
}
