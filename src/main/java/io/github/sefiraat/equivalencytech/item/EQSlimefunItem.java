package io.github.sefiraat.equivalencytech.item;

import com.github.drakescraft_labs.slimefun4.api.items.ItemGroup;
import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItem;
import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItemStack;
import com.github.drakescraft_labs.slimefun4.api.recipes.RecipeType;
import com.github.drakescraft_labs.slimefun4.core.attributes.NotConfigurable;
import com.github.drakescraft_labs.slimefun4.core.attributes.NotPlaceable;
import org.bukkit.inventory.ItemStack;

public class EQSlimefunItem extends SlimefunItem implements NotPlaceable, NotConfigurable {

    public EQSlimefunItem(ItemGroup category, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(category, item, recipeType, recipe);
        setUseableInWorkbench(true);
    }

}