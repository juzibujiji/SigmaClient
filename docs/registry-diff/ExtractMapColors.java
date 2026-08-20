package extract;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** 提取 1.21.11 全部 MapColor（地图颜色）。混淆名：MapColor=flf, MATERIAL_COLORS=am, col=ak, id=al */
public class ExtractMapColors {
    public static void main(String[] args) throws Exception {
        Class.forName("w").getMethod("a").invoke(null);
        Class.forName("amv").getMethod("a").invoke(null);

        // 名称来自 mappings：MapColor 的静态字段（COLOR_* / NONE / GRASS ...）
        Map<String,String> names = new LinkedHashMap<>();
        boolean in = false;
        for (String line : Files.readAllLines(Paths.get("client-mappings.txt"))) {
            if (line.startsWith("net.minecraft.world.level.material.MapColor ->")) { in = true; continue; }
            if (in) {
                if (!line.startsWith(" ") && !line.startsWith("#")) break;
                String t = line.trim();
                if (t.startsWith("net.minecraft.world.level.material.MapColor ") && t.contains(" -> ") && !t.contains("("))
                    { String[] p = t.split(" "); if (p.length >= 4) names.put(p[3], p[1]); }
            }
        }
        Class<?> cls = Class.forName("flf");
        Field fArr = cls.getDeclaredField("am"); fArr.setAccessible(true);
        Field fCol = cls.getDeclaredField("ak"); fCol.setAccessible(true);
        Field fId  = cls.getDeclaredField("al"); fId.setAccessible(true);
        Object[] arr = (Object[]) fArr.get(null);

        // 反查：每个数组元素对应哪个静态字段名
        Map<Object,String> objName = new IdentityHashMap<>();
        for (Map.Entry<String,String> e : names.entrySet()) {
            try { Field f = cls.getDeclaredField(e.getKey()); f.setAccessible(true);
                  Object v = f.get(null); if (v != null) objName.put(v, e.getValue()); } catch (Exception ignored) {}
        }
        StringBuilder out = new StringBuilder("id,name,rgb\n");
        int count = 0, maxId = -1;
        for (Object o : arr) {
            if (o == null) continue;
            int id = (int) fId.get(o), col = (int) fCol.get(o);
            out.append(id).append(',').append(objName.getOrDefault(o, "?")).append(',').append(col).append('\n');
            count++; maxId = Math.max(maxId, id);
        }
        Files.writeString(Paths.get("map-colors-1.21.11.csv"), out.toString());
        System.err.println("MapColor 总数=" + count + "  最大 id=" + maxId + "  数组长度=" + arr.length);
    }
}
