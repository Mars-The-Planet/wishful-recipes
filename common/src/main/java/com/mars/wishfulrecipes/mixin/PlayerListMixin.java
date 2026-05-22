package com.mars.wishfulrecipes.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

import static com.mars.wishfulrecipes.WishfulRecipesConfig.unlock_all_recipes;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    // unlock all recipes
    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void placeNewPlayer(Connection connection, ServerPlayer player, CallbackInfo ci) {
        if (!unlock_all_recipes) return;
        Collection<Recipe<?>> allRecipes = player.server.getRecipeManager().getRecipes();
        player.awardRecipes(allRecipes);
    }
}
