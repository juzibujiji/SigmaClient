import com.viaversion.viaversion.api.data.MappingDataLoader;
import com.viaversion.nbt.tag.CompoundTag;
import java.util.*;
import java.nio.file.*;

public class Diff {
    static List<String> ids(MappingDataLoader L, String v, String key) throws Exception {
        CompoundTag t = L.loadNBT("identifiers-" + v + ".nbt");
        if (t == null) return null;
        try { return L.identifiersFromGlobalIds(t, key); } catch (Throwable e) { return null; }
    }
    public static void main(String[] a) throws Exception {
        MappingDataLoader.loadGlobalIdentifiers();
        MappingDataLoader L = MappingDataLoader.INSTANCE;
        String[] vers = {"1.16.2","1.17","1.18","1.19","1.19.3","1.19.4","1.20","1.20.2","1.20.3","1.20.5","1.21","1.21.2","1.21.4","1.21.5","1.21.6","1.21.7","1.21.9","1.21.11"};
        System.out.printf("%-9s %7s %7s%n","version","items","blocks");
        for (String v : vers) {
            List<String> it = ids(L,v,"items"), bl = ids(L,v,"blocks");
            System.out.printf("%-9s %7s %7s%n", v, it==null?"-":it.size(), bl==null?"-":bl.size());
        }
        // 差集
        for (String key : new String[]{"items","blocks"}) {
            List<String> base = ids(L,"1.16.2",key), tgt = ids(L,"1.21.11",key);
            if (base==null||tgt==null) continue;
            Set<String> b = new HashSet<>(base);
            List<String> added = new ArrayList<>();
            for (String s : tgt) if (!b.contains(s)) added.add(s);
            Set<String> t2 = new HashSet<>(tgt);
            List<String> removed = new ArrayList<>();
            for (String s : base) if (!t2.contains(s)) removed.add(s);
            System.out.println("\n### " + key + ": 1.16.4 has " + base.size() + ", 1.21.11 has " + tgt.size()
                + " | NEW=" + added.size() + " REMOVED=" + removed.size());
            Files.write(Paths.get("added-"+key+".txt"), added);
            Files.write(Paths.get("removed-"+key+".txt"), removed);
            if (!removed.isEmpty()) System.out.println("  removed: " + removed);
        }
    }
}
