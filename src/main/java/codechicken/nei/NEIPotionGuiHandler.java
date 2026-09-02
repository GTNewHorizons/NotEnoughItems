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
        if (!(guiContainer instanceof InventoryEffectRenderer)) {
            return false;
        }

        int x = guiContainer.guiLeft - 124;
        if (slotX + slotW <= x || slotX >= x + 140) {
            return false;
        }

        Minecraft minecraft = guiContainer.mc;
        if (minecraft == null) {
            return false;
        }
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null) {
            return false;
        }
        int potionCount = player.getActivePotionEffects().size();
        if (potionCount == 0 || NEIClientConfig.ignorePotionOverlap()) {
            return false;
        }
        int height = 33;
        if (potionCount > 5) {
            height = 132 / (potionCount - 1);
        }
        return intersectsPotionEffects(slotY, slotH, guiContainer.guiTop, potionCount, height);
    }

    static boolean intersectsPotionEffects(int slotY, int slotH, int effectY, int potionCount, int height) {
        for (int i = 0; i < potionCount; i++, effectY += height) {
            if (slotY + slotH > effectY && slotY < effectY + 32) return true;
        }
        return false;
    }
}
