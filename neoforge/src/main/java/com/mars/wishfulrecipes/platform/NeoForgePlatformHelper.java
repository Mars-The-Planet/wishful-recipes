package com.mars.wishfulrecipes.platform;

import com.mars.wishfulrecipes.platform.services.IPlatformHelper;
import net.neoforged.fml.ModList;

public class NeoForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "NeoForge";
    }
}
