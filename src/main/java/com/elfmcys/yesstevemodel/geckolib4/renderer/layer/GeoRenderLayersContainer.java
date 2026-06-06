package com.elfmcys.yesstevemodel.geckolib4.renderer.layer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * GeckoLib 4 style render layer container.
 */
public final class GeoRenderLayersContainer {
    private final List<GeoRenderLayer> layers = new ArrayList<>();

    public List<GeoRenderLayer> getRenderLayers() {
        return Collections.unmodifiableList(this.layers);
    }

    public void addLayer(GeoRenderLayer layer) {
        this.layers.add(layer);
    }
}
