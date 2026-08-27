package codechicken.nei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.InventoryEffectRenderer;

import codechicken.nei.api.INEIGuiAdapter;

/**
 * Inspired by InventoryEffectRendererGuiHandler.java in JEI
 */
public class NEIPotionGuiHandler extends INEIGuiAdapter {

    @Override
    public boolean hideItemPanelSlot(GuiContainer guiContainer, int slotX, int slotY, int slotW, int slotH) {
        if (NEIClientConfig.ignorePotionOverlap()) {
            return false;
        }

        if (guiContainer instanceof InventoryEffectRenderer) {
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
            int potionCount = player.getActivePotionEffects().size();
            if (potionCount == 0) {
                return false;
            }
            int height = 33;
            if (potionCount > 5) {
                height = 132 / (potionCount - 1);
            }
            return intersectsPotionEffects(slotX, slotY, slotW, slotH, x, y, potionCount, height);
        }
        return false;
    }

    static boolean intersectsPotionEffects(int slotX, int slotY, int slotW, int slotH, int effectX, int effectY,
            int potionCount, int height) {
        for (int i = 0; i < potionCount; i++, effectY += height) {
            if (slotX + slotW > effectX && slotX < effectX + 140 && slotY + slotH > effectY && slotY < effectY + 32)
                return true;
        }
        return false;
    }
}
