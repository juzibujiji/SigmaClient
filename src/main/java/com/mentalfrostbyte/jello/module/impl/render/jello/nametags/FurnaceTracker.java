package com.mentalfrostbyte.jello.module.impl.render.jello.nametags;

import com.mentalfrostbyte.Client;
import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.AirItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipe;
import net.minecraft.item.crafting.IRecipeType;

import java.util.Optional;

public class FurnaceTracker {
    public int windowId;
    public float smeltProgress;
    public float smeltTime;
    public int cooldown;
    public int smeltDelay;
    public ItemStack inputStack;
    public ItemStack fuelStack;
    public ItemStack outputStack;

    public FurnaceTracker(int windowId) {
        this.windowId = windowId;
    }

    public void updateSmelting() {
        this.refreshOutput();
        boolean hasFuel = this.fuelStack != null && this.fuelStack.count > 0;
        boolean hasInput = this.inputStack != null && this.inputStack.count > 0;
        Item smeltingResult = this.getSmeltingResult();
        boolean canSmelt = smeltingResult != null
                && this.outputStack != null
                && smeltingResult.equals(this.outputStack.getItem())
                && this.outputStack.count < 64;
        if (this.smeltTime < this.smeltProgress && hasInput && canSmelt && this.smeltDelay > 0) {
            this.smeltTime = this.smeltTime + Client.getInstance().playerTracker.getPing();
        }

        if (this.smeltDelay > 0) {
            this.smeltDelay--;
        }

        if (this.smeltDelay == 0) {
            if (hasFuel && hasInput) {
                this.fuelStack.count--;
                this.smeltDelay = this.cooldown;
            } else {
                this.smeltTime = 0.0F;
            }
        }

        if (this.smeltTime >= this.smeltProgress && this.smeltProgress != 0.0F) {
            if (hasInput) {
                this.inputStack.count--;
            }

            this.smeltTime = 0.0F;
            if (this.outputStack != null) {
                ItemStack result = this.getSmeltingResultStack();
                this.outputStack.count = this.outputStack.count + result.count;
            }
        }

        if (this.inputStack != null && this.inputStack.count == 0) {
            this.inputStack = null;
        }
    }

    public ItemStack getSmeltingResultStack() {
        if (this.inputStack == null) {
            return null;
        }

        // The client's RecipeManager is already synced from the server, so we can query it
        // directly. Reloading the whole recipe manager here (as the old code did) forced a full
        // synchronous recipe reload on every call - and this method runs several times per tick
        // and per frame while a furnace is smelting, which caused the frame drops.
        Optional<FurnaceRecipe> recipe = Minecraft.getInstance().getConnection().getRecipeManager()
                .getRecipe(IRecipeType.SMELTING, new Inventory(this.inputStack), Minecraft.getInstance().world);
        if (recipe.isPresent()) {
            ItemStack result = recipe.get().getRecipeOutput();
            if (!result.isEmpty()) {
                return result.copy();
            }
        }

        return null;
    }

    public Item getSmeltingResult() {
        ItemStack resultStack = getSmeltingResultStack();
        return resultStack == null ? null : resultStack.getItem();
    }

    public ItemStack refreshOutput() {
        if (outputStack != null && outputStack.getItem() instanceof AirItem) {
            outputStack = null;
        }
        if (inputStack != null && inputStack.getItem() instanceof AirItem) {
            inputStack = null;
        }
        if (fuelStack != null && fuelStack.getItem() instanceof AirItem) {
            fuelStack = null;
        }

        if (outputStack == null) {
            if (inputStack != null) {
                ItemStack result = getSmeltingResultStack();
                if (result != null) {
                    result.count = 0;
                }
                return outputStack = result;
            } else {
                return null;
            }
        } else {
            return outputStack;
        }
    }
}
