import com.viaversion.viaversion.api.data.*;
import com.viaversion.nbt.tag.CompoundTag;
import java.util.*;
public class Chain {
    public static void main(String[] a) throws Exception {
        MappingDataLoader.loadGlobalIdentifiers();
        MappingDataLoader V = MappingDataLoader.INSTANCE;
        // ViaBackwards 的数据目录
        MappingDataLoader B = new MappingDataLoader(
            Class.forName("com.viaversion.viabackwards.api.ViaBackwardsPlatform"), "assets/viabackwards/data/");
        String[][] steps = {
            {"1.21.11","1.21.9"},{"1.21.9","1.21.7"},{"1.21.7","1.21.6"},{"1.21.6","1.21.5"},
            {"1.21.5","1.21.4"},{"1.21.4","1.21.2"},{"1.21.2","1.21"},{"1.21","1.20.5"},
            {"1.20.5","1.20.3"},{"1.20.3","1.20.2"},{"1.20.2","1.20"},{"1.20","1.19.4"},
            {"1.19.4","1.19.3"},{"1.19.3","1.19"},{"1.19","1.18"},
            {"1.18","1.17"},{"1.17","1.16.2"}};
        List<Mappings> chain = new ArrayList<>(); List<String> names = new ArrayList<>();
        for (String[] s : steps) {
            CompoundTag t = B.loadNBT("mappings-"+s[0]+"to"+s[1]+".nbt");
            if (t == null) continue;
            Mappings m = B.loadMappings(t, "items");
            if (m != null) { chain.add(m); names.add(s[0]+"->"+s[1]); }
        }
        List<String> tgt = V.identifiersFromGlobalIds(V.loadNBT("identifiers-1.21.11.nbt"), "items");
        List<String> base = V.identifiersFromGlobalIds(V.loadNBT("identifiers-1.16.2.nbt"), "items");
        System.out.println("链长度=" + chain.size() + " " + names + "\n");
        for (String probe : new String[]{"mace","netherite_spear","copper_pickaxe","copper_sword",
                "deepslate","cherry_planks","breeze_rod","trial_key","copper_bulb","amethyst_shard"}) {
            int id = tgt.indexOf(probe);
            if (id < 0) { System.out.printf("%-18s 不在 1.21.11%n", probe); continue; }
            int cur = id; String died = null;
            for (int i=0;i<chain.size();i++){
                int nxt = chain.get(i).getNewId(cur);
                if (nxt < 0) { died = names.get(i); break; }
                cur = nxt;
            }
            String res = died != null ? "【丢弃于 "+died+"】"
                : (cur < base.size() ? base.get(cur) : "越界 id="+cur);
            System.out.printf("%-18s (1.21.11 id=%4d) -> %s%n", probe, id, res);
        }
    }
}
