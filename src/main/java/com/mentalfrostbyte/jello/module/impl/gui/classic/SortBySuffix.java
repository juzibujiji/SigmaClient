package com.mentalfrostbyte.jello.module.impl.gui.classic;

import java.util.Comparator;
import com.mentalfrostbyte.jello.module.Module;

public record SortBySuffix(ActiveMods activeMods) implements Comparator<Module> {

	public int compare(Module a, Module b) {
		if (ActiveMods.method16860(this.activeMods, a) <= ActiveMods.method16860(this.activeMods, b)) {
			return ActiveMods.method16860(this.activeMods, a) >= ActiveMods.method16860(this.activeMods, b)
					? a.getName().compareTo(b.getName())
					: 1;
		} else {
			return -1;
		}
	}
}