package com.mars.wishfulrecipes.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.Tag;
import net.minecraft.tags.TagLoader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.mars.wishfulrecipes.CommonClass.itemsInTags;

@Mixin(TagLoader.class)
public class TagLoaderMixin<T> {
    @Shadow @Final private String directory;

    @Inject(method = "load", at = @At("RETURN"))
    private void onRecipesLoaded(ResourceManager resourceManager, CallbackInfoReturnable<Map<ResourceLocation, Tag.Builder>> cir) {
        Map<ResourceLocation, Tag.Builder> loadedTags = cir.getReturnValue();

        if (!Objects.equals(this.directory, "tags/items")) return;

        loadedTags.forEach((tagId, builder) -> {
            builder.getEntries().forEach(entryWithSource -> {
                String item = entryWithSource.entry().toString();
                String tag = tagId.toString();

                // skipping nested tags
                if (!item.startsWith("#")) {
                    List<String> items;
                    // Assuming itemsInTags is defined elsewhere in your class
                    if (itemsInTags.containsKey(tag)) {
                        items = itemsInTags.get(tag);
                    }
                    else {
                        items = new ArrayList<>();
                    }
                    items.add(item);
                    itemsInTags.put(tag, items);
                }
            });
        });
    }
}