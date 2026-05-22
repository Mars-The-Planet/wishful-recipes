package com.mars.wishfulrecipes.mixin;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

import static com.mars.wishfulrecipes.CommonClass.itemsInTags;

@Mixin(TagLoader.class)
public class TagLoaderMixin<T> {
    @Shadow @Final private String directory;

    @Inject(method = "load", at = @At("RETURN"))
    private void onRecipesLoaded(ResourceManager resourceManager, CallbackInfoReturnable<Map<Identifier, List<TagLoader.EntryWithSource>>> cir) {
        Map<Identifier, List<TagLoader.EntryWithSource>> loadedTags = cir.getReturnValue();

        // accepting only item tags
        if (!Objects.equals(this.directory, "tags/item")) return;

        loadedTags.forEach((tagId, entries) -> {
            for (TagLoader.EntryWithSource entryWithSource : entries) {
                String item = entryWithSource.entry().toString();
                String tag = tagId.toString();

                // skipping nested tags
                if (!item.startsWith("#")) {
                    List<String> items;
                    if (itemsInTags.containsKey(tag)) {
                        items = itemsInTags.get(tag);
                    }
                    else {
                        items = new ArrayList<>();
                    }
                    items.add(item);
                    itemsInTags.put(tag, items);
                }
            }
        });
    }
}
