package com.mars.wishfulrecipes.mixin;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mars.deimos.datagen.DeimosRecipeGenerator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.Reader;
import java.util.*;

import static com.mars.wishfulrecipes.CommonClass.alreadyGeneratedRecipes;
import static com.mars.wishfulrecipes.CommonClass.itemsInTags;
import static com.mars.wishfulrecipes.WishfulRecipesConfig.*;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @Inject(method = "prepare", at = @At(value = "TAIL"), cancellable = true)
    private void interceptPrepare(ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfoReturnable<RecipeMap> cir) {
        if (alreadyGeneratedRecipes) return;

        List<JsonElement> loadedRecipes = new ArrayList<>();

        // Intercept and parse raw JSON files before the game evaluates tags
        for (Map.Entry<ResourceLocation, Resource> entry : resourceManager.listResources("recipe", id -> id.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = entry.getValue().openAsReader()) {
                loadedRecipes.add(JsonParser.parseReader(reader));
            } catch (Exception ignored) {}
        }

        // Key - Raw Metal Item
        // 0 - Raw Metal Item
        // 1 - Metal Item
        // 2 - Raw Metal Block
        // 3 - Metal Block
        // 4 - exp
        // "" - missing
        Map<String, String[]> isRawMetal = new HashMap<>();

        // is it ever part of a stonecutting recipe
        Set<String> isStone = new HashSet<>();

        // first recipe loop
        for (JsonElement recipeElement : loadedRecipes) {
            try
            {
                JsonObject recipe = recipeElement.getAsJsonObject();
                String result = getResult(recipe);
                if (result == null) continue;

                // looking for items in stonecutting recipes
                if (getIngredients(recipe).length > 0 && blasting_stone_enable && Objects.equals(getType(recipe), "minecraft:stonecutting")) {
                    isStone.addAll(Arrays.asList(getIngredients(recipe)));
                }

                // checking whether item has a blasting recipe
                if (blasting_raw_metal_blocks_enable && Objects.equals(getType(recipe), "minecraft:blasting")) {
                    String[] ingredients = getIngredients(recipe);
                    for (String ingredient : ingredients) {
                        if (!isRawMetal.containsKey(ingredient)) {
                            isRawMetal.put(ingredient, new String[]{ingredient, result, "", "", String.valueOf(getExp(recipe))});
                        }
                    }
                }

                // slabs to blocks
                if (hasSlabPattern(recipe) && slabs_to_blocks_enable) {
                    String[] keys = getKeys(recipe);

                    for (String key : keys) {
                        if (key == null) continue;
                        DeimosRecipeGenerator.createShapelessRecipeJson(Lists.newArrayList(result, result), key, slabs_to_blocks_amount);
                    }
                }

                if (hasStairsPattern(recipe)) {
                    String[] keys = getKeys(recipe);
                    for (String key : keys) {
                        if (key == null) continue;

                        if (better_stairs_crafting_enable) {
                            DeimosRecipeGenerator.createShapedRecipeJson(
                                    Lists.newArrayList(key),
                                    Lists.newArrayList("# ", "##"),
                                    result,
                                    better_stairs_crafting_amount
                            );
                        }

                        if (stairs_to_blocks_enable)
                            DeimosRecipeGenerator.createItemConvertorJson(result, key, stairs_to_blocks_amount);
                    }
                }

                // walls to blocks
                if (hasWallPattern(recipe) && walls_to_blocks_enable) {
                    String[] keys = getKeys(recipe);

                    for (String key : keys) {
                        if (key == null) continue;
                        DeimosRecipeGenerator.createItemConvertorJson(result, key, walls_to_blocks_amount);
                    }
                }

                // use use_stone_crafting_materials tag instead of cobblestone
                if (use_stone_crafting_materials_enable) {
                    if (use_stone_crafting_materials_list.contains(result)) {
                        JsonObject recipeCopy = recipe.deepCopy();

                        if (recipeCopy.has("key") && recipeCopy.get("key").isJsonObject()) {
                            JsonObject keyObject = recipeCopy.getAsJsonObject("key");

                            // Iterate through every character mapping inside the "key" object
                            for (Map.Entry<String, JsonElement> entry : keyObject.entrySet()) {
                                JsonElement ingredientElement = entry.getValue();

                                // Ensure the ingredient mapping is a standard JsonObject
                                if (ingredientElement.isJsonObject()) {
                                    JsonObject ingredientObject = ingredientElement.getAsJsonObject();

                                    // Check if it explicitly declares "item" as "minecraft:cobblestone"
                                    if (ingredientObject.has("item") &&
                                            ingredientObject.get("item").isJsonPrimitive() &&
                                            ingredientObject.get("item").getAsString().equals("minecraft:cobblestone")) {

                                        // Replace the "item" definition with the "tag" definition
                                        ingredientObject.remove("item");
                                        ingredientObject.addProperty("tag", "minecraft:stone_crafting_materials");
                                    }
                                }
                            }
                        }

                        DeimosRecipeGenerator.RECIPES.add(recipeCopy);
                    }
                }
            }
            catch (Exception ignored) {}
        }

        // second recipe loop
        for (JsonElement recipeElement : loadedRecipes) {
            try
            {
                JsonObject recipe = recipeElement.getAsJsonObject();
                String result = getResult(recipe);
                if (result == null) continue;

                // to check for the rest of metal blasting properties
                if (blasting_raw_metal_blocks_enable && (hasFullBlockPattern(recipe) || isMekanismRawBlockPattern(recipe))) {
                    String[] keys = getKeys(recipe);

                    for (String key : keys) {
                        if (key == null) continue;

                        // for raw metal blocks
                        if (isRawMetal.containsKey(key)) {
                            String[] items = isRawMetal.get(key);
                            items[2] = result;
                            isRawMetal.put(key, items);
                        }

                        // for metal blocks
                        for (Map.Entry<String, String[]> entry : isRawMetal.entrySet()) {
                            String[] items = entry.getValue();
                            if (items[1].equals(key)) {
                                items[3] = getResult(recipe);
                                entry.setValue(items);
                            }
                        }
                    }
                }

                if (blasting_stone_enable && Objects.equals(getType(recipe), "minecraft:smelting")) {
                    String[] ingredients = getIngredients(recipe);

                    for (String ingredient : ingredients) {
                        if (isStone.contains(ingredient) || isStone.contains(getResult(recipe)))
                            DeimosRecipeGenerator.createBlastingJson(ingredient, result, 100, getExp(recipe));
                    }
                }
            }
            catch (Exception ignored) {}
        }

        // blasting raw metal blocks
        if (blasting_raw_metal_blocks_enable || smelting_raw_metal_blocks_enable) {
            for (String key : isRawMetal.keySet()) {
                String[] items = isRawMetal.get(key);
                if (Arrays.asList(items).contains("")) continue;
                if (blasting_raw_metal_blocks_enable)
                    DeimosRecipeGenerator.createBlastingJson(items[2], items[3], blasting_raw_metal_blocks_cookingtime, 9 * Float.parseFloat(items[4]));
                if (smelting_raw_metal_blocks_enable)
                    DeimosRecipeGenerator.createSmeltingJson(items[2], items[3], 2 * blasting_raw_metal_blocks_cookingtime, 9 * Float.parseFloat(items[4]));
            }
        }

        alreadyGeneratedRecipes = true;
    }

    @Unique
    private static String getType(JsonObject recipe) {
        if (!recipe.has("type")) return null;

        JsonElement typeElement = recipe.get("type");
        if (!typeElement.isJsonPrimitive() || !typeElement.getAsJsonPrimitive().isString()) return null;

        return typeElement.getAsString();
    }

    @Unique
    private static float getExp(JsonObject recipe) {
        if (!recipe.has("experience")) return 0;

        JsonElement expElement = recipe.get("experience");
        if (!expElement.isJsonPrimitive() || !expElement.getAsJsonPrimitive().isNumber()) return 0;

        return expElement.getAsFloat();
    }

    @Unique
    private static String getResult(JsonObject recipe) {
        if (!recipe.has("result")) return null;
        JsonElement resultElement = recipe.get("result");

        // 1.21.3 format: Result can be just a primitive string e.g. "minecraft:iron_block"
        if (resultElement.isJsonPrimitive() && resultElement.getAsJsonPrimitive().isString()) {
            return resultElement.getAsString();
        }

        // Fallback for object format e.g. {"id": "minecraft:iron_block", "count": 1}
        if (resultElement.isJsonObject()) {
            JsonObject resultObject = resultElement.getAsJsonObject();
            if (resultObject.has("id")) {
                return resultObject.get("id").getAsString();
            }
        }

        return null;
    }

    @Unique
    private static int getCount(JsonObject recipe) {
        if (!recipe.has("result")) return 0;
        JsonElement resultElement = recipe.get("result");

        // 1.21.3 format: If it's just a string, count implicitly defaults to 1
        if (resultElement.isJsonPrimitive() && resultElement.getAsJsonPrimitive().isString()) {
            return 1;
        }

        if (resultElement.isJsonObject()) {
            JsonObject resultObject = resultElement.getAsJsonObject();
            if (resultObject.has("count")) {
                return resultObject.get("count").getAsInt();
            }
            return 1; // Explicit object without a count also defaults to 1
        }

        return 0;
    }

    @Unique
    private static String[] getIngredients(JsonObject recipe) {
        List<String> ingredientsList = new ArrayList<>();

        if (recipe != null && recipe.isJsonObject()) {
            if (recipe.has("ingredient")) {
                JsonElement ingredientElement = recipe.get("ingredient");

                if (ingredientElement.isJsonArray()) {
                    for (JsonElement ingItem : ingredientElement.getAsJsonArray()) {
                        extractItemString(ingItem, ingredientsList);
                    }
                } else {
                    extractItemString(ingredientElement, ingredientsList);
                }
            }
        }

        return ingredientsList.toArray(new String[0]);
    }

    @Unique
    private static String[] getKeys(JsonObject recipe) {
        List<String> items = new ArrayList<>();

        if (recipe == null || !recipe.isJsonObject()) {
            return new String[0];
        }

        if (recipe.has("key")) {
            JsonObject keyObject = recipe.getAsJsonObject("key");

            for (Map.Entry<String, JsonElement> entry : keyObject.entrySet()) {
                JsonElement ingredient = entry.getValue();

                if (ingredient.isJsonArray()) {
                    for (JsonElement alt : ingredient.getAsJsonArray()) {
                        extractItemString(alt, items);
                    }
                } else {
                    extractItemString(ingredient, items);
                }
            }
        }

        return items.toArray(new String[0]);
    }

    @Unique
    private static void extractItemString(JsonElement element, List<String> list) {
        // 1.21.3 format: Can be a direct string "#minecraft:logs" or "minecraft:iron_ingot"
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String str = element.getAsString();
            if (str.startsWith("#")) {
                String tag = str.substring(1);
                if (itemsInTags.containsKey(tag)) {
                    list.addAll(itemsInTags.get(tag));
                }
            } else {
                list.add(str);
            }
        }
        // Legacy or explicit format {"item": "..."} or {"tag": "..."}
        else if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("item")) {
                list.add(obj.get("item").getAsString());
            } else if (obj.has("tag")) {
                String tag = obj.get("tag").getAsString();
                if (itemsInTags.containsKey(tag)) {
                    list.addAll(itemsInTags.get(tag));
                }
            }
        }
    }

    // if rightAmount set to 0 it doesnt check for it
    @Unique
    private static String[] basePatternCheck(JsonObject recipe, int rightAmount, int rightPatternArraySize) {
        if (!getType(recipe).equals("minecraft:crafting_shaped")) return null;

        if (rightAmount != 0 && getCount(recipe) != rightAmount) return null;

        if (!recipe.has("pattern")) return null;

        JsonElement patternElem = recipe.get("pattern");
        if (!patternElem.isJsonArray()) return null;

        JsonArray patternArray = patternElem.getAsJsonArray();
        if (patternArray.size() != rightPatternArraySize) return null;

        String[] rows = new String[rightPatternArraySize];
        for (int i = 0; i < rightPatternArraySize; i++) {
            JsonElement rowElem = patternArray.get(i);
            if (!rowElem.isJsonPrimitive() || !rowElem.getAsJsonPrimitive().isString()) return null;
            rows[i] = rowElem.getAsString();
            if (rows[i].length() != 3) return null;
        }

        return rows;
    }

    @Unique
    private static boolean hasWallPattern(JsonObject recipe) {
        String[] rows = basePatternCheck(recipe, 6, 2);
        if (rows == null) return false;

        char c = rows[0].charAt(0);
        if (c == ' ') return false;

        String expectedLine = "" + c + c + c;
        return rows[0].equals(expectedLine) && rows[1].equals(expectedLine);
    }

    @Unique
    private static boolean hasFullBlockPattern(JsonObject recipe) {
        String[] rows = basePatternCheck(recipe, 1, 3);
        if (rows == null) return false;

        char c = rows[0].charAt(0);
        if (c == ' ') return false;

        String expectedLine = "" + c + c + c;
        return rows[0].equals(expectedLine) && rows[1].equals(expectedLine) && rows[2].equals(expectedLine);
    }

    @Unique
    private static boolean isMekanismRawBlockPattern(JsonObject recipe) {
        if (!getResult(recipe).contains("block")) return false;

        String[] rows = basePatternCheck(recipe, 1, 3);
        if (rows == null) return false;

        char c = rows[0].charAt(0);
        if (c == ' ') return false;

        String expected = "" + c + c + c;
        return rows[0].equals(expected) && rows[1].charAt(0) == c && rows[2].charAt(0) == c && rows[2].equals(expected);
    }

    @Unique
    private static boolean hasSlabPattern(JsonObject recipe) {
        String[] rows = basePatternCheck(recipe, 6, 1);
        if (rows == null) return false;

        char c = rows[0].charAt(0);
        if (c == ' ') return false;

        String expected = "" + c + c + c;
        return rows[0].equals(expected);
    }

    @Unique
    private static boolean hasStairsPattern(JsonObject recipe) {
        String[] rows = basePatternCheck(recipe, 0, 3);
        if (rows == null) return false;

        char c = rows[0].charAt(0);
        if (c == ' ') return false;

        String expected0 = "" + c + "  ";
        String expected1 = "" + c + c + " ";
        String expected2 = "" + c + c + c;

        return rows[0].equals(expected0) && rows[1].equals(expected1) && rows[2].equals(expected2);
    }
}
