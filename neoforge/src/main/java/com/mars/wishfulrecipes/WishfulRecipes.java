package com.mars.wishfulrecipes;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class WishfulRecipes {
    public WishfulRecipes(IEventBus eventBus) {
        CommonClass.init();
    }
}
