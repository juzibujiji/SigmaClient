package com.mentalfrostbyte.jello.module.impl.misc;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRender3D;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.managers.RotationManager;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.MinecraftUtil;
import com.mentalfrostbyte.jello.util.client.render.Resources;
import com.mentalfrostbyte.jello.util.game.sound.*;
import com.mentalfrostbyte.jello.util.game.world.blocks.BlockUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.NoteBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.CPlayerDiggingPacket;
import net.minecraft.network.play.client.CPlayerTryUseItemOnBlockPacket;
import net.minecraft.state.properties.NoteBlockInstrument;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import org.lwjgl.opengl.GL11;
import team.sdhq.eventBus.annotations.EventTarget;

import java.io.File;
import java.util.*;

public class NoteblockPlayer extends Module {
    public int field23638;
    private long lastTickTime = 0L;
    private NBSFile nbsFile;
    private List<String> field23640 = new ArrayList<>();
    private final List<Class6463> field23641 = new ArrayList<>();
    private final List<BlockPos> positions = new ArrayList<>();

    // 预分配表：key = 乐器ID * 25 + 音高(0-24) → 负责这个音的唯一音符盒。
    // 一个音符只敲它对应的这一个盒子，避免重复敲响、音量翻倍和抢占配额。
    private final Map<Integer, Class6463> assignments = new HashMap<>();
    // 调音节流：右键发出去后，方块音高要等服务器回包才会更新，隔几 tick 再复查。
    private int tuneDelay = 0;

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

        this.registerSetting(new NumberSetting<>("Max notes per tick", "Maximum noteblocks hit in a single tick", 8.0F, 1.0F, 32.0F, 1.0F));
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
        this.assignments.clear();
        this.tuneDelay = 0;
    }

    @EventTarget
    public void method16405(EventUpdate var1) {
        if (!this.isEnabled() || this.nbsFile == null) {
            return;
        }

        if (mc.playerController.isInCreativeMode()) {
            MinecraftUtil.addChatMessage("§cNoteBlockPlayer isn't available in creative mode!");
            this.setEnabled(false);
            return;
        }

        // 播放前先把每个已分配的音符盒调到它负责的音高。全部调好之前不开始播放。
        if (!this.tuneStep()) {
            this.lastTickTime = 0L;
            return;
        }

        // 基于真实时间驱动播放，而不是量化到整数个游戏 tick。
        // getTempo() = 20 / TPS（每个 NBS tick 折算的游戏 tick 数），
        // 所以每个 NBS tick 的真实时长 = 50 * getTempo() = 1000 / TPS 毫秒。
        // 用累加器推进，避免因 round() 量化导致的整首歌加速/减速和长期漂移。
        long now = System.currentTimeMillis();
        double msPerNbsTick = 50.0 * this.nbsFile.getTempo();
        if (this.lastTickTime == 0L) {
            this.lastTickTime = now;
        }

        int maxNotesPerTick = (int) this.getNumberValueBySettingName("Max notes per tick");

        // 一次事件内可能需要推进多个 NBS tick（TPS > 20 时），也可能一个都不推进
        // （TPS < 20 时）。限制单次最多补 8 个 tick，防止卡顿后一次性补太多导致乱响。
        int guard = 0;
        while ((double) (now - this.lastTickTime) >= msPerNbsTick && guard++ < 8) {
            this.lastTickTime += (long) msPerNbsTick;
            this.playNbsTick(maxNotesPerTick);
        }
    }

    // 播放单个 NBS tick：把这一刻要响的每个 (乐器, 音高) 只敲一次它对应的那个盒子。
    private void playNbsTick(int maxNotesPerTick) {
        if (this.field23638 > this.nbsFile.getShort2()) {
            this.field23638 = 0;
        }

        // 先收集这一 tick 需要响的所有 (乐器, 音高) key，天然去重：
        // 多个音轨落在同一个音上时，只敲一次那个盒子。
        Set<Integer> keysThisTick = new LinkedHashSet<>();
        for (Class9616 layer : this.nbsFile.method9950().values()) {
            Class8255 note = layer.method37433(this.field23638);
            if (note != null) {
                int n = this.clampNote(note.method28782() - 33);
                keysThisTick.add(note.method28780() * 25 + n);
            }
        }

        this.positions.clear();
        int hitsThisTick = 0;

        for (int key : keysThisTick) {
            if (hitsThisTick >= maxNotesPerTick) {
                break;
            }

            Class6463 block = this.assignments.get(key);
            if (block == null || !this.withinReach(block.field28401)) {
                continue;
            }

            Direction face = this.hitFace(block.field28401);
            float[] rot = BlockUtil.getRotationsToBlockFace(block.field28401, face);
            RotationManager.setRotations(rot[0], rot[1]);
            mc.getConnection().sendPacket(new CPlayerDiggingPacket(
                    CPlayerDiggingPacket.Action.START_DESTROY_BLOCK, block.field28401, face));
            mc.player.swingArm(Hand.MAIN_HAND);
            this.positions.add(block.field28401);
            hitsThisTick++;
        }

        this.field23638++;
    }

    // 每 tick 调一次：找一个音高还不对、且在触及范围内的已分配盒子，右键把它拧到目标音。
    // 一次只处理一个盒子，节流后返回 false；全部到位时返回 true。
    private boolean tuneStep() {
        Class6463 target = null;
        int currentNote = -1;

        for (Class6463 block : this.field23641) {
            if (block.targetNote == -1) {
                continue;
            }

            int cur = this.getCurrentNote(block.field28401);
            if (cur != -1 && cur != block.targetNote && this.withinReach(block.field28401)) {
                target = block;
                currentNote = cur;
                break;
            }
        }

        if (target == null) {
            this.tuneDelay = 0;
            return true;
        }

        // 上次右键发出的音高变化还没被服务器确认，等几 tick 再复查，避免拧过头。
        if (this.tuneDelay < 5) {
            this.tuneDelay++;
            return false;
        }

        // 右键循环 0→1→…→24→0，从当前音拧到目标音需要的次数。
        int reqTunes = (target.targetNote - currentNote + 25) % 25;
        // 命中面：盒子在头顶（+上方有阻挡）时点它的下面，否则点上面。
        Direction face = this.hitFace(target.field28401);
        float[] rot = BlockUtil.getRotationsToBlockFace(target.field28401, face);
        RotationManager.setRotations(rot[0], rot[1]);
        // 直接对准目标盒子构造命中结果，而不是发射世界射线。
        // 密集摆放时世界射线会先撞到别的盒子/方块，导致拧错盒子（把已调好的拧乱），
        // 或压根没命中目标 → 目标音高永远不变 → tuneStep 卡在同一个盒子上永远不开始播放。
        BlockRayTraceResult ray = this.blockHit(target.field28401, face);
        for (int i = 0; i < reqTunes; i++) {
            mc.getConnection().sendPacket(new CPlayerTryUseItemOnBlockPacket(Hand.MAIN_HAND, ray));
        }
        mc.player.swingArm(Hand.MAIN_HAND);

        this.tuneDelay = 0;
        return false;
    }

    // 直接从方块状态读音符盒当前音高(0-24)，不用等声音包。非音符盒返回 -1。
    private int getCurrentNote(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (!(state.getBlock() instanceof NoteBlock)) {
            return -1;
        }
        return state.get(NoteBlock.NOTE);
    }

    // 音符盒只能表现 0-24 两个八度；超范围的音按八度移调进范围，尽量不丢音。
    private int clampNote(int note) {
        while (note < 0) {
            note += 12;
        }
        while (note > 24) {
            note -= 12;
        }
        return note;
    }

    private boolean withinReach(BlockPos pos) {
        return Math.sqrt(mc.player.getPosition().distanceSq(pos)) < (double) mc.playerController.getBlockReachDistance();
    }

    // 盒子在头顶上方时敲/拧它的下表面，否则用上表面。上下都能触发音符盒。
    private Direction hitFace(BlockPos pos) {
        return pos.getY() > mc.player.getPosY() + 1.0 ? Direction.DOWN : Direction.UP;
    }

    // 直接对准指定盒子的指定面构造命中结果，不发射世界射线。
    // 保证右键/左键落在这个盒子上，不会被密集摆放里更近的方块抢走。
    private BlockRayTraceResult blockHit(BlockPos pos, Direction face) {
        double y = face == Direction.DOWN ? (double) pos.getY() : (double) pos.getY() + 1.0;
        Vector3d hit = new Vector3d((double) pos.getX() + 0.5, y, (double) pos.getZ() + 0.5);
        return new BlockRayTraceResult(hit, face, pos, false);
    }

    @EventTarget
    public void method16409(EventRender3D var1) {
        if (this.isEnabled()) {
            for (BlockPos pos : this.positions) {
                handlerRenderingAt(pos);
            }
        }
    }

    @Override
    public void onEnable() {
        if (mc.playerController.isInCreativeMode()) {
            MinecraftUtil.addChatMessage("§cNoteBlockPlayer isn't available in creative mode!");
            this.setEnabled(false);
            return;
        }

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
            MinecraftUtil.addChatMessage("§eNote: Non-integer tempo (" + this.nbsFile.getTempo() + " TPS). Playback works but timing may drift slightly.");
        }

        this.field23638 = 0;
        this.lastTickTime = 0L;
        this.tuneDelay = 0;
        this.field23641.clear();

        for (BlockPos var4 : BlockUtil.getBlockPositionsInRange(mc.playerController.getBlockReachDistance())) {
            BlockState var5 = mc.world.getBlockState(var4);
            if (var5.getBlock() instanceof NoteBlock) {
                this.field23641.add(new Class6463(var4));
            }
        }

        this.buildAssignments();
    }

    // 预分配：为整首歌需要的每个 (乐器, 音高) 组合，各指派一个专属音符盒。
    // 同一乐器有几个不同音高，就需要几个该乐器的盒子（一个盒子同一时刻只能是一个音）。
    private void buildAssignments() {
        this.assignments.clear();
        for (Class6463 block : this.field23641) {
            block.targetNote = -1;
        }

        // 收集整首歌用到的全部 (乐器, 音高) key（去重）。
        Set<Integer> requiredKeys = new LinkedHashSet<>();
        for (Class9616 layer : this.nbsFile.method9950().values()) {
            for (Class8255 note : layer.method37429().values()) {
                if (note != null) {
                    int n = this.clampNote(note.method28782() - 33);
                    requiredKeys.add(note.method28780() * 25 + n);
                }
            }
        }

        Map<Integer, Integer> requiredPerInstrument = new HashMap<>();
        Map<Integer, Integer> foundPerInstrument = new HashMap<>();

        for (int key : requiredKeys) {
            int instrument = key / 25;
            int note = key % 25;
            requiredPerInstrument.merge(instrument, 1, Integer::sum);

            for (Class6463 block : this.field23641) {
                if (block.targetNote == -1 && block.method19640() == instrument) {
                    block.targetNote = note;
                    this.assignments.put(key, block);
                    foundPerInstrument.merge(instrument, 1, Integer::sum);
                    break;
                }
            }
        }

        // 告诉玩家哪种乐器的盒子不够，缺多少。缺的音会静音而不是用错乐器代替。
        for (Map.Entry<Integer, Integer> entry : requiredPerInstrument.entrySet()) {
            int missing = entry.getValue() - foundPerInstrument.getOrDefault(entry.getKey(), 0);
            if (missing > 0) {
                MinecraftUtil.addChatMessage(
                        "§eMissing §c" + missing + " §e" + this.instrumentName(entry.getKey()) + " §enoteblock(s)");
            }
        }
    }

    private String instrumentName(int instrumentId) {
        return Class9705.method38022((byte) instrumentId).replace("BLOCK_NOTE_BLOCK_", "");
    }

    public static class Class6463 {
        public BlockPos field28401;
        public NoteBlockInstrument instrument;
        // 预分配后这个盒子要被调到的目标音高(0-24)，-1 表示没有音符用到它。
        public int targetNote = -1;

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
