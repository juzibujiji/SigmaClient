import com.viaversion.viaversion.api.data.MappingDataLoader;
import com.viaversion.nbt.tag.CompoundTag;
import java.util.*;
import java.nio.file.*;
public class Ent {
    public static void main(String[] a) throws Exception {
        MappingDataLoader.loadGlobalIdentifiers();
        MappingDataLoader L = MappingDataLoader.INSTANCE;
        List<String> b = L.identifiersFromGlobalIds(L.loadNBT("identifiers-1.16.2.nbt"),"entities");
        List<String> t = L.identifiersFromGlobalIds(L.loadNBT("identifiers-1.21.11.nbt"),"entities");
        Set<String> bs = new HashSet<>(b);
        List<String> added = new ArrayList<>();
        for (String s : t) if (!bs.contains(s)) added.add(s);
        System.out.println("1.16.4 生物/实体="+b.size()+"  1.21.11="+t.size()+"  新增="+added.size());
        System.out.println(added);
        Files.write(Paths.get("added-entities.txt"), added);
    }
}
