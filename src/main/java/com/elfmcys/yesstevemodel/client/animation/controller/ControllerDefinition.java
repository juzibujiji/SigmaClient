package com.elfmcys.yesstevemodel.client.animation.controller;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ControllerDefinition {
    private final String name;
    private final String initialState;
    private final Map<String, ControllerStateDefinition> states;
    private final ControllerLayer layer;

    public ControllerDefinition(String name, String initialState, Map<String, ControllerStateDefinition> states) {
        this.name = name == null ? "" : name.trim();
        this.states = Collections.unmodifiableMap(new LinkedHashMap<>(states == null ? Collections.emptyMap() : states));
        String requestedInitial = initialState == null ? "" : initialState.trim();
        if (!requestedInitial.isEmpty() && this.states.containsKey(requestedInitial)) {
            this.initialState = requestedInitial;
        } else if (this.states.containsKey("default")) {
            this.initialState = "default";
        } else if (!this.states.isEmpty()) {
            this.initialState = this.states.keySet().iterator().next();
        } else {
            this.initialState = "";
        }
        this.layer = ControllerLayer.fromControllerName(this.name);
    }

    public String getName() {
        return this.name;
    }

    public String getInitialState() {
        return this.initialState;
    }

    public Map<String, ControllerStateDefinition> getStates() {
        return this.states;
    }

    public ControllerStateDefinition getState(String name) {
        return this.states.get(name);
    }

    public ControllerLayer getLayer() {
        return this.layer;
    }

    public boolean isUsable() {
        return !this.name.isEmpty() && !this.initialState.isEmpty() && !this.states.isEmpty();
    }
}
