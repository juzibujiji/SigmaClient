package extract;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/**
 * 从官方混淆 server.jar 提取每个方块的硬度与爆炸抗性，遍历注册表以保证不漏。
 *
 * 1.21.11 混淆名（由 official client mappings 得出）：
 *   BuiltInRegistries=mi, 其 BLOCK 字段=e        Registry=jq, getKey(Object)=b
 *   Block=dzq, defaultBlockState=d               BlockState=eoh
 *   BlockBehaviour=eog, explosionResistance=G    BlockStateBase.destroySpeed=p
 *   SharedConstants=w, tryDetectVersion=a        Bootstrap=amv, bootStrap=a
 */
public class ExtractHardness {
    public static void main(String[] args) throws Exception {
        Class.forName("w").getMethod("a").invoke(null);
        Class.forName("amv").getMethod("a").invoke(null);

        Class<?> blockCls  = Class.forName("dzq");
        Class<?> stateCls  = Class.forName("eoh");
        Class<?> behaviour = Class.forName("eog");
        Class<?> registry  = Class.forName("jq");

        Field fDefState = blockCls.getDeclaredField("d");  fDefState.setAccessible(true);
        Field fResist   = behaviour.getDeclaredField("G"); fResist.setAccessible(true);
        Field fHardness = findUp(stateCls, "p");

        Field fBlockReg = Class.forName("mi").getDeclaredField("e"); fBlockReg.setAccessible(true);
        Object blockRegistry = fBlockReg.get(null);
        Method getKey = registry.getMethod("b", Object.class);

        StringBuilder out = new StringBuilder("identifier,hardness,resistance\n");
        List<String> failed = new ArrayList<>();
        int ok = 0;
        for (Object block : (Iterable<?>) blockRegistry) {
            String id = "?";
            try {
                id = String.valueOf(getKey.invoke(blockRegistry, block)).replace("minecraft:", "");
                Object state = fDefState.get(block);
                out.append(id).append(',')
                   .append((float) fHardness.get(state)).append(',')
                   .append((float) fResist.get(block)).append('\n');
                ok++;
            } catch (Throwable t) { failed.add(id + "(" + t.getClass().getSimpleName() + ")"); }
        }
        Files.writeString(Paths.get("block-hardness-1.21.11.csv"), out.toString());
        System.err.println("提取成功=" + ok + "  失败=" + failed.size() + (failed.isEmpty() ? "" : " " + failed));
    }
    static Field findUp(Class<?> c, String name) throws Exception {
        for (Class<?> k = c; k != null; k = k.getSuperclass())
            try { Field f = k.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(name);
    }
}
