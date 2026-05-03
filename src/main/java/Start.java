import java.util.Arrays;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.main.Main;

public class Start {
    public static void main(String[] args) {

        String assets = System.getenv().containsKey("assetDirectory") ? System.getenv("assetDirectory") : "assets";
        List<String> defaultArgs = new ArrayList<>();
        defaultArgs.add("--version");
        defaultArgs.add("vanilla");
        defaultArgs.add("--accessToken");
        defaultArgs.add("0");
        defaultArgs.add("--assetsDir");
        defaultArgs.add(assets);

        if (new File(new File(assets, "indexes"), "1.16.json").isFile()) {
            defaultArgs.add("--assetIndex");
            defaultArgs.add("1.16");
        }

        defaultArgs.add("--userProperties");
        defaultArgs.add("{}");
        Main.main(concat(defaultArgs.toArray(new String[0]), args));
    }

    public static <T> T[] concat(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
