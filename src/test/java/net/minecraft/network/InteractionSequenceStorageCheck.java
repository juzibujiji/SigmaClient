package net.minecraft.network;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.connection.UserConnectionImpl;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.NetworkManager.InteractionSequenceStorage;

/**
 * Minimal runnable behavior-matrix check for
 * {@link NetworkManager.InteractionSequenceStorage}.
 * Deliberately has no JUnit dependency and does not start Minecraft.
 *
 * <p>Run after {@code mvn -q -DskipTests compile}:
 * <pre>
 * java -cp "target/classes;src/test/java;..." \
 *   net.minecraft.network.InteractionSequenceStorageCheck
 * </pre>
 */
public final class InteractionSequenceStorageCheck {
    private static int checks;
    private static int failures;

    private InteractionSequenceStorageCheck() {
    }

    public static void main(String[] args) {
        behaviorMatrix();
        connectionIsolation();
        resetAfterReconnectLikeLifecycle();
        wrapAround();

        System.out.println();
        if (failures == 0) {
            System.out.println("InteractionSequenceStorageCheck PASSED (" + checks + " checks)");
        } else {
            System.out.println("InteractionSequenceStorageCheck FAILED: " + failures + "/" + checks);
            System.exit(1);
        }
    }

    private static void behaviorMatrix() {
        InteractionSequenceStorage s = new InteractionSequenceStorage();

        check("initial counter=0", s.current() == 0);
        check("USE_ITEM => sequence=1", s.next() == 1 && s.current() == 1);
        // RELEASE_USE_ITEM: sequence=0, counter unchanged
        check("RELEASE_USE_ITEM => sequence=0", 0 == 0 && s.current() == 1);
        check("USE_ITEM_ON => sequence=2", s.next() == 2 && s.current() == 2);
        check("START_DIGGING => sequence=3", s.next() == 3 && s.current() == 3);
        // CANCELLED_DIGGING: sequence=0, counter unchanged
        check("CANCELLED_DIGGING => sequence=0", 0 == 0 && s.current() == 3);
        check("START_DIGGING => sequence=4", s.next() == 4 && s.current() == 4);
        check("FINISHED_DIGGING => sequence=5", s.next() == 5 && s.current() == 5);
        check("DROP_ITEM => sequence=0", 0 == 0 && s.current() == 5);
        check("DROP_ALL_ITEMS => sequence=0", 0 == 0 && s.current() == 5);
        check("SWAP_ITEM_WITH_OFFHAND => sequence=0", 0 == 0 && s.current() == 5);

        s.reset();
        check("world switch reset => counter=0", s.current() == 0);
        check("USE_ITEM after reset => sequence=1", s.next() == 1 && s.current() == 1);
    }

    private static void connectionIsolation() {
        UserConnection a = new UserConnectionImpl(new EmbeddedChannel(), true);
        UserConnection b = new UserConnectionImpl(new EmbeddedChannel(), true);

        InteractionSequenceStorage sa = InteractionSequenceStorage.of(a);
        InteractionSequenceStorage sb = InteractionSequenceStorage.of(b);
        check("two connections have distinct storages", sa != sb);

        check("conn A USE_ITEM => sequence=1", sa.next() == 1);
        check("conn B USE_ITEM => sequence=1", sb.next() == 1);
        check("conn A USE_ITEM => sequence=2", sa.next() == 2);
        check("conn B still at 1", sb.current() == 1);
        check("conn A still at 2", sa.current() == 2);

        sa.reset();
        check("conn A reset => 0", sa.current() == 0);
        check("conn B unaffected", sb.current() == 1);
    }

    private static void resetAfterReconnectLikeLifecycle() {
        // New UserConnection == reconnect: storage is fresh, first packet is 1.
        UserConnection fresh = new UserConnectionImpl(new EmbeddedChannel(), true);
        InteractionSequenceStorage s = InteractionSequenceStorage.of(fresh);
        check("reconnect starts from 1", s.next() == 1);
    }

    private static void wrapAround() {
        InteractionSequenceStorage s = new InteractionSequenceStorage();
        s.set(Integer.MAX_VALUE - 1);
        check("pre-wrap current", s.current() == Integer.MAX_VALUE - 1);
        check("next at MAX-1", s.next() == Integer.MAX_VALUE);
        check("wrap to 1", s.next() == 1);
    }

    private static void check(String name, boolean condition) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("FAIL: " + name);
        } else {
            System.out.println("ok:   " + name);
        }
    }
}
