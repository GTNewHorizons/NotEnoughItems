package codechicken.nei;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.api.INEIGuiAdapter;
import com.google.common.collect.Ordering;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.InventoryEffectRenderer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import java.util.Collection;
import java.util.Collections;

/**
 * Inspired by InventoryEffectRendererGuiHandler.java in JEI
 */
public class NEIPotionGuiHandler extends INEIGuiAdapter {
    @Override
    public boolean hideItemPanelSlot(GuiContainer guiContainer, int slotX, int slotY, int slotW, int slotH) {
        if(guiContainer instanceof InventoryEffectRenderer) {
            int x = guiContainer.guiLeft - 124;
            int y = guiContainer.guiTop;
            Minecraft minecraft = guiContainer.mc;
            if (minecraft == null) {
                return false;
            }
            EntityPlayerSP player = minecraft.thePlayer;
            if (player == null) {
                return false;
            }
            Collection<PotionEffect> activePotionEffects = player.getActivePotionEffects();
            if (activePotionEffects.isEmpty()) {
                return false;
            }
            int height = 33;
            if (activePotionEffects.size() > 5) {
                height = 132 / (activePotionEffects.size() - 1);
            }
            Rectangle4i slotRect = new Rectangle4i(slotX, slotY, slotW, slotH);
            for (PotionEffect potioneffect : activePotionEffects) {
                Rectangle4i box = new Rectangle4i(x, y, 140, 32);
                if(box.intersects(slotRect))
                    return true;
                y += height;
            }
        }
        return false;
    }
}
