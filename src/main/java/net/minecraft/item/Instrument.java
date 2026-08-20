package net.minecraft.item;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

/**
 * Backport of the 1.19 instrument definition.
 *
 * Official source: net/minecraft/world/item/Instrument.java (1.21.11 / MCP-Reborn-release), a record:
 *     record Instrument(Holder&lt;SoundEvent&gt; soundEvent, float useDuration, float range, Component description)
 *
 * In 1.19+ instruments live in a datapack registry (Registries.INSTRUMENT). 1.16.4 has no dynamic registry
 * infrastructure, so this is a plain immutable holder and {@link Instruments} acts as the registry.
 * {@code useDuration} is in SECONDS (official ExtraCodecs.POSITIVE_FLOAT "use_duration") and {@code range}
 * is in blocks.
 */
public class Instrument
{
    private final ResourceLocation id;
    private final SoundEvent soundEvent;
    private final float useDuration;
    private final float range;
    private final ITextComponent description;

    public Instrument(ResourceLocation id, SoundEvent soundEvent, float useDuration, float range)
    {
        this.id = id;
        this.soundEvent = soundEvent;
        this.useDuration = useDuration;
        this.range = range;
        // Official Instruments#register: Component.translatable(Util.makeDescriptionId("instrument", key.location()))
        this.description = new TranslationTextComponent("instrument." + id.getNamespace() + "." + id.getPath());
    }

    public ResourceLocation getId()
    {
        return this.id;
    }

    public SoundEvent getSoundEvent()
    {
        return this.soundEvent;
    }

    /** Official Instrument#useDuration - in seconds. */
    public float getUseDuration()
    {
        return this.useDuration;
    }

    /** Official Instrument#range - in blocks. */
    public float getRange()
    {
        return this.range;
    }

    public ITextComponent getDescription()
    {
        return this.description;
    }
}
