import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
public class Split {
    // 1.16.4 已有对应类、可直接生成的 1.21 方块类型
    static final Set<String> MAPPABLE = new HashSet<>(Arrays.asList(
        "block","rotated_pillar","slab","stair","wall","fence","fence_gate","door","trapdoor",
        "button","pressure_plate","drop_experience","iron_bars","chain","lantern","leaves",
        "untinted_particle_leaves","flower_pot","standing_sign","wall_sign","carpet","wool_carpet",
        "stained_glass","stained_glass_pane","concrete_powder","glazed_terracotta","shulker_box",
        "flower","tall_flower","sapling","slime","half_transparent","transparent","waterlogged_transparent",
        "ladder","torch","wall_torch","end_rod","cake","bed","banner","wall_banner","sand","gravel",
        "farm","dirt_path","snow_layer","ice","stone_pressure_plate","web","vine","bush","crop"));
    public static void main(String[] a) throws Exception {
        JsonObject r = JsonParser.parseString(Files.readString(Paths.get(
          "C:/Users/juzibujiji/AppData/Local/Temp/mcreports/generated/reports/blocks.json"))).getAsJsonObject();
        Set<String> added = new LinkedHashSet<>(Files.readAllLines(Paths.get(
          "F:/HCMLNew/SigmaClient/docs/registry-diff/added-blocks-1.16.4-to-1.21.11.txt")));
        List<String> can = new ArrayList<>(), cant = new ArrayList<>();
        Map<String,Integer> cantTypes = new TreeMap<>();
        for (String id : added) {
            JsonElement e = r.get("minecraft:"+id);
            if (e == null) { cant.add(id); continue; }
            String t = e.getAsJsonObject().getAsJsonObject("definition").get("type").getAsString().replace("minecraft:","");
            if (MAPPABLE.contains(t)) can.add(id);
            else { cant.add(id); cantTypes.merge(t, 1, Integer::sum); }
        }
        System.out.println("可直接生成的方块 = " + can.size());
        System.out.println("需要新写类的方块 = " + cant.size());
        System.out.println("\n需要新类的类型分布：");
        cantTypes.entrySet().stream().sorted((x,y)->y.getValue()-x.getValue())
            .forEach(en -> System.out.printf("  %-32s %d%n", en.getKey(), en.getValue()));
        Files.write(Paths.get("gen-batch1-blocks.txt"), can);
        Files.write(Paths.get("gen-needs-newclass-blocks.txt"), cant);
    }
}
