package com.mars.wishfulrecipes;

import com.google.common.collect.Lists;
import com.mars.deimos.config.DeimosConfig;

import java.util.List;

public class WishfulRecipesConfig extends DeimosConfig {
    @Entry public static boolean stairs_to_blocks_enable = true;
    @Entry public static int stairs_to_blocks_amount = 1;

    @Entry public static boolean better_stairs_crafting_enable = true;
    @Entry public static int better_stairs_crafting_amount = 3;

    @Entry public static boolean slabs_to_blocks_enable = true;
    @Entry public static int slabs_to_blocks_amount = 1;

    @Entry public static boolean walls_to_blocks_enable = true;
    @Entry public static int walls_to_blocks_amount = 1;

    @Entry public static boolean blast_to_glass_enable = true;

    @Entry public static boolean blasting_raw_metal_blocks_enable = true;
    @Entry public static int blasting_raw_metal_blocks_cookingtime = 100;
    @Entry public static boolean smelting_raw_metal_blocks_enable = true;

    @Entry public static boolean blasting_stone_enable = true;

    @Entry public static boolean extra_blasting_enable = true;
    @Entry public static List<String> extra_blasting_list = Lists.newArrayList(
            "minecraft:netherrack, minecraft:nether_brick, 0.1",
            "minecraft:clay_ball, minecraft:brick, 0.3",
            "minecraft:clay, minecraft:terracotta, 0.35",
            "minecraft:white_terracotta, minecraft:white_glazed_terracotta, 0.1",
            "minecraft:orange_terracotta, minecraft:orange_glazed_terracotta, 0.1",
            "minecraft:magenta_terracotta, minecraft:magenta_glazed_terracotta, 0.1",
            "minecraft:light_blue_terracotta, minecraft:light_blue_glazed_terracotta, 0.1",
            "minecraft:yellow_terracotta, minecraft:yellow_glazed_terracotta, 0.1",
            "minecraft:lime_terracotta, minecraft:lime_glazed_terracotta, 0.1",
            "minecraft:pink_terracotta, minecraft:pink_glazed_terracotta, 0.1",
            "minecraft:gray_terracotta, minecraft:gray_glazed_terracotta, 0.1",
            "minecraft:light_gray_terracotta, minecraft:light_gray_glazed_terracotta, 0.1",
            "minecraft:cyan_terracotta, minecraft:cyan_glazed_terracotta, 0.1",
            "minecraft:purple_terracotta, minecraft:purple_glazed_terracotta, 0.1",
            "minecraft:blue_terracotta, minecraft:blue_glazed_terracotta, 0.1",
            "minecraft:brown_terracotta, minecraft:brown_glazed_terracotta, 0.1",
            "minecraft:green_terracotta, minecraft:green_glazed_terracotta, 0.1",
            "minecraft:red_terracotta, minecraft:red_glazed_terracotta, 0.1",
            "minecraft:black_terracotta, minecraft:black_glazed_terracotta, 0.1"
    );

    @Entry public static boolean extra_smelting_enable = true;
    @Entry public static List<String> extra_smelting_list = Lists.newArrayList(
    );

    @Entry public static boolean extra_smoking_enable = true;
    @Entry public static List<String> extra_smoking_list = Lists.newArrayList(
    );

    @Entry public static boolean backport_name_tag_enable = true;
    @Entry public static boolean backport_copper_trapdoor_enable = true;
    @Entry public static boolean backport_saddle_enable = true;
    @Entry public static boolean backport_lead_enable = true;
    @Entry public static boolean backport_lodestone_enable = true;
    @Entry public static boolean backport_bundle_enable = true;

    @Entry public static boolean unpacking_enable = true;
    @Entry public static List<String> unpacking_list = Lists.newArrayList(
            "snow_block, snowball, 4",
            "packed_ice, ice, 9",
            "blue_ice, packed_ice, 9",
            "white_wool, string, 4",
            "bamboo_block, bamboo, 9",
            "nether_wart_block, nether_wart, 9",
            "clay, clay_ball, 4"
    );

    @Entry public static boolean use_stone_crafting_materials_enable = true;
    @Entry public static List<String> use_stone_crafting_materials_list = Lists.newArrayList(
            "minecraft:dispenser",
            "minecraft:dropper",
            "minecraft:lever",
            "minecraft:observer",
            "minecraft:piston"
    );

    @Entry public static boolean unlock_all_recipes = true;
}
