package codechicken.nei.recipe;

import java.awt.Dimension;
import java.util.List;

import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import codechicken.lib.gui.GuiDraw.ITooltipLineHandler;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.Recipe.RecipeId;

public class RecipeTooltipLineHandler implements ITooltipLineHandler {

    protected long lastUpdate = System.currentTimeMillis();
    protected GuiRecipe<?> gui = null;
    protected RecipeId recipeId = null;
    protected boolean createdGui = false;

    public RecipeTooltipLineHandler(RecipeId recipeId) {
        this.recipeId = recipeId;
    }

    public ItemStack getItemStack() {
        return this.recipeId.getResult();
    }

    public RecipeId getRecipeId() {
        return this.recipeId;
    }

    @Override
    public Dimension getSize() {

        if (this.gui == null && !this.createdGui) {
            this.gui = GuiCraftingRecipe.createRecipeGui("recipeId", false, this.recipeId.getResult(), this.recipeId);
            this.createdGui = true;

            if (this.gui != null) {
                this.gui.initGui();
                this.gui.guiTop = 0;
                this.gui.guiLeft = 0;
            }
        }

        if (this.gui != null) {
            return this.gui.getWidgetSize();
        }

        return new Dimension(0, 0);
    }

    @Override
    public void draw(int x, int y) {
        if (this.gui == null) return;

        if ((System.currentTimeMillis() - lastUpdate) > 50) {
            lastUpdate = System.currentTimeMillis();
            this.gui.onUpdate();
        }

        GL11.glPushMatrix();
        GL11.glScaled(1, 1, 3);
        GL11.glTranslatef(x, y, 0);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        RenderHelper.disableStandardItemLighting();

        this.gui.drawGuiContainerBackgroundLayer(0.0f, -100, -100);

        GL11.glPopAttrib();

        if (this.gui.slotcontainer != null) {
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
            RenderHelper.enableGUIStandardItemLighting();
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            List<Slot> slots = this.gui.slotcontainer.inventorySlots;

            for (Slot slot : slots) {
                if (slot != null && slot.getStack() != null) {
                    GuiContainerManager.drawItem(slot.xDisplayPosition, slot.yDisplayPosition, slot.getStack());
                }
            }

            GL11.glPopAttrib();
        }

        this.gui.drawGuiContainerForegroundLayer(-100, -100);

        GL11.glPopMatrix();
    }
}
