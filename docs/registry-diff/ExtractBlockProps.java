package extract;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 一次性导出 1.21.11 全部方块的属性，供注册代码生成器使用。
 *
 * 1.21.11 混淆名（来自 official client mappings）：
 *   BuiltInRegistries=mi(BLOCK=e)  Registry=jq(getKey=b)
 *   Block=dzq(defaultBlockState=d)  BlockState=eoh  BlockBehaviour=eog  SoundType=ehs
 *   BlockStateBase: destroySpeed=p  mapColor=o  lightEmission=h  isAir=j
 *                   ignitedByLava=k  liquid=l  legacySolid=m  pushReaction=n
 *   BlockBehaviour:  explosionResistance=G  hasCollision=F  isRandomlyTicking=H  soundType=I
 *   MapColor=flf(id=al)
 */
public class ExtractBlockProps {
    public static void main(String[] args) throws Exception {
        Class.forName("w").getMethod("a").invoke(null);
        Class.forName("amv").getMethod("a").invoke(null);

        Class<?> blockCls = Class.forName("dzq");
        Class<?> stateCls = Class.forName("eoh");
        Class<?> behaviour = Class.forName("eog");
        Class<?> mapColorCls = Class.forName("flf");

        Field fDefState = blockCls.getDeclaredField("d");   fDefState.setAccessible(true);
        Field fResist   = behaviour.getDeclaredField("G");  fResist.setAccessible(true);
        Field fCollision= behaviour.getDeclaredField("F");  fCollision.setAccessible(true);
        Field fTicking  = behaviour.getDeclaredField("H");  fTicking.setAccessible(true);
        Field fSound    = behaviour.getDeclaredField("I");  fSound.setAccessible(true);
        Field fHardness = up(stateCls, "p");
        Field fMapColor = up(stateCls, "o");
        Field fLight    = up(stateCls, "h");
        Field fAir      = up(stateCls, "j");
        Field fLava     = up(stateCls, "k");
        Field fLiquid   = up(stateCls, "l");
        Field fSolid    = up(stateCls, "m");
        Field fMapColorId = mapColorCls.getDeclaredField("al"); fMapColorId.setAccessible(true);

        // SoundType 是 record/常量对象，按实例身份反查它在 SoundType 类里的静态字段名。
        Map<Object,String> soundNames = new IdentityHashMap<>();
        collectStaticNames("net.minecraft.world.level.block.SoundType", "ehs", soundNames);

        Object registry = get(Class.forName("mi").getDeclaredField("e"), null);
        Method getKey = Class.forName("jq").getMethod("b", Object.class);

        StringBuilder out = new StringBuilder(
            "identifier,hardness,resistance,mapColorId,light,sound,hasCollision,randomTick,isAir,ignitedByLava,liquid,legacySolid\n");
        int ok = 0;
        for (Object block : (Iterable<?>) registry) {
            String id = String.valueOf(getKey.invoke(registry, block)).replace("minecraft:", "");
            Object state = fDefState.get(block);
            Object mapColor = fMapColor.get(state);
            Object sound = fSound.get(block);
            out.append(id).append(',')
               .append((float) fHardness.get(state)).append(',')
               .append((float) fResist.get(block)).append(',')
               .append(mapColor == null ? -1 : (int) fMapColorId.get(mapColor)).append(',')
               .append((int) fLight.get(state)).append(',')
               .append(soundNames.getOrDefault(sound, "?")).append(',')
               .append((boolean) fCollision.get(block)).append(',')
               .append((boolean) fTicking.get(block)).append(',')
               .append((boolean) fAir.get(state)).append(',')
               .append((boolean) fLava.get(state)).append(',')
               .append((boolean) fLiquid.get(state)).append(',')
               .append((boolean) fSolid.get(state)).append('\n');
            ok++;
        }
        Files.writeString(Paths.get("block-props-1.21.11.csv"), out.toString(), StandardCharsets.UTF_8);
        System.err.println("导出 " + ok + " 个方块属性");
    }

    /** 解析 mappings，把某个类的静态字段实例反查成它的原名（用于 SoundType 这类常量池）。 */
    static void collectStaticNames(String deobf, String obf, Map<Object,String> into) throws Exception {
        Class<?> cls = Class.forName(obf);
        boolean in = false;
        for (String line : Files.readAllLines(Paths.get("client-mappings.txt"))) {
            if (line.startsWith(deobf + " ->")) { in = true; continue; }
            if (in) {
                if (!line.startsWith(" ") && !line.startsWith("#")) break;
                String t = line.trim();
                if (!t.startsWith(deobf + " ") || !t.contains(" -> ") || t.contains("(")) continue;
                String[] p = t.split(" ");
                if (p.length < 4) continue;
                try {
                    Field f = cls.getDeclaredField(p[3]); f.setAccessible(true);
                    Object v = f.get(null);
                    if (v != null) into.put(v, p[1]);
                } catch (Exception ignored) {}
            }
        }
    }

    static Object get(Field f, Object target) throws Exception { f.setAccessible(true); return f.get(target); }

    static Field up(Class<?> c, String name) throws Exception {
        for (Class<?> k = c; k != null; k = k.getSuperclass())
            try { Field f = k.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(name);
    }
}
