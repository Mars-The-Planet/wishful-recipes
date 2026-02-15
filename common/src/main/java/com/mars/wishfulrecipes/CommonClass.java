package com.mars.wishfulrecipes;

import com.google.common.collect.Lists;
import com.mars.deimos.config.DeimosConfig;
import com.mars.deimos.datagen.DeimosRecipeGenerator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mars.wishfulrecipes.WishfulRecipesConfig.*;

public class CommonClass {
    public static Map<String, List<String>> itemsInTags = new HashMap<>();

    public static void init() {
        DeimosConfig.init(Constants.MOD_ID, WishfulRecipesConfig.class);

        if (blast_to_glass_enable) {
            // #minecraft:smelts_to_glass - works down to 1.19.4
            DeimosRecipeGenerator.createBlastingJson("#minecraft:smelts_to_glass", "glass", 100, 0.1F);
        }

        if (extra_blasting_enable) {
            for(String blasting_recipe : extra_blasting_list){
                String[] rawMetalSet =  (blasting_recipe.replaceAll("\\s","")).split(",");
                DeimosRecipeGenerator.createBlastingJson(rawMetalSet[0], rawMetalSet[1], 100, Float.parseFloat(rawMetalSet[2]));
            }
        }

        // 26.1 becomes official
        if (backport_name_tag_enable)
            DeimosRecipeGenerator.createShapelessRecipeJson(Lists.newArrayList("paper", "#c:nuggets"), "name_tag", 1);

        // 1.21.6 becomes official
        if (backport_saddle_enable)
            DeimosRecipeGenerator.createShapedRecipeJson(Lists.newArrayList("leather", "iron_ingot"), Lists.newArrayList(" A ", "ABA"), "saddle", 1);

        // 1.21.2 becomes official
        if (backport_bundle_enable)
            DeimosRecipeGenerator.createShapedRecipeJson(Lists.newArrayList("string", "leather"), Lists.newArrayList("A", "B"), "bundle", 1);
    }
}
