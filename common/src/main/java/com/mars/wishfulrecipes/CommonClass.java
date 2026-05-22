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
            for (String input : blast_to_glass_list)
                DeimosRecipeGenerator.createBlastingJson(input, "glass", 100, 0.1F);
        }

        if (extra_blasting_enable) {
            for(String blastingRecipe : extra_blasting_list){
                String[] rawMetalSet =  (blastingRecipe.replaceAll("\\s","")).split(",");
                DeimosRecipeGenerator.createBlastingJson(rawMetalSet[0], rawMetalSet[1], 100, Float.parseFloat(rawMetalSet[2]));
            }
        }

        if (extra_smoking_enable) {
            for(String blastingRecipe : extra_smoking_list){
                String[] rawMetalSet =  (blastingRecipe.replaceAll("\\s","")).split(",");
                DeimosRecipeGenerator.createSmokingJson(rawMetalSet[0], rawMetalSet[1], 100, Float.parseFloat(rawMetalSet[2]));
            }
        }

        if (extra_smelting_enable) {
            for(String blastingRecipe : extra_smelting_list){
                String[] rawMetalSet =  (blastingRecipe.replaceAll("\\s","")).split(",");
                DeimosRecipeGenerator.createSmeltingJson(rawMetalSet[0], rawMetalSet[1], 200, Float.parseFloat(rawMetalSet[2]));
            }
        }

        if (unpacking_enable) {
            for (String unpackingRecipe : unpacking_list) {
                String[] unpackingSet =  (unpackingRecipe.replaceAll("\\s","")).split(",");
                DeimosRecipeGenerator.createItemConvertorJson(unpackingSet[0], unpackingSet[1], Integer.parseInt(unpackingSet[2]));
            }
        }

        // 26.1 becomes official
        if (backport_name_tag_enable)
            DeimosRecipeGenerator.createShapelessRecipeJson(Lists.newArrayList("paper", "#c:nuggets"), "name_tag", 1);

        // copper trapdoor were added in 1.20.3
        // 1.21.9 becomes official
        // added in 1.20.3, officially in 1.21
        // if (backport_copper_trapdoor_enable)
        //    DeimosRecipeGenerator.createShapedRecipeJson(Lists.newArrayList("copper_ingot"), Lists.newArrayList("AA", "AA"), "copper_trapdoor", 2);

        // 1.21.6 becomes official
        if (backport_saddle_enable)
            DeimosRecipeGenerator.createShapedRecipeJson(Lists.newArrayList("leather", "iron_ingot"), Lists.newArrayList(" A ", "ABA"), "saddle", 1);
        if (backport_lead_enable)
            DeimosRecipeGenerator.createShapedRecipeJson(Lists.newArrayList("string"), Lists.newArrayList("AA ", "AA ", "  A"), "lead", 2);

        // 1.21.5 becomes official
        if (backport_lodestone_enable)
            DeimosRecipeGenerator.createShapedRecipeJson(Lists.newArrayList("chiseled_stone_bricks", "iron_ingot"), Lists.newArrayList("AAA", "ABA", "AAA"), "lodestone", 1);

        // 1.21.2 becomes official
        // added in 1.17, made accessible using datapack in 1.19.3, officially in 1.21.2
        if (backport_bundle_enable)
            DeimosRecipeGenerator.createShapedRecipeJson(Lists.newArrayList("string", "leather"), Lists.newArrayList("A", "B"), "bundle", 1);
    }
}
