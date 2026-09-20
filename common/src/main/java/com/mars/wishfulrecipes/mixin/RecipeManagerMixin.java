package com.mars.wishfulrecipes.mixin;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mars.deimos.datagen.DeimosRecipeGenerator;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.*;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.stream.Collectors;

import static com.mars.wishfulrecipes.CommonClass.itemsInTags;
import static com.mars.wishfulrecipes.Constants.MOD_ID;
import static com.mars.wishfulrecipes.WishfulRecipesConfig.*;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @Unique
    private static final Logger wishfulrecipes$LOGGER = LogUtils.getLogger();

    @Mutable
    @Shadow
    @Final
    private RecipeMap recipes;

    @Mutable
    @Shadow
    @Final
    private Collection<RecipeHolder<?>> learnableRecipes;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void injectAndModifyRecipes(HolderLookup.Provider registries, CallbackInfo ci) {

        ImmutableMultimap.Builder<RecipeType<?>, RecipeHolder<?>> byType = ImmutableMultimap.builder();
        ImmutableMap.Builder<ResourceKey<Recipe<?>>, RecipeHolder<?>> byKey = ImmutableMap.builder();

        RegistryOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
        List<JsonElement> loadedRecipes = new ArrayList<>();

        // Serialize existing recipes back to JSON
        for (RecipeHolder<?> holder : this.recipes.values()) {
            byType.put(holder.value().getType(), holder);
            byKey.put(holder.id(), holder);

            Recipe.DIRECT_CODEC.encodeStart(ops, (Recipe) holder.value()).ifSuccess(json -> {
                loadedRecipes.add(json);
            }).ifError(err -> wishfulrecipes$LOGGER.error("[WishfulRecipes] Failed to encode recipe to JSON: " + err));
        }

        // Clear previous recipes to support the /reload command properly
        if (DeimosRecipeGenerator.RECIPES != null) {
            DeimosRecipeGenerator.RECIPES.clear();
        } else {
            DeimosRecipeGenerator.RECIPES = new ArrayList<>();
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

                        // better stairs crafting
                        if (better_stairs_crafting_enable) {
                            DeimosRecipeGenerator.createShapedRecipeJson(
                                    Lists.newArrayList(key),
                                    Lists.newArrayList("# ", "##"),
                                    result,
                                    better_stairs_crafting_amount
                            );
                        }

                        // stairs to blocks
                        if (stairs_to_blocks_enable)
                            DeimosRecipeGenerator.createItemConvertorJson(result, key, stairs_to_blocks_amount);
                        // alternative stairs to blocks
                        if (alternative_stairs_to_blocks_enable)
                            DeimosRecipeGenerator.createShapedRecipeJson(Lists.newArrayList(result), Lists.newArrayList("##", "##"), key, alternative_stairs_to_blocks_amount);
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

                        // 1. SHAPED RECIPES (Checking the "key" object)
                        if (recipeCopy.has("key") && recipeCopy.get("key").isJsonObject()) {
                            JsonObject keyObject = recipeCopy.getAsJsonObject("key");

                            for (Map.Entry<String, JsonElement> entry : keyObject.entrySet()) {
                                String mapKey = entry.getKey();
                                boolean isCobblestone = isIsCobblestone(entry.getValue());

                                if (isCobblestone) {
                                    // 1.21.2+ Standard: Replace the entire definition with a primitive string
                                    keyObject.addProperty(mapKey, "#minecraft:stone_crafting_materials");
                                }
                            }
                        }

                        // 2. SHAPELESS RECIPES (Checking the "ingredients" array)
                        if (recipeCopy.has("ingredients") && recipeCopy.get("ingredients").isJsonArray()) {
                            JsonArray ingredientsArray = recipeCopy.getAsJsonArray("ingredients");

                            for (int i = 0; i < ingredientsArray.size(); i++) {
                                boolean isCobblestone = isIsCobblestone(ingredientsArray.get(i));

                                if (isCobblestone) {
                                    // 1.21.2+ Standard: Overwrite the object in the array with a primitive string
                                    ingredientsArray.set(i, new com.google.gson.JsonPrimitive("#minecraft:stone_crafting_materials"));
                                }
                            }
                        }

                        DeimosRecipeGenerator.RECIPES.add(recipeCopy);
                    }
                }
            }
            catch (Exception ignored) {
                wishfulrecipes$LOGGER.error("[WishfulRecipes] Error processing recipe in first loop", ignored);}
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

        // Parse dynamic JSON recipes back into standard 26.3 objects
        int i = 0;
        for (JsonElement jsonElement : DeimosRecipeGenerator.RECIPES) {
            int finalI = i;

            // Use DIRECT_CODEC. It returns the raw Recipe directly, no Holder checks needed
            Recipe.DIRECT_CODEC.parse(ops, jsonElement).ifSuccess((recipeInstance) -> {

                ResourceKey<Recipe<?>> resourceKey = ResourceKey.create(
                        Registries.RECIPE,
                        Identifier.fromNamespaceAndPath(MOD_ID, "generated_crafting_" + finalI)
                );

                // Cast recipeInstance to (Recipe<?>) just in case Java's inference complains
                RecipeHolder<?> recipeHolder = new RecipeHolder<>(resourceKey, (Recipe<?>) recipeInstance);
                byType.put(recipeInstance.getType(), recipeHolder);
                byKey.put(resourceKey, recipeHolder);

            }).ifError(err -> wishfulrecipes$LOGGER.error("[WishfulRecipes] Failed to parse generated recipe: " + err));

            i++;
        }

        // Construct the final RecipeMap and overwrite the fields
        try {
            java.lang.reflect.Constructor<RecipeMap> constructor = RecipeMap.class.getDeclaredConstructor(Multimap.class, java.util.Map.class);
            constructor.setAccessible(true);
            RecipeMap newMap = constructor.newInstance(byType.build(), byKey.build());

            // Overwrite the original fields
            this.recipes = newMap;
            this.learnableRecipes = newMap.values().stream().filter(r -> !r.value().isSpecial()).collect(Collectors.toUnmodifiableList());

        } catch (Exception e) {
            throw new RuntimeException("Failed to inject WishfulRecipes map!", e);
        }
    }

    @Unique
    private static String getSafeString(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return element.getAsString();
        }
        return null; // Safely ignore objects/arrays to prevent UnsupportedOperationException
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
