package com.mars.wishfulrecipes;

import com.google.common.collect.Lists;
import com.google.gson.*;
import com.mars.deimos.datagen.DeimosRecipeGenerator;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Unique;

import java.util.*;

import static com.mars.wishfulrecipes.CommonClass.itemsInTags;
import static com.mars.wishfulrecipes.WishfulRecipesConfig.*;

public final class WishfulRecipeGeneration {
    private static final Logger LOGGER = LogUtils.getLogger();

    private WishfulRecipeGeneration() {}

    public static void generate(List<JsonElement> loadedRecipes) {
        Map<String, String[]> isRawMetal = new HashMap<>();
        Set<String> isStone = new HashSet<>();

        // first recipe loop
        for (JsonElement recipeElement : loadedRecipes) {
            try {
                JsonObject recipe = recipeElement.getAsJsonObject();
                String result = getResult(recipe);
                if (result == null) continue;

                if (getIngredients(recipe).length > 0 && blasting_stone_enable
                        && Objects.equals(getType(recipe), "minecraft:stonecutting")) {
                    isStone.addAll(Arrays.asList(getIngredients(recipe)));
                }

                if (blasting_raw_metal_blocks_enable && Objects.equals(getType(recipe), "minecraft:blasting")) {
                    for (String ingredient : getIngredients(recipe)) {
                        if (!isRawMetal.containsKey(ingredient)) {
                            isRawMetal.put(ingredient,
                                    new String[]{ingredient, result, "", "", String.valueOf(getExp(recipe))});
                        }
                    }
                }

                if (hasSlabPattern(recipe) && slabs_to_blocks_enable) {
                    for (String key : getKeys(recipe)) {
                        if (key == null) continue;
                        DeimosRecipeGenerator.createShapelessRecipeJson(
                                Lists.newArrayList(result, result), key, slabs_to_blocks_amount);
                    }
                }

                if (hasStairsPattern(recipe)) {
                    for (String key : getKeys(recipe)) {
                        if (key == null) continue;

                        if (better_stairs_crafting_enable) {
                            DeimosRecipeGenerator.createShapedRecipeJson(
                                    Lists.newArrayList(key),
                                    Lists.newArrayList("# ", "##"),
                                    result,
                                    better_stairs_crafting_amount);
                        }
                        if (stairs_to_blocks_enable) {
                            DeimosRecipeGenerator.createItemConvertorJson(result, key, stairs_to_blocks_amount);
                        }
                        if (alternative_stairs_to_blocks_enable) {
                            DeimosRecipeGenerator.createShapedRecipeJson(
                                    Lists.newArrayList(result),
                                    Lists.newArrayList("##", "##"),
                                    key,
                                    alternative_stairs_to_blocks_amount);
                        }
                    }
                }

                if (hasWallPattern(recipe) && walls_to_blocks_enable) {
                    for (String key : getKeys(recipe)) {
                        if (key == null) continue;
                        DeimosRecipeGenerator.createItemConvertorJson(result, key, walls_to_blocks_amount);
                    }
                }

                if (use_stone_crafting_materials_enable && use_stone_crafting_materials_list.contains(result)) {
                    JsonObject recipeCopy = recipe.deepCopy();

                    if (recipeCopy.has("key") && recipeCopy.get("key").isJsonObject()) {
                        JsonObject keyObject = recipeCopy.getAsJsonObject("key");
                        for (Map.Entry<String, JsonElement> entry : keyObject.entrySet()) {
                            if (isIsCobblestone(entry.getValue())) {
                                keyObject.addProperty(entry.getKey(), "#minecraft:stone_crafting_materials");
                            }
                        }
                    }

                    if (recipeCopy.has("ingredients") && recipeCopy.get("ingredients").isJsonArray()) {
                        JsonArray ingredientsArray = recipeCopy.getAsJsonArray("ingredients");
                        for (int i = 0; i < ingredientsArray.size(); i++) {
                            if (isIsCobblestone(ingredientsArray.get(i))) {
                                ingredientsArray.set(i, new JsonPrimitive("#minecraft:stone_crafting_materials"));
                            }
                        }
                    }

                    DeimosRecipeGenerator.RECIPES.add(recipeCopy);
                }
            } catch (Exception e) {
                LOGGER.error("[WishfulRecipes] Error processing recipe in first loop", e);
            }
        }

        // second recipe loop
        for (JsonElement recipeElement : loadedRecipes) {
            try {
                JsonObject recipe = recipeElement.getAsJsonObject();
                String result = getResult(recipe);
                if (result == null) continue;

                if (blasting_raw_metal_blocks_enable
                        && (hasFullBlockPattern(recipe) || isMekanismRawBlockPattern(recipe))) {
                    for (String key : getKeys(recipe)) {
                        if (key == null) continue;

                        if (isRawMetal.containsKey(key)) {
                            String[] items = isRawMetal.get(key);
                            items[2] = result;
                            isRawMetal.put(key, items);
                        }

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
                    for (String ingredient : getIngredients(recipe)) {
                        if (isStone.contains(ingredient) || isStone.contains(getResult(recipe))) {
                            DeimosRecipeGenerator.createBlastingJson(ingredient, result, 100, getExp(recipe));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // blasting / smelting raw metal blocks
        if (blasting_raw_metal_blocks_enable || smelting_raw_metal_blocks_enable) {
            for (String key : isRawMetal.keySet()) {
                String[] items = isRawMetal.get(key);
                if (Arrays.asList(items).contains("")) continue;

                if (blasting_raw_metal_blocks_enable) {
                    DeimosRecipeGenerator.createBlastingJson(items[2], items[3],
                            blasting_raw_metal_blocks_cookingtime, 9 * Float.parseFloat(items[4]));
                }
                if (smelting_raw_metal_blocks_enable) {
                    DeimosRecipeGenerator.createSmeltingJson(items[2], items[3],
                            2 * blasting_raw_metal_blocks_cookingtime, 9 * Float.parseFloat(items[4]));
                }
            }
        }
    }

    @Unique
    private static String getSafeString(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString();
        }
        return null;
    }

    @Unique
    private static String getType(JsonObject recipe) {
        if (!recipe.has("type")) return null;
        return getSafeString(recipe.get("type"));
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

        // Primitive string format (e.g. "minecraft:iron_block")
        String safeStr = getSafeString(resultElement);
        if (safeStr != null) return safeStr;

        // Object format (e.g. {"id": "minecraft:iron_block", "count": 1})
        if (resultElement.isJsonObject()) {
            JsonObject resultObject = resultElement.getAsJsonObject();
            if (resultObject.has("id")) {
                return getSafeString(resultObject.get("id"));
            } else if (resultObject.has("item")) {
                return getSafeString(resultObject.get("item"));
            }
        }
        return null;
    }

    @Unique
    private static int getCount(JsonObject recipe) {
        if (!recipe.has("result")) return 0;
        JsonElement resultElement = recipe.get("result");

        if (getSafeString(resultElement) != null) {
            return 1; // Implicit default for string results
        }

        if (resultElement.isJsonObject()) {
            JsonObject resultObject = resultElement.getAsJsonObject();
            if (resultObject.has("count")) {
                JsonElement countElement = resultObject.get("count");
                if (countElement.isJsonPrimitive() && countElement.getAsJsonPrimitive().isNumber()) {
                    return countElement.getAsInt();
                }
            }
            return 1; // Explicit object without a count also defaults to 1
        }
        return 0;
    }

    @Unique
    private static String[] getIngredients(JsonObject recipe) {
        List<String> ingredientsList = new ArrayList<>();

        if (recipe != null && recipe.isJsonObject()) {
            JsonElement ingredientElement = null;

            // 26.3 changed SingleItemRecipe (Smelting/Blasting) to use "input" instead of "ingredient"
            if (recipe.has("input")) {
                ingredientElement = recipe.get("input");
            } else if (recipe.has("ingredient")) {
                ingredientElement = recipe.get("ingredient");
            } else if (recipe.has("ingredients")) {
                ingredientElement = recipe.get("ingredients");
            }

            if (ingredientElement != null) {
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
        String safeStr = getSafeString(element);

        if (safeStr != null) {
            if (safeStr.startsWith("#")) {
                String tag = safeStr.substring(1);
                if (itemsInTags != null && itemsInTags.containsKey(tag)) {
                    list.addAll(itemsInTags.get(tag));
                }
            } else {
                list.add(safeStr);
            }
        } else if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();

            // Extract safely using the new helper
            if (obj.has("item")) {
                String item = getSafeString(obj.get("item"));
                if (item != null) list.add(item);
            } else if (obj.has("id")) {
                String id = getSafeString(obj.get("id"));
                if (id != null) list.add(id);
            } else if (obj.has("tag")) {
                String tag = getSafeString(obj.get("tag"));
                if (tag != null && itemsInTags != null && itemsInTags.containsKey(tag)) {
                    list.addAll(itemsInTags.get(tag));
                }
            }
        }
    }

    @Unique
    private static boolean isIsCobblestone(JsonElement entry) {
        if (entry == null) return false;

        String safeStr = getSafeString(entry);
        if (safeStr != null) {
            return safeStr.equals("minecraft:cobblestone") || safeStr.equals("#c:cobblestones/normal");
        }

        if (entry.isJsonObject()) {
            JsonObject obj = entry.getAsJsonObject();

            if (obj.has("item")) {
                String item = getSafeString(obj.get("item"));
                if (item != null && (item.equals("minecraft:cobblestone") || item.equals("#c:cobblestones/normal"))) return true;
            }
            if (obj.has("id")) {
                String id = getSafeString(obj.get("id"));
                if (id != null && (id.equals("minecraft:cobblestone") || id.equals("#c:cobblestones/normal"))) return true;
            }
        }
        return false;
    }

    // if rightAmount set to 0 it doesnt check for it
    @Unique
    private static String[] basePatternCheck(JsonObject recipe, int rightAmount, int rightPatternArraySize) {
        if (!"minecraft:crafting_shaped".equals(getType(recipe))) return null;

        if (rightAmount != 0 && getCount(recipe) != rightAmount) return null;

        if (!recipe.has("pattern")) return null;

        JsonElement patternElem = recipe.get("pattern");
        if (!patternElem.isJsonArray()) return null;

        JsonArray patternArray = patternElem.getAsJsonArray();
        if (patternArray.size() != rightPatternArraySize) return null;

        String[] rows = new String[rightPatternArraySize];
        for (int i = 0; i < rightPatternArraySize; i++) {
            JsonElement rowElem = patternArray.get(i);

            // Protect pattern lines
            String rowStr = getSafeString(rowElem);
            if (rowStr == null || rowStr.length() != 3) return null;

            rows[i] = rowStr;
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
        String result = getResult(recipe);
        if (result == null || !result.contains("block")) return false;

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