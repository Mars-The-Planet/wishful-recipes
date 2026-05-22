package com.mars.wishfulrecipes.platform;

import com.mars.wishfulrecipes.platform.services.IPlatformHelper;
import net.minecraftforge.fml.ModList;

public class ForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Forge";
    }
}
