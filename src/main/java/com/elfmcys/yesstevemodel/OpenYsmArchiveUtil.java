package com.elfmcys.yesstevemodel;

import java.io.IOException;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class OpenYsmArchiveUtil {
    private OpenYsmArchiveUtil() {
    }

    public static String normalizeArchivePath(String path) throws IOException {
        if (path == null) {
            throw new IOException("Archive path is missing");
        }

        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        if (normalized.isEmpty() || normalized.indexOf('\0') >= 0) {
            throw new IOException("Invalid archive path: " + path);
        }

        String[] parts = normalized.split("/");
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                throw new IOException("Unsafe archive path: " + path);
            }
        }

        return normalized;
    }

    public static ZipEntry findYsmJson(ZipFile zipFile) {
        ZipEntry root = zipFile.getEntry("ysm.json");
        if (root != null && !root.isDirectory()) {
            return root;
        }

        return zipFile.stream()
                .filter(entry -> !entry.isDirectory())
                .filter(entry -> {
                    try {
                        return normalizeArchivePath(entry.getName()).endsWith("/ysm.json");
                    } catch (IOException exception) {
                        return false;
                    }
                })
                .min(Comparator.comparingInt(entry -> entry.getName().length()))
                .orElse(null);
    }

    public static String rootPrefix(ZipEntry ysmJsonEntry) throws IOException {
        String name = normalizeArchivePath(ysmJsonEntry.getName());
        return name.endsWith("ysm.json") ? name.substring(0, name.length() - "ysm.json".length()) : "";
    }

    public static ZipEntry findEntry(ZipFile zipFile, String normalizedName) {
        ZipEntry exact = zipFile.getEntry(normalizedName);
        if (exact != null && !exact.isDirectory()) {
            return exact;
        }

        return zipFile.stream()
                .filter(entry -> !entry.isDirectory())
                .filter(entry -> {
                    try {
                        return normalizeArchivePath(entry.getName()).equals(normalizedName);
                    } catch (IOException exception) {
                        return false;
                    }
                })
                .findFirst()
                .orElse(null);
    }
}
