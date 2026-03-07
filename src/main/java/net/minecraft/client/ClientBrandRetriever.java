package net.minecraft.client;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.module.impl.misc.ClientSpoof;

public class ClientBrandRetriever {
    // MODIFICATION BEGIN: spoofing
    public static String getClientModName() {
        if (Client.getInstance().moduleManager == null) {
            return "vanilla";
        }

        ClientSpoof clientSpoof = (ClientSpoof) Client.getInstance().moduleManager.getModuleByClass(ClientSpoof.class);
        return clientSpoof != null ? clientSpoof.getSpoofedBrand("vanilla") : "vanilla";
    }
    // MODIFICATION END
}