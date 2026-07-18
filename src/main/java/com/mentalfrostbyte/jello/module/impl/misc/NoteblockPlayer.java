package com.mentalfrostbyte.jello.module.impl.misc;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRender3D;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.mentalfrostbyte.jello.util.game.MinecraftUtil;
import com.mentalfrostbyte.jello.util.client.render.Resources;
import com.mentalfrostbyte.jello.util.game.sound.*;
import com.mentalfrostbyte.jello.util.game.world.blocks.BlockUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.NoteBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.CPlayerDiggingPacket;
import net.minecraft.network.play.client.CPlayerPacket;
import net.minecraft.network.play.client.CPlayerTryUseItemOnBlockPacket;
import net.minecraft.network.play.server.SPlaySoundEffectPacket;
import net.minecraft.network.play.server.SPlaySoundPacket;
import net.minecraft.state.properties.NoteBlockInstrument;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import org.lwjgl.opengl.GL11;
import team.sdhq.eventBus.annotations.EventTarget;

import java.io.File;
import java.util.*;

public class NoteblockPlayer extends Module {
    public int field23638;
    private NBSFile nbsFile;
    // 演奏节奏用真实时间调度，避免 Math.round(getTempo()) 把高 TPS 歌曲量化成每 tick 一播导致偏快。
    // 仅用于演奏分支，不影响调音逻辑。
    private long nextNoteTimeMs = -1L;
    private List<String> field23640 = new ArrayList<>();
    private final List<Class6463> field23641 = new ArrayList<>();
    private final List<BlockPos> positions = new ArrayList<>();

    public NoteblockPlayer() {
        super(ModuleCategory.MISC, "NoteblockPlayer", "Plays noteblocks! Needs NBS files in sigma5/nbs");
        File nbsFile = new File(Client.getInstance().file + "/nbs");
        if (nbsFile.exists()) {
            this.field23640 = new ArrayList<>(Arrays.asList(nbsFile.list()));

            for (int var4 = 0; var4 < this.field23640.size(); var4++) {
                if (this.field23640.get(var4).startsWith(".")) {
                    this.field23640.remove(var4);
                    break;
                }
            }

            String[] var5 = new String[this.field23640.size()];
            var5 = this.field23640.toArray(var5);
            if (var5.length > 0) {
                this.registerSetting(new ModeSetting("Song", "songs", 0, var5));
            }
        } else {
            nbsFile.mkdirs();
        }
    }

    public static void handlerRenderingAt(BlockPos var0) {
        double var3 = (double) ((float) var0.getX() + 0.5F)
                - Minecraft.getInstance().gameRenderer.getActiveRenderInfo().getPos().getX();
        double var5 = (double) ((float) var0.getY() + 1.0F)
                - Minecraft.getInstance().gameRenderer.getActiveRenderInfo().getPos().getY();
        double var7 = (double) ((float) var0.getZ() + 0.5F)
                - Minecraft.getInstance().gameRenderer.getActiveRenderInfo().getPos().getZ();
        GL11.glBlendFunc(770, 771);
        GL11.glEnable(3042);
        GL11.glEnable(2848);
        GL11.glLineWidth(1.0F);
        GL11.glDisable(3553);
        GL11.glDisable(2929);
        GL11.glDepthMask(false);
        GL11.glColor4d(1.0, 1.0, 1.0, 1.0);
        Vector3d var9 = new Vector3d(0.0, 0.0, 1.0)
                .rotatePitch(-((float) Math.toRadians(Minecraft.getInstance().player.rotationPitch)))
                .rotateYaw(-((float) Math.toRadians(Minecraft.getInstance().player.rotationYaw)));
        GL11.glBegin(1);
        GL11.glVertex3d(var9.x, var9.y, var9.z);
        GL11.glVertex3d(var3, var5, var7);
        GL11.glEnd();
        GL11.glEnable(3553);
        GL11.glEnable(2929);
        GL11.glDisable(2848);
        GL11.glDepthMask(true);
        GL11.glDisable(3042);
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @EventTarget
    public void method16405(EventMotion var1) {
        if (this.isEnabled()) {
            if (this.nbsFile != null) {
                if (mc.playerController.isInCreativeMode()) {
                    MinecraftUtil.addChatMessage("§cNoteBlockPlayer isn't available in creative mode!");
                    this.setEnabled(false);
                } else {
                    // 去掉每 4 tick 的节流，调音（错开重复音高）每 tick 都进行，加快调音速度。
                    // 仍是一次调整一个音符盒并依赖回包更新音高，不会超调发散。
                    if (!this.method16407(this.field23641, var1)) {
                        this.method16408(this.field23641, var1);
                    }

                    if (this.method16406(this.field23641)) {
                        // 每个 NBS tick 的真实间隔（毫秒）= 50 * getTempo()（getTempo() = 20 / TPS）。
                        // 用真实时间判断而不是把间隔四舍五入成整数游戏 tick，否则高 TPS 歌曲会被量化到 20 TPS 偏快。
                        long now = System.currentTimeMillis();
                        double intervalMs = 50.0 * this.nbsFile.getTempo();
                        if (this.nextNoteTimeMs < 0L) {
                            this.nextNoteTimeMs = now;
                        }

                        if (now >= this.nextNoteTimeMs) {
                            // 累加间隔以补偿误差；若落后超过一个间隔（卡顿），重新对齐到当前时间，避免快速连播补齐。
                            this.nextNoteTimeMs += (long) intervalMs;
                            if (now >= this.nextNoteTimeMs) {
                                this.nextNoteTimeMs = now + (long) intervalMs;
                            }

                            if (this.field23638 > this.nbsFile.getShort2()) {
                                this.field23638 = 0;
                            }

                            this.positions.clear();

                            for (Class9616 var5 : this.nbsFile.method9950().values()) {
                                Class8255 var6 = var5.method37433(this.field23638);
                                if (var6 != null) {
                                    for (Class6463 var8 : this.field23641) {
                                        if ((var6.method28780() != 3 && this.countByNbsInstrument(var6.method28780()) == 0
                                                || var8.method19640() == var6.method28780())
                                                && Class2121.method8807(
                                                var8.field28402) == (float) foldKeyToPitch(var6.method28782())
                                                && Math.sqrt(mc.player.getPosition()
                                                .distanceSq(var8.field28401)) < (double) mc.playerController
                                                .getBlockReachDistance()) {
                                            float[] var9 = BlockUtil.method34542(var8.field28401, Direction.UP);
                                            if ((double) var8.field28401.getY() > mc.player.getPosY() + 1.0) {
                                                var9 = BlockUtil.method34542(var8.field28401, Direction.DOWN);
                                            }

                                            var1.setYaw(var9[0]);
                                            var1.setPitch(var9[1]);

                                            mc.getConnection()
                                                    .sendPacket(new CPlayerDiggingPacket(
                                                            CPlayerDiggingPacket.Action.START_DESTROY_BLOCK,
                                                            var8.field28401, Direction.UP));
                                            mc.player.swingArm(Hand.MAIN_HAND);
                                            this.positions.add(var8.field28401);
                                        }
                                    }
                                }
                            }

                            this.field23638++;
                        }
                    }
                }
            }
        }
    }

    public boolean method16406(List<Class6463> var1) {
        for (Class6463 var5 : var1) {
            if ((var5.field28402 == -1.0F || this.method16411(var5.field28402, var5.instrument))
                    && Math.sqrt(mc.player.getPosition().distanceSq(var5.field28401)) < (double) mc.playerController
                    .getBlockReachDistance()) {
                return false;
            }
        }

        return true;
    }

    public boolean method16407(List<Class6463> var1, EventMotion event) {
        for (Class6463 var5 : var1) {
            if (var5.field28402 == -1.0F && Math.sqrt(mc.player.getPosition()
                    .distanceSq(var5.field28401)) < (double) mc.playerController.getBlockReachDistance()) {
                float[] var6 = BlockUtil.method34542(var5.field28401, Direction.UP);
                event.setYaw(var6[0]);
                event.setPitch(var6[1]);

                mc.getConnection().sendPacket(new CPlayerDiggingPacket(CPlayerDiggingPacket.Action.START_DESTROY_BLOCK,
                        var5.field28401, Direction.UP));
                mc.player.swingArm(Hand.MAIN_HAND);
                this.positions.clear();
                this.positions.add(var5.field28401);
                return true;
            }
        }

        return false;
    }

    public void method16407(List<Class6463> var1) {
        for (Class6463 var5 : var1) {
            if (var5.field28402 == -1.0F && Math.sqrt(mc.player.getPosition()
                    .distanceSq(var5.field28401)) < (double) mc.playerController.getBlockReachDistance()) {
                float[] var6 = BlockUtil.method34542(var5.field28401, Direction.UP);
                mc.getConnection().sendPacket(new CPlayerPacket.RotationPacket(var6[0], var6[1], mc.player.isOnGround()));

                mc.player.rotationYawHead = var6[0];
                mc.player.renderYawOffset = var6[0];
                mc.getConnection().sendPacket(new CPlayerDiggingPacket(CPlayerDiggingPacket.Action.START_DESTROY_BLOCK,
                        var5.field28401, Direction.UP));
                mc.player.swingArm(Hand.MAIN_HAND);
                this.positions.clear();
                this.positions.add(var5.field28401);
                return;
            }
        }

    }

    public void method16408(List<Class6463> var1) {
        for (Class6463 var5 : var1) {
            if (this.method16411(var5.field28402, var5.instrument)
                    && Math.sqrt(mc.player.getPosition().distanceSq(var5.field28401)) < (double) mc.playerController
                    .getBlockReachDistance()) {
                float[] var6 = BlockUtil.method34542(var5.field28401, Direction.UP);
                mc.player.swingArm(Hand.MAIN_HAND);
                mc.getConnection().sendPacket(new CPlayerPacket.RotationPacket(var6[0], var6[1], mc.player.isOnGround()));
                mc.getConnection().sendPacket(new CPlayerTryUseItemOnBlockPacket(Hand.MAIN_HAND,
                        BlockUtil.rayTrace(var6[0], var6[1], mc.playerController.getBlockReachDistance() + 1.0F)));
                this.positions.clear();
                this.positions.add(var5.field28401);

                return;
            }
        }

    }

    public void method16408(List<Class6463> var1, EventMotion event) {
        for (Class6463 var5 : var1) {
            if (this.method16411(var5.field28402, var5.instrument)
                    && Math.sqrt(mc.player.getPosition().distanceSq(var5.field28401)) < (double) mc.playerController
                    .getBlockReachDistance()) {
                float[] var6 = BlockUtil.method34542(var5.field28401, Direction.UP);
                mc.player.swingArm(Hand.MAIN_HAND);
                event.setYaw(var6[0]);
                event.setPitch(var6[1]);
                mc.getConnection().sendPacket(new CPlayerTryUseItemOnBlockPacket(Hand.MAIN_HAND,
                        BlockUtil.rayTrace(var6[0], var6[1], mc.playerController.getBlockReachDistance() + 1.0F)));
                this.positions.clear();
                this.positions.add(var5.field28401);

                return;
            }
        }

    }

    @EventTarget
    public void method16409(EventRender3D var1) {
        if (this.isEnabled()) {
            for (BlockPos pos : this.positions) {
                handlerRenderingAt(pos);
            }
        }
    }

    public boolean method16411(float var1, NoteBlockInstrument var2) {
        int var5 = 0;

        for (Class6463 var7 : this.field23641) {
            if (var7.field28402 == var1 && var1 != -1.0F && var7.instrument == var2) {
                var5++;
            }
        }

        return var5 > 1;
    }

    @EventTarget
    public void onPacket(EventReceivePacket var1) {
        if (this.isEnabled()) {
            if (this.field23641 != null) {
                if (var1.packet instanceof SPlaySoundEffectPacket var4) {

					for (int var5 = 0; var5 < this.field23641.size(); var5++) {
                        Class6463 var6 = this.field23641.get(var5);
                        if (var6.field28401
                                .equals(new BlockPos(var4.getX(), var4.getY(), var4.getZ()))) {
                            var6.field28402 = var4.getPitch();
                            this.field23641.set(var5, var6);
                        }
                    }
                }

                if (var1.packet instanceof SPlaySoundPacket var7) {

					for (int var8 = 0; var8 < this.field23641.size(); var8++) {
                        Class6463 var9 = this.field23641.get(var8);
                        if (var9.field28401
                                .equals(new BlockPos(var7.getX(), var7.getY(), var7.getZ()))) {
                            var9.field28402 = var7.getPitch();
                            this.field23641.set(var8, var9);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onEnable() {
        if (!mc.playerController.isInCreativeMode()) {
            if (this.field23640.isEmpty()) {
                MinecraftUtil.addChatMessage(
                        "§cNo Song available! Place NBS formated files in sigma5/nbs and restart the client to try again!");
                MinecraftUtil.addChatMessage("§cPlaying the only integrated demo song!");
                this.nbsFile = NBSFileReader.fromInputStream(
                        Resources.readInputStream("com/mentalfrostbyte/gui/resources/music/rememberthis.nbs"));
                if (this.nbsFile == null) {
                    MinecraftUtil.addChatMessage("§cError loading included song, wtf!");
                    this.setEnabled(false);
                    return;
                }
            } else {
                File var3 = new File(Client.getInstance().file + "/nbs/" + this.getStringSettingValueByName("Song"));
                this.nbsFile = NBSFileReader.fromFile(var3);
                if (this.nbsFile == null) {
                    MinecraftUtil.addChatMessage("§cError loading song! Supported formats: NBS v0-v5. Check console for details.");
                    this.setEnabled(false);
                    return;
                }
            }

            System.out.println(this.nbsFile.getSongName());
            MinecraftUtil.addChatMessage("Now Playing: " + this.nbsFile.getSongName());
            if (Math.floor(20.0F / this.nbsFile.getTempo()) != (double) (20.0F / this.nbsFile.getTempo())) {
                MinecraftUtil.addChatMessage(
                        "§eNote: Non-integer tempo (" + this.nbsFile.getTempo()
                                + " TPS). Playback works but timing may drift slightly.");
            }

            this.field23638 = 0;
            this.nextNoteTimeMs = -1L;
            this.field23641.clear();

            for (BlockPos var4 : BlockUtil.getBlockPositionsInRange(mc.playerController.getBlockReachDistance())) {
                BlockState var5 = mc.world.getBlockState(var4);
                if (var5.getBlock() instanceof NoteBlock) {
                    Class6463 var6 = new Class6463(var4);
                    if (this.method16414(var6) <= 24) {
                        this.field23641.add(new Class6463(var4));
                    }
                }
            }

            this.method16407(this.field23641);
            this.method16408(this.field23641);
        } else {
            MinecraftUtil.addChatMessage("§cNoteBlockPlayer isn't available in creative mode!");
            this.setEnabled(false);
        }
    }

    private int method16414(Class6463 var1) {
        Map<NoteBlockInstrument, Integer> var4 = new HashMap<>();

        for (Class6463 var6 : this.field23641) {
            int var7 = var4.getOrDefault(var6.instrument, 0);
            var4.put(var6.instrument, var7 + 1);
        }

        return var4.getOrDefault(var1.instrument, 0);
    }

    /**
     * 把 NBS 音符的 key 折叠成音符盒可播放的 pitch（0-24）。
     * 音符盒只有 25 个音高（NBS key 33-57）。超出这个范围的音符（很多曲子有）
     * 若直接用 key-33 去匹配，会得到负数或 >24 的值，世界里没有对应音高的音符盒，
     * 导致这些音丢失或匹配到错误音符盒 → 听起来乱/走音。
     * 标准做法：按八度（12 个半音）把越界音高折叠进 0-24 范围。
     */
    private static int foldKeyToPitch(int key) {
        int pitch = key - 33;
        // 低于最低音就整八度升高，高于最高音就整八度降低，直到落进 0-24。
        // 只改八度、不改音名，保证音高就近折叠而不走调。
        while (pitch < 0) {
            pitch += 12;
        }
        while (pitch > 24) {
            pitch -= 12;
        }
        return pitch;
    }

    /**
     * 统计世界中与指定 NBS 乐器 ID 匹配的音符盒数量。
     * 用于兜底逻辑：当没有对应乐器的音符盒时，允许用任意音符盒代替。
     */
    private int countByNbsInstrument(byte nbsInstrumentId) {
        int count = 0;
        for (Class6463 var6 : this.field23641) {
            if (var6.method19640() == nbsInstrumentId) {
                count++;
            }
        }
        return count;
    }

    public static class Class6463 {
        public BlockPos field28401;
        public float field28402 = -1.0F;
        public NoteBlockInstrument instrument;

        public Class6463(BlockPos var1) {
            this.field28401 = var1;
            this.instrument = NoteBlockInstrument.byState(mc.world.getBlockState(var1.down()));
        }

        public int method19640() {
            // 返回此音符盒对应的 NBS 乐器 ID（与 Class9705 / NBS 格式一致）。
            // 不能用 ordinal()-1，因为 NoteBlockInstrument 枚举顺序和 NBS 乐器 ID 顺序不同。
            return switch (this.instrument) {
                case HARP -> 0;
                case BASS -> 1;
                case BASEDRUM -> 2;
                case SNARE -> 3;
                case HAT -> 4;
                case GUITAR -> 5;
                case FLUTE -> 6;
                case BELL -> 7;
                case CHIME -> 8;
                case XYLOPHONE -> 9;
                case IRON_XYLOPHONE -> 10;
                case COW_BELL -> 11;
                case DIDGERIDOO -> 12;
                case BIT -> 13;
                case BANJO -> 14;
                case PLING -> 15;
            };
        }
    }
}
