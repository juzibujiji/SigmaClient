package com.mentalfrostbyte.jello.util.game.render.shader;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.IResource;
import net.minecraft.resources.IResourceManager;
import net.minecraft.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class SigmaBlurShader implements IResourceManager {

    public @NotNull Set<String> getResourceNamespaces() {
        return Minecraft.getInstance().getResourceManager().getResourceNamespaces();
    }

    public @NotNull IResource getResource(ResourceLocation resourceLocationIn) throws IOException {
        return !resourceLocationIn.getPath().equals("jelloblur") ? Minecraft.getInstance().getResourceManager().getResource(resourceLocationIn) : new JelloBlurJSON();
    }

    public boolean hasResource(ResourceLocation path) {
        return path.getPath().equals("jelloblur") || Minecraft.getInstance().getResourceManager().hasResource(path);
    }

    public @NotNull List<IResource> getAllResources(@NotNull ResourceLocation resourceLocationIn) throws IOException {
        return Minecraft.getInstance().getResourceManager().getAllResources(resourceLocationIn);
    }

    public @NotNull Collection<ResourceLocation> getAllResourceLocations(@NotNull String pathIn, @NotNull Predicate<String> filter) {
        return Minecraft.getInstance().getResourceManager().getAllResourceLocations(pathIn, filter);
    }

    @Override
    public Stream<IResourcePack> getResourcePackStream() {
        return null;
    }
}
