package net.minecraft.item;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;

/**
 * Backport of the 1.19 goat horn instrument registry.
 *
 * Official source: net/minecraft/world/item/Instruments.java (1.21.11 / MCP-Reborn-release):
 *     int   GOAT_HORN_RANGE_BLOCKS = 256;
 *     float GOAT_HORN_DURATION     = 7.0F;
 *     register(ctx, PONDER_GOAT_HORN, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0), 7.0F, 256.0F);
 *     ... one per variant, in this exact order:
 *     ponder, sing, seek, feel, admire, call, yearn, dream
 *
 * 1.16.4 has no "item.goat_horn.sound.0" .. ".7" sound events (added in 1.19) and this project ships no
 * extra sound assets, so each of the eight variants is mapped to a distinct stock note-block instrument.
 * The eight-variant mechanic, the 7 second duration, the 256 block range and the 140 tick cooldown are all
 * preserved exactly; only the timbre differs. Swap the SoundEvent on each line below once the real
 * goat_horn assets are registered.
 */
public class Instruments
{
    /** Official Instruments.GOAT_HORN_RANGE_BLOCKS. */
    public static final int GOAT_HORN_RANGE_BLOCKS = 256;
    /** Official Instruments.GOAT_HORN_DURATION - in seconds. */
    public static final float GOAT_HORN_DURATION = 7.0F;

    /** Official Instruments.PONDER_GOAT_HORN - official sound GOAT_HORN_SOUND_VARIANTS[0]. */
    public static final Instrument PONDER_GOAT_HORN = create("ponder_goat_horn", SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO);
    /** Official Instruments.SING_GOAT_HORN - official sound GOAT_HORN_SOUND_VARIANTS[1]. */
    public static final Instrument SING_GOAT_HORN = create("sing_goat_horn", SoundEvents.BLOCK_NOTE_BLOCK_FLUTE);
    /** Official Instruments.SEEK_GOAT_HORN - official sound GOAT_HORN_SOUND_VARIANTS[2]. */
    public static final Instrument SEEK_GOAT_HORN = create("seek_goat_horn", SoundEvents.BLOCK_NOTE_BLOCK_BASS);
    /** Official Instruments.FEEL_GOAT_HORN - official sound GOAT_HORN_SOUND_VARIANTS[3]. */
    public static final Instrument FEEL_GOAT_HORN = create("feel_goat_horn", SoundEvents.BLOCK_NOTE_BLOCK_COW_BELL);
    /** Official Instruments.ADMIRE_GOAT_HORN - official sound GOAT_HORN_SOUND_VARIANTS[4]. */
    public static final Instrument ADMIRE_GOAT_HORN = create("admire_goat_horn", SoundEvents.BLOCK_NOTE_BLOCK_BELL);
    /** Official Instruments.CALL_GOAT_HORN - official sound GOAT_HORN_SOUND_VARIANTS[5]. */
    public static final Instrument CALL_GOAT_HORN = create("call_goat_horn", SoundEvents.BLOCK_NOTE_BLOCK_GUITAR);
    /** Official Instruments.YEARN_GOAT_HORN - official sound GOAT_HORN_SOUND_VARIANTS[6]. */
    public static final Instrument YEARN_GOAT_HORN = create("yearn_goat_horn", SoundEvents.BLOCK_NOTE_BLOCK_HARP);
    /** Official Instruments.DREAM_GOAT_HORN - official sound GOAT_HORN_SOUND_VARIANTS[7]. */
    public static final Instrument DREAM_GOAT_HORN = create("dream_goat_horn", SoundEvents.BLOCK_NOTE_BLOCK_XYLOPHONE);

    /** In official registration order. Used as the creative-tab / loot order and for NBT-less fallbacks. */
    public static final List<Instrument> GOAT_HORNS = ImmutableList.of(
                PONDER_GOAT_HORN,
                SING_GOAT_HORN,
                SEEK_GOAT_HORN,
                FEEL_GOAT_HORN,
                ADMIRE_GOAT_HORN,
                CALL_GOAT_HORN,
                YEARN_GOAT_HORN,
                DREAM_GOAT_HORN);

    private static final Map<ResourceLocation, Instrument> BY_ID = buildIndex();

    private static Instrument create(String name, SoundEvent soundEvent)
    {
        // Official create(): ResourceKey.create(Registries.INSTRUMENT, Identifier.withDefaultNamespace(name))
        return new Instrument(new ResourceLocation(name), soundEvent, GOAT_HORN_DURATION, (float)GOAT_HORN_RANGE_BLOCKS);
    }

    private static Map<ResourceLocation, Instrument> buildIndex()
    {
        ImmutableMap.Builder<ResourceLocation, Instrument> builder = ImmutableMap.builder();

        for (Instrument instrument : GOAT_HORNS)
        {
            builder.put(instrument.getId(), instrument);
        }

        return builder.build();
    }

    @Nullable
    public static Instrument byId(@Nullable ResourceLocation id)
    {
        return id == null ? null : BY_ID.get(id);
    }
}
