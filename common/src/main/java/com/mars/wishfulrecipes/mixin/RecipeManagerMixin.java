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
    @Shadow @Final private HolderLookup.Provider registries;

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
                if (ingredients.length < 1) continue;
                for (String ingredient : ingredients) {
                    if (!isRawMetal.containsKey(ingredient)) {
                        isRawMetal.put(ingredient, new String[]{ingredient, result, "", "", String.valueOf(getExp(recipe))});
                    }
                }
            }

            if (hasSlabPattern(recipe)) {
                String[] keys = getKeys(recipe);
                if (keys.length < 1 || keys[0] == null) continue;

                // slabs to blocks
                if (slabs_to_blocks_enable)
                    DeimosRecipeGenerator.createShapelessRecipeJson(Lists.newArrayList(result, result), keys[0], slabs_to_blocks_amount);
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
                if (keys.length < 1 || keys[0] == null) continue;

                DeimosRecipeGenerator.createItemConvertorJson(result, keys[0], walls_to_blocks_amount);
            }
        }

        // second recipe loop
        for (JsonElement recipeElement : map.values()) {
            JsonObject recipe = recipeElement.getAsJsonObject();
            // to check for the rest of metal blasting properties
            if (blasting_raw_metal_blocks_enable && (hasFullBlockPattern(recipe) || isMekanismRawBlockPattern(recipe))) {
                String[] keys = getKeys(recipe);
                String result = getResult(recipe);
                if (keys.length < 1) continue;

                for (String key : keys) {
                    if (key == null || result == null) continue;

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
                if (getIngredients(recipe).length > 0 && isStone.contains(getIngredients(recipe)[0]) || isStone.contains(getResult(recipe))) {
                    DeimosRecipeGenerator.createBlastingJson(getIngredients(recipe)[0], Objects.requireNonNull(getResult(recipe)), 100, getExp(recipe));
                }
            }
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

        JsonObject resultObject = recipe.getAsJsonObject("result");
        if (!resultObject.has("id")) return null;

        JsonElement idElement = resultObject.get("id");
        if (!idElement.isJsonPrimitive() || !((JsonPrimitive) idElement).isString()) return null;

        return idElement.getAsString();
    }

    @Unique
    private static int getCount(JsonObject recipe) {
        if (!recipe.has("result")) return 0;

        JsonObject resultObject = recipe.getAsJsonObject("result");
        if (!resultObject.has("count")) return 0;

        JsonElement countElement = resultObject.get("count");
        if (!countElement.isJsonPrimitive() || !((JsonPrimitive) countElement).isNumber()) return 0;

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

    // Helper method to extract "item" (or "tag") from the ingredient object
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

    @Unique
    private static boolean hasWallPattern(JsonObject recipe) {

        // is wall
        if (getCount(recipe) != 6) return false;

        if (!recipe.has("pattern")) return false;

        JsonElement patternElem = recipe.get("pattern");
        if (!patternElem.isJsonArray()) return false;

        JsonArray patternArray = patternElem.getAsJsonArray();
        // Must have exactly 2 rows
        if (patternArray.size() != 2) return false;

        // Each row must be a string of length 3
        String[] rows = new String[2];
        for (int i = 0; i < 2; i++) {
            JsonElement rowElem = patternArray.get(i);
            if (!rowElem.isJsonPrimitive() || !((JsonPrimitive) rowElem).isString()) return false;
            rows[i] = rowElem.getAsString();
            if (rows[i].length() != 3) return false;
        }

        // Extract the candidate character from row[0].charAt(0)
        char c = rows[0].charAt(0);
        if (c == ' ') return false;

        // Build the expected string line for this c:
        String expectedLine = "" + c + c + c;

        return rows[0].equals(expectedLine) && rows[1].equals(expectedLine);
    }

    @Unique
    private static boolean hasFullBlockPattern(JsonObject recipe) {

        if (!getType(recipe).equals("minecraft:crafting_shaped")) return false;

        // isnt full block conversion
        if (getCount(recipe) != 1) return false;

        if (!recipe.has("pattern")) return false;

        JsonElement patternElem = recipe.get("pattern");
        if (!patternElem.isJsonArray()) return false;

        JsonArray patternArray = patternElem.getAsJsonArray();
        // Must have exactly 3 rows
        if (patternArray.size() != 3) return false;

        // Each row must be a string of length 3
        String[] rows = new String[3];
        for (int i = 0; i < 3; i++) {
            JsonElement rowElem = patternArray.get(i);
            if (!rowElem.isJsonPrimitive() || !((JsonPrimitive) rowElem).isString()) return false;
            rows[i] = rowElem.getAsString();
            if (rows[i].length() != 3) return false;
        }

        // Extract the candidate character from row[0].charAt(0)
        char c = rows[0].charAt(0);
        if (c == ' ') return false;

        // Build the expected string line for this c:
        String expectedLine = "" + c + c + c;

        return rows[0].equals(expectedLine) && rows[1].equals(expectedLine) && rows[2].equals(expectedLine);
    }

    @Unique
    private static boolean isMekanismRawBlockPattern(JsonObject recipe) {

        if (!getType(recipe).equals("minecraft:crafting_shaped")) return false;

        // isnt full block conversion
        if (getCount(recipe) != 1) return false;

        // isnt golden apple
        String result = getResult(recipe);
        if (!result.contains("block")) return false;

        if (!recipe.has("pattern")) return false;

        JsonElement patternElem = recipe.get("pattern");
        if (!patternElem.isJsonArray()) return false;

        JsonArray patternArray = patternElem.getAsJsonArray();
        // Must have exactly 3 rows
        if (patternArray.size() != 3) return false;

        // Each row must be a string of length 3
        String[] rows = new String[3];
        for (int i = 0; i < 3; i++) {
            JsonElement rowElem = patternArray.get(i);
            if (!rowElem.isJsonPrimitive() || !((JsonPrimitive) rowElem).isString()) return false;
            rows[i] = rowElem.getAsString();
            if (rows[i].length() != 3) return false;
        }
        
        char char_tag = rows[0].charAt(0);
        char char_item = rows[1].charAt(1);
        if (char_tag == ' ' || char_item == ' ') return false;
        
        String top_bottom = "" + char_tag + char_tag + char_tag; 
        String middle = "" + char_tag + char_item + char_tag;

        return rows[0].equals(top_bottom) && rows[1].equals(middle) && rows[2].equals(top_bottom);
    }

    @Unique
    private static boolean hasSlabPattern(JsonObject recipe) {

        if (!getType(recipe).equals("minecraft:crafting_shaped")) return false;

        // isnt bread
        if (getCount(recipe) != 6) return false;

        if (!recipe.has("pattern")) return false;

        JsonElement patternElem = recipe.get("pattern");
        if (!patternElem.isJsonArray()) return false;

        JsonArray patternArray = patternElem.getAsJsonArray();
        // Must have exactly 1 row
        if (patternArray.size() != 1) return false;

        // row must be a string of length 3
        JsonElement rowElem = patternArray.get(0);
        if (!rowElem.isJsonPrimitive() || !((JsonPrimitive) rowElem).isString()) return false;
        String row = rowElem.getAsString();
        if (row.length() != 3) return false;

        // Extract the candidate character from row[0].charAt(0)
        char c = row.charAt(0);
        if (c == ' ') return false;

        // Build the three expected strings for this c:
        String expected = "" + c + c + c;    // three c's, no spaces

        return row.equals(expected);
    }

    @Unique
    private static boolean hasStairsPattern(JsonObject recipe) {

        if (!getType(recipe).equals("minecraft:crafting_shaped")) return false;

        if (!recipe.has("pattern")) return false;

        JsonElement patternElem = recipe.get("pattern");
        if (!patternElem.isJsonArray()) return false;

        JsonArray patternArray = patternElem.getAsJsonArray();
        // Must have exactly 3 rows
        if (patternArray.size() != 3) return false;

        // Each row must be a string of length 3
        String[] rows = new String[3];
        for (int i = 0; i < 3; i++) {
            JsonElement rowElem = patternArray.get(i);
            if (!rowElem.isJsonPrimitive() || !((JsonPrimitive) rowElem).isString()) return false;
            rows[i] = rowElem.getAsString();
            if (rows[i].length() != 3) return false;
        }

        // Extract the candidate character from row[0].charAt(0)
        char c = rows[0].charAt(0);
        if (c == ' ') return false;

        // Build the three expected strings for this c:
        String expected0 = "" + c + "  ";     // c + two spaces
        String expected1 = "" + c + c + " ";  // two c's + one space
        String expected2 = "" + c + c + c;    // three c's, no spaces

        return rows[0].equals(expected0) && rows[1].equals(expected1) && rows[2].equals(expected2);
    }
}
