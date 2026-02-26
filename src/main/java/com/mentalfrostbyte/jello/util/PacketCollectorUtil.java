package com.mentalfrostbyte.jello.util;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import net.minecraft.network.IPacket;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PacketCollectorUtil {

    @SuppressWarnings("unchecked")
    public static String[] getPacketClasses(String type) {
        String[] packages = {
                "net.minecraft.network.play.client",
                "net.minecraft.network.play.server"
        };

        Set<Class<? extends IPacket>> allPackets = new HashSet<>(Set.of());
//        for (String packageName : packages) {
//            Reflections reflections = new Reflections(packageName);
//            allPackets.addAll(reflections.getSubTypesOf(IPacket.class));
//        }
        try (ScanResult scanResult = new ClassGraph().acceptPackages("net.minecraft.network.play", "net.minecraft.network.play.server")
                .scan()) {

            allPackets.addAll(scanResult.getSubclasses(IPacket.class).loadClasses().stream().map(clazz -> (Class<? extends IPacket>) clazz).collect(Collectors.toSet()));
        }

        Stream<Class<? extends IPacket>> filteredPackets = getFilteredPackets(type, allPackets);

        return filteredPackets
                .map(Class::getSimpleName)
                .sorted()
                .toArray(String[]::new);
    }

    private static Stream<Class<? extends IPacket>> getFilteredPackets(String type, Set<Class<? extends IPacket>> allPackets) {
        Stream<Class<? extends IPacket>> filteredPackets = allPackets.stream();
        switch (type) {
            case "Incoming" -> filteredPackets = filteredPackets.filter(packet -> packet.getSimpleName().matches("^S[A-Za-z]+Packet$"));
            case "Outgoing" -> filteredPackets = filteredPackets.filter(packet -> packet.getSimpleName().matches("^C[A-Za-z]+Packet$"));
        }
        return filteredPackets;
    }
}