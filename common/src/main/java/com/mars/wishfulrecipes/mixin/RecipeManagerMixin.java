package com.mars.wishfulrecipes.mixin;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mars.deimos.datagen.DeimosRecipeGenerator;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

import static com.mars.wishfulrecipes.CommonClass.itemsInTags;
import static com.mars.wishfulrecipes.WishfulRecipesConfig.*;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    @Inject(method = "apply*", at = @At("HEAD"))
    private void onRecipesLoaded(Map<ResourceLocation, JsonElement> map, ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo info) {
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
        for (JsonElement recipeElement : map.values()) {
            try {
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

                        if (stairs_to_blocks_enable) DeimosRecipeGenerator.createItemConvertorJson(result, key, stairs_to_blocks_amount);
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
        for (JsonElement recipeElement : map.values()) {
            try {
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
                        for (Map.Entry<String, String[]> entry: isRawMetal.entrySet()) {
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
    }

    @Unique
    private static String getType(JsonObject recipe) {
        if (!recipe.has("type")) return null;

        JsonElement typeElement = recipe.get("type");
        if (!typeElement.isJsonPrimitive() || !((JsonPrimitive) typeElement).isString()) return null;

        return typeElement.getAsString();
    }

    @Unique
    private static float getExp(JsonObject recipe) {
        if (!recipe.has("experience")) return 0;

        JsonElement typeElement = recipe.get("experience");
        if (!typeElement.isJsonPrimitive() || !((JsonPrimitive) typeElement).isNumber()) return 0;

        return typeElement.getAsFloat();
    }

    @Unique
    private static String getResult(JsonObject recipe) {
        if (!recipe.has("result")) return null;

        JsonElement resultElement = recipe.get("result");

        // 1.20.4 sometimes uses a string primitive for the result (e.g., stonecutting)
        if (resultElement.isJsonPrimitive() && ((JsonPrimitive) resultElement).isString()) {
            return resultElement.getAsString();
        }

        if (!resultElement.isJsonObject()) return null;
        JsonObject resultObject = resultElement.getAsJsonObject();

        // 1.20.4 uses "item" instead of 1.20.6's "id"
        if (!resultObject.has("item")) return null;

        JsonElement itemElement = resultObject.get("item");
        if (!itemElement.isJsonPrimitive() || !((JsonPrimitive) itemElement).isString()) return null;

        return itemElement.getAsString();
    }

    @Unique
    private static int getCount(JsonObject recipe) {
        if (!recipe.has("result")) return 0;

        JsonElement resultElement = recipe.get("result");

        if (resultElement.isJsonPrimitive()) {
            if (recipe.has("count")) {
                JsonElement rootCount = recipe.get("count");
                if (rootCount.isJsonPrimitive() && ((JsonPrimitive) rootCount).isNumber()) {
                    return rootCount.getAsInt();
                }
            }
            return 1;
        }

        if (!resultElement.isJsonObject()) return 0;
        JsonObject resultObject = resultElement.getAsJsonObject();

        // In 1.20.4, standard 1-item outputs completely omit the "count" tag
        if (!resultObject.has("count")) return 1;

        JsonElement countElement = resultObject.get("count");
        if (!countElement.isJsonPrimitive() || !((JsonPrimitive) countElement).isNumber()) return 1;

        return countElement.getAsInt();
    }

    @Unique
    private static String[] getIngredients(JsonObject recipe) {
        List<String> ingredientsList = new ArrayList<>();

        // Ensure the element is a valid object before accessing fields
        if (recipe != null && recipe.isJsonObject()) {
            JsonObject root = recipe.getAsJsonObject();

            // Check if the "ingredient" member exists
            if (root.has("ingredient")) {
                JsonElement ingredientElement = root.get("ingredient");

                // CASE 1: Ingredient is a single Object (e.g., {"item": "minecraft:raw_gold"})
                if (ingredientElement.isJsonObject()) {
                    JsonObject ingObj = ingredientElement.getAsJsonObject();
                    if (ingObj.has("item")) {
                        ingredientsList.add(ingObj.get("item").getAsString());
                    }
                    if (ingObj.has("tag")) {
                        String tagName = ingObj.get("tag").getAsString();
                        List<String> items = itemsInTags.get(tagName);
                        if (items != null)
                            ingredientsList.addAll(items);
                    }
                }
                // CASE 2: Ingredient is an Array (e.g., [{"item":...}, {"item":...}])
                else if (ingredientElement.isJsonArray()) {
                    JsonArray ingArray = ingredientElement.getAsJsonArray();
                    for (JsonElement ingItem : ingArray) {
                        if (ingItem.isJsonObject()) {
                            JsonObject ingObj = ingItem.getAsJsonObject();
                            if (ingObj.has("item")) {
                                ingredientsList.add(ingObj.get("item").getAsString());
                            }
                        }
                    }
                }
            }
        }

        // Convert List to String Array
        return ingredientsList.toArray(new String[0]);
    }

    @Unique
    private static String[] getKeys(JsonObject recipe) {
        List<String> items = new ArrayList<>();

        // Ensure the element is a valid object
        if (recipe == null || !recipe.isJsonObject()) {
            return new String[0];
        }

        JsonObject root = recipe.getAsJsonObject();

        // Check if the "key" object exists
        if (root.has("key")) {
            JsonObject keyObject = root.getAsJsonObject("key");

            // Iterate through every character key (e.g., "#", "X")
            for (Map.Entry<String, JsonElement> entry : keyObject.entrySet()) {
                JsonElement ingredient = entry.getValue();

                // Case 1: The ingredient is a list of alternatives (JsonArray)
                // Example: "key": { "#": [ {"item": "A"}, {"item": "B"} ] }
                if (ingredient.isJsonArray()) {
                    JsonArray alternatives = ingredient.getAsJsonArray();
                    for (JsonElement alt : alternatives) {
                        extractItemString(alt.getAsJsonObject(), items);
                    }
                }
                // Case 2: The ingredient is a single object (JsonObject)
                // Example: "key": { "#": {"item": "A"} }
                else if (ingredient.isJsonObject()) {
                    extractItemString(ingredient.getAsJsonObject(), items);
                }
            }
        }

        return items.toArray(new String[0]);
    }

    @Unique
    private static void extractItemString(JsonObject recipe, List<String> list) {
        if (!recipe.isJsonObject()) return;

        JsonObject obj = recipe.getAsJsonObject();

        if (obj.has("item")) {
            list.add(obj.get("item").getAsString());
        }
        else if (obj.has("tag") && itemsInTags.containsKey(obj.get("tag").getAsString())) {
            list.addAll(itemsInTags.get(obj.get("tag").getAsString()));
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
            if (!rowElem.isJsonPrimitive() || !((JsonPrimitive) rowElem).isString()) return null;
            rows[i] = rowElem.getAsString();
            if (rows[i].length() != 3) return null;
        }

        return rows;
    }

    @Unique
    private static boolean hasWallPattern(JsonObject recipe) {
        String[] rows = basePatternCheck(recipe, 6, 2);
        if (rows == null) return false;

        // Extract the candidate character
        char c = rows[0].charAt(0);
        if (c == ' ') return false;

        // Build the expected string line for this c:
        String expectedLine = "" + c + c + c;
        return rows[0].equals(expectedLine) && rows[1].equals(expectedLine);
    }

    @Unique
    private static boolean hasFullBlockPattern(JsonObject recipe) {
        String[] rows = basePatternCheck(recipe, 1, 3);
        if (rows == null) return false;

        // Extract the candidate character
        char c = rows[0].charAt(0);
        if (c == ' ') return false;

        // Build the expected string line for this c:
        String expectedLine = "" + c + c + c;
        return rows[0].equals(expectedLine) && rows[1].equals(expectedLine) && rows[2].equals(expectedLine);
    }

    @Unique
    private static boolean isMekanismRawBlockPattern(JsonObject recipe) {
        // isnt golden apple
        if (!getResult(recipe).contains("block")) return false;

        String[] rows = basePatternCheck(recipe, 1, 3);
        if (rows == null) return false;

        // Extract the candidate character
        char c = rows[0].charAt(0);
        if (c == ' ') return false;

        String expected = "" + c + c + c;
        return rows[0].equals(expected) && rows[1].charAt(0) == c && rows[2].charAt(0) == c && rows[2].equals(expected);
    }

    @Unique
    private static boolean hasSlabPattern(JsonObject recipe) {
        String[] rows = basePatternCheck(recipe, 6, 1);
        if (rows == null) return false;

        // Extract the candidate character
        char c = rows[0].charAt(0);
        if (c == ' ') return false;

        String expected = "" + c + c + c;
        return rows[0].equals(expected);
    }

    @Unique
    private static boolean hasStairsPattern(JsonObject recipe) {
        String[] rows = basePatternCheck(recipe, 0, 3);
        if (rows == null) return false;

        // Extract the candidate character
        char c = rows[0].charAt(0);
        if (c == ' ') return false;

        String expected0 = "" + c + "  ";
        String expected1 = "" + c + c + " ";
        String expected2 = "" + c + c + c;

        return rows[0].equals(expected0) && rows[1].equals(expected1) && rows[2].equals(expected2);
    }
}
