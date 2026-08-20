import com.google.gson.*;
import com.viaversion.viaversion.api.data.MappingDataLoader;
import java.nio.file.*;
import java.util.*;

public class Verify {
    static List<String> official(JsonObject root, String reg) {
        JsonObject entries = root.getAsJsonObject(reg).getAsJsonObject("entries");
        String[] arr = new String[entries.size()];
        for (Map.Entry<String,JsonElement> e : entries.entrySet()) {
            int pid = e.getValue().getAsJsonObject().get("protocol_id").getAsInt();
            arr[pid] = e.getKey().replace("minecraft:", "");
        }
        return Arrays.asList(arr);
    }
    public static void main(String[] a) throws Exception {
        JsonObject root = JsonParser.parseString(
            Files.readString(Paths.get("C:/Users/juzibujiji/AppData/Local/Temp/mcreports/generated/reports/registries.json"))).getAsJsonObject();
        MappingDataLoader.loadGlobalIdentifiers();
        MappingDataLoader L = MappingDataLoader.INSTANCE;

        for (String[] p : new String[][]{{"minecraft:item","items"},{"minecraft:block","blocks"}}) {
            List<String> off = official(root, p[0]);
            List<String> via = L.identifiersFromGlobalIds(L.loadNBT("identifiers-1.21.11.nbt"), p[1]);
            System.out.println("=== " + p[0] + "  官方=" + off.size() + "  Via导出=" + via.size());
            // 顺序与内容是否完全一致
            int mismatch = 0;
            int n = Math.min(off.size(), via.size());
            for (int i = 0; i < n; i++)
                if (!off.get(i).equals(via.get(i))) { if (mismatch<5) System.out.println("  ID "+i+" 官方="+off.get(i)+" Via="+via.get(i)); mismatch++; }
            System.out.println("  逐 ID 顺序不一致数 = " + mismatch);
            Set<String> so = new HashSet<>(off), sv = new HashSet<>(via);
            List<String> onlyOff = new ArrayList<>(so); onlyOff.removeAll(sv);
            List<String> onlyVia = new ArrayList<>(sv); onlyVia.removeAll(so);
            System.out.println("  仅官方有 = " + onlyOff.size() + (onlyOff.isEmpty()?"":" "+onlyOff));
            System.out.println("  仅Via有  = " + onlyVia.size() + (onlyVia.isEmpty()?"":" "+onlyVia));
            Files.write(Paths.get("official-"+p[1]+"-1.21.11.txt"), off);
        }
    }
}
