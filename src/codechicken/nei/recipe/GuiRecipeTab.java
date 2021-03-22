package codechicken.nei.recipe;


import codechicken.lib.gui.GuiDraw;
import codechicken.nei.Widget;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.guihook.GuiContainerManager;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.List;

public class GuiRecipeTab extends Widget {
    public static HashMap<String, ImmutablePair<String, DrawableResource>> imageMap = new HashMap<>();
    public static HashMap<String, ImmutablePair<String, ItemStack>> stackMap = new HashMap<>();
    
    public static final int TAB_WIDTH = 28;
    public static final int TAB_HEIGHT = 31;
    private static final DrawableResource selectedIcon = new DrawableBuilder("minecraft:textures/gui/container/creative_inventory/tabs.png", 28, 32, 28, 32).build();
    private static final DrawableResource unselectedIcon = new DrawableBuilder("minecraft:textures/gui/container/creative_inventory/tabs.png", 28, 0, 28, 30).build();

    private final GuiRecipe guiRecipe;
    private final IRecipeHandler handler;
    private final String handlerName;
    private final String handlerID;

    private boolean selected;

    public GuiRecipeTab(GuiRecipe guiRecipe, IRecipeHandler handler, int x, int y) {
        super();
        this.x = x;
        this.y = y;
        this.w = TAB_WIDTH;
        this.h = TAB_HEIGHT;
        this.handler = handler;
        this.handlerName = handler.toString().split("@")[0];
        this.guiRecipe = guiRecipe;
        this.selected = false;
        
        if(handler instanceof TemplateRecipeHandler) {
            handlerID = (((TemplateRecipeHandler)handler).getOverlayIdentifier());
        } else {
            handlerID = null;
        }
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        drawBackground(mouseX, mouseY);
        drawForeground(mouseX, mouseY);
    }

    public void drawBackground(int mouseX, int mouseY) {
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1, 1, 1, 1);
        final DrawableResource image;
        if (selected)  image = selectedIcon;
        else           image = unselectedIcon;

        final int iconX = x + (w - image.getWidth())  / 2;
        final int iconY = y;
        image.draw(iconX, iconY);
    }

    public void drawForeground(int mouseX, int mouseY) {
        final int iconX = x + (w - 16) / 2;
        final int iconY = y + h - 22;

        final FontRenderer fontRenderer = GuiDraw.fontRenderer;
        final DrawableResource icon = getImage(handlerName);

        final ItemStack itemStack = getItemStack(handlerName, handlerID);
       
        
        if (icon != null) {
            icon.draw(iconX + 1, iconY + 1);
        } else if (itemStack != null) {
            GuiContainerManager.drawItems.zLevel += 100;
            GuiContainerManager.drawItem(iconX, iconY, itemStack);
            GuiContainerManager.drawItems.zLevel -= 100;
        } else {
            // Text fallback
            String text = handler.getRecipeName().substring(0, 2);
            int textCenterX = x + (int) (TAB_WIDTH / 2f);
            int textCenterY = y + (int) (TAB_HEIGHT / 2f) - 3;
            int color = selected ? 0xffffa0 : 0xe0e0e0;
            fontRenderer.drawStringWithShadow(text, textCenterX - (int) (fontRenderer.getStringWidth(text) / 2f), textCenterY, color);
            GL11.glColor4f(1, 1, 1, 1);
        } 
    }

    public void addTooltips(List<String> tooltip) {
        tooltip.add(handler.getRecipeName().trim());
        
        String handlerMod = getHandlerMod(handlerName, handlerID);
        tooltip.add(EnumChatFormatting.BLUE + handlerMod);
        
        if (handlerMod.equals("Unknown")) {
            tooltip.add("");
            tooltip.add("HandlerName: " + handlerName);
            tooltip.add("HandlerID: " + handlerID);
        }

    }
    
    public boolean onButtonPress(boolean rightclick) {
        int newIdx = guiRecipe.currenthandlers.indexOf(handler);
        if (newIdx == -1)
            return false;
    
        guiRecipe.recipetype = newIdx;
        return true;
    }
    
    public void setSelected(IRecipeHandler current) {
        selected = handler == current;
    }

    public static Pair<String, ItemStack> lookupStackMap(String name, String name2) {
        Pair<String, ItemStack> res = stackMap.get(name);
        if (res == null) res = stackMap.get(name2);

        return res;
    }


    public static String getHandlerMod(String name, String name2) {
        Pair<String, ItemStack> stackRes = lookupStackMap(name, name2);
        if (stackRes != null)
            return stackRes.getLeft();
        
        Pair<String, DrawableResource> iconRes = imageMap.get(name);
        if (iconRes != null)
            return iconRes.getLeft();

        return "Unknown";
    }
    
    private ItemStack getItemStack(String handlerName, String handlerID) {
        Pair<String, ItemStack> lookup = lookupStackMap(handlerName, handlerID);
        if (lookup != null)
            return lookup.getRight();
        return null;
    }

    public static DrawableResource getImage(String id) {
        Pair<String, DrawableResource> res = imageMap.get(id);
        if (res != null)
            return res.getRight();
        return null;
    }
    
    public static void initStackMap() {
        System.out.println("InitStackMap() --------");
        // Vanilla
        addToStackMap("codechicken.nei.recipe.BrewingRecipeHandler", "minecraft", "minecraft:brewing_stand");
        addToStackMap("codechicken.nei.recipe.FireworkRecipeHandler", "minecraft", "minecraft:fireworks");
        addToStackMap("codechicken.nei.recipe.FurnaceRecipeHandler", "minecraft", "minecraft:furnace");
        addToStackMap("codechicken.nei.recipe.ShapedRecipeHandler", "minecraft", "minecraft:crafting_table");
        addToStackMap("codechicken.nei.recipe.ShapelessRecipeHandler", "minecraft", "minecraft:crafting_table");
        addToIconMap("codechicken.nei.recipe.FuelRecipeHandler", "minecraft", 
                     new DrawableBuilder("nei:textures/nei_tabbed_sprites.png", 80, 0, 14, 14).build()); 

        // IC2
        if (Loader.isModLoaded("IC2")) {
            addToStackMap("ic2.neiIntegration.core.recipehandler.AdvRecipeHandler", "IC2", "IC2:blockreactorvessel");
            addToStackMap("ic2.neiIntegration.core.recipehandler.AdvShapelessRecipeHandler", "IC2", "IC2:blockreactorvessel");
            addToStackMap("ic2.neiIntegration.core.recipehandler.BlastFurnaceRecipeHandler", "IC2", "IC2:blockMachine3:1");
            addToStackMap("ic2.neiIntegration.core.recipehandler.BlockCutterRecipeHandler", "IC2", "IC2:blockMachine3:2");
            addToStackMap("ic2.neiIntegration.core.recipehandler.CentrifugeRecipeHandler", "IC2", "IC2:blockMachine2:3");
            addToStackMap("ic2.neiIntegration.core.recipehandler.CompressorRecipeHandler", "IC2", "IC2:blockMachine:5");
            addToStackMap("ic2.neiIntegration.core.recipehandler.ExtractorRecipeHandler", "IC2", "IC2:blockMachine:4");
            addToStackMap("ic2.neiIntegration.core.recipehandler.FluidCannerRecipeHandler", "IC2", "IC2:blockMachine:6");
            addToStackMap("ic2.neiIntegration.core.recipehandler.LatheRecipeHandler", "IC2", "IC2:blockMachine3:8");
            addToStackMap("ic2.neiIntegration.core.recipehandler.MaceratorRecipeHandler", "IC2", "IC2:blockMachine:3");
            addToStackMap("ic2.neiIntegration.core.recipehandler.MetalFormerRecipeHandlerCutting", "IC2", "IC2:blockMachine2:4");
            addToStackMap("ic2.neiIntegration.core.recipehandler.MetalFormerRecipeHandlerExtruding", "IC2", "IC2:blockMachine2:4");
            addToStackMap("ic2.neiIntegration.core.recipehandler.MetalFormerRecipeHandlerRolling", "IC2", "IC2:blockMachine2:4");
            addToStackMap("ic2.neiIntegration.core.recipehandler.OreWashingRecipeHandler", "IC2", "IC2:blockMachine2:5");
            addToStackMap("ic2.neiIntegration.core.recipehandler.ScrapboxRecipeHandler", "IC2", "IC2:itemScrapbox");
            addToStackMap("ic2.neiIntegration.core.recipehandler.SolidCannerRecipeHandler", "IC2", "IC2:blockMachine2:9");
        }
        
        // Gregtech 5u
        if (!Loader.isModLoaded("gregapi_post") && Loader.isModLoaded("gregtech")) {
            addToStackMap("gt.recipe.alloyblastsmelter", "gregtech", "gregtech:gt.blockmachines:810");               // gt.blockmachines.industrialsalloyamelter.controller.tier.single
            addToStackMap("gt.recipe.alloysmelter", "gregtech", "gregtech:gt.blockmachines:201");                    // gt.blockmachines.basicmachine.alloysmelter.tier.01
            addToStackMap("gt.recipe.arcfurnace", "gregtech", "gregtech:gt.blockmachines:651");                      // gt.blockmachines.basicmachine.arcfurnace.tier.01
            addToStackMap("gt.recipe.assembler", "gregtech", "gregtech:gt.blockmachines:211");                       // gt.blockmachines.basicmachine.assembler.tier.01
            addToStackMap("gt.recipe.autoclave", "gregtech", "gregtech:gt.blockmachines:571");                       // gt.blockmachines.basicmachine.autoclave.tier.01
            addToStackMap("gt.recipe.blastfurnace", "gregtech", "gregtech:gt.blockmachines:1000");                   // gt.blockmachines.multimachine.blastfurnace
            addToStackMap("gt.recipe.brewer", "gregtech", "gregtech:gt.blockmachines:491");                          // gt.blockmachines.basicmachine.brewery.tier.01
            addToStackMap("gt.recipe.canner", "gregtech", "gregtech:gt.blockmachines:231");                          // gt.blockmachines.basicmachine.canner.tier.01
            addToStackMap("gt.recipe.centrifuge", "gregtech", "gregtech:gt.blockmachines:361");                      // gt.blockmachines.basicmachine.centrifuge.tier.01
            addToStackMap("gt.recipe.chemicalbath", "gregtech", "gregtech:gt.blockmachines:541");                    // gt.blockmachines.basicmachine.chemicalbath.tier.01
            addToStackMap("gt.recipe.chemicaldehydrator", "gregtech", "gregtech:gt.blockmachines:912");              // gt.blockmachines.machine.dehydrator.tier.01
            addToStackMap("gt.recipe.chemicalreactor", "gregtech", "gregtech:gt.blockmachines:421");                 // gt.blockmachines.basicmachine.chemicalreactor.tier.01
            addToStackMap("gt.recipe.circuitassembler", "gregtech", "gregtech:gt.blockmachines:1180");               // gt.blockmachines.basicmachine.circuitassembler.tier.01
            addToStackMap("gt.recipe.cokeoven", "gregtech", "gregtech:gt.blockmachines:791");                        // gt.blockmachines.industrialcokeoven.controller.tier.single
            addToStackMap("gt.recipe.compressor", "gregtech", "gregtech:gt.blockmachines:241");                      // gt.blockmachines.basicmachine.compressor.tier.01
            addToStackMap("gt.recipe.craker", "gregtech", "gregtech:gt.blockmachines:1160");                         // gt.blockmachines.multimachine.cracker
            addToStackMap("gt.recipe.cuttingsaw", "gregtech", "gregtech:gt.blockmachines:251");                      // gt.blockmachines.basicmachine.cutter.tier.01
            addToStackMap("gt.recipe.cyclotron", "gregtech", "gregtech:gt.blockmachines:828");                       // gt.blockmachines.cyclotron.tier.single
            addToStackMap("gt.recipe.dieselgeneratorfuel", "gregtech", "gregtech:gt.blockmachines:793");             // gt.blockmachines.advancedgenerator.rocketfuel.tier.01
            addToStackMap("gt.recipe.distillationtower", "gregtech", "gregtech:gt.blockmachines:1126");              // gt.blockmachines.multimachine.distillationtower
            addToStackMap("gt.recipe.distillery", "gregtech", "gregtech:gt.blockmachines:531");                      // gt.blockmachines.basicmachine.distillery.tier.01
            addToStackMap("gt.recipe.electricimplosioncompressor", "gregtech", "gregtech:gt.blockmachines:12734");   // gt.blockmachines.electricimplosioncompressor
            addToStackMap("gt.recipe.electrolyzer", "gregtech", "gregtech:gt.blockmachines:371");                    // gt.blockmachines.basicmachine.electrolyzer.tier.01
            addToStackMap("gt.recipe.electromagneticseparator", "gregtech", "gregtech:gt.blockmachines:561");        // gt.blockmachines.basicmachine.electromagneticseparator.tier.01
            addToStackMap("gt.recipe.extractor", "gregtech", "gregtech:gt.blockmachines:271");                       // gt.blockmachines.basicmachine.extractor.tier.01
            addToStackMap("gt.recipe.extruder", "gregtech", "gregtech:gt.blockmachines:281");                        // gt.blockmachines.basicmachine.extruder.tier.01
            addToStackMap("gt.recipe.fakeAssemblylineProcess", "gregtech", "gregtech:gt.blockmachines:1170");        // gt.blockmachines.multimachine.assemblyline
            addToStackMap("gt.recipe.fermenter", "gregtech", "gregtech:gt.blockmachines:501");                       // gt.blockmachines.basicmachine.fermenter.tier.01
            addToStackMap("gt.recipe.fishpond", "gregtech", "gregtech:gt.blockmachines:829");                        // gt.blockmachines.industrial.fishpond.controller.tier.single
            addToStackMap("gt.recipe.fluidcanner", "gregtech", "gregtech:gt.blockmachines:431");                     // gt.blockmachines.basicmachine.fluidcanner.tier.01
            addToStackMap("gt.recipe.fluidextractor", "gregtech", "gregtech:gt.blockmachines:511");                  // gt.blockmachines.basicmachine.fluidextractor.tier.01
            addToStackMap("gt.recipe.fluidheater", "gregtech", "gregtech:gt.blockmachines:621");                     // gt.blockmachines.basicmachine.fluidheater.tier.01
            addToStackMap("gt.recipe.fluidsolidifier", "gregtech", "gregtech:gt.blockmachines:521");                 // gt.blockmachines.basicmachine.fluidsolidifier.tier.01
            addToStackMap("gt.recipe.fusionreactor", "gregtech", "gregtech:gt.blockmachines:1193");                  // gt.blockmachines.fusioncomputer.tier.06
            addToStackMap("gt.recipe.gasturbinefuel", "gregtech", "gregtech:gt.blockmachines:961");                  // gt.blockmachines.basicgenerator.gas.tier.00
            addToStackMap("gt.recipe.geothermalfuel", "gregtech", "gregtech:gt.blockmachines:830");                  // gt.blockmachines.advancedgenerator.geothermalfuel.tier.01
            addToStackMap("gt.recipe.hammer", "gregtech", "gregtech:gt.blockmachines:611");                          // gt.blockmachines.basicmachine.hammer.tier.01
            addToStackMap("gt.recipe.implosioncompressor", "gregtech", "gregtech:gt.blockmachines:12734");           // gt.blockmachines.electricimplosioncompressor
            addToStackMap("gt.recipe.largeboilerfakefuels", "gregtech", "gregtech:gt.blockmachines:1020");           // gt.blockmachines.multimachine.boiler.bronze
            addToStackMap("gt.recipe.largechemicalreactor", "gregtech", "gregtech:gt.blockmachines:1169");           // gt.blockmachines.multimachine.chemicalreactor
            addToStackMap("gt.recipe.laserengraver", "gregtech", "gregtech:gt.blockmachines:591");                   // gt.blockmachines.basicmachine.laserengraver.tier.01
            addToStackMap("gt.recipe.lathe", "gregtech", "gregtech:gt.blockmachines:291");                           // gt.blockmachines.basicmachine.lathe.tier.01
            addToStackMap("gt.recipe.macerator", "gregtech", "gregtech:gt.blockmachines:301");                       // gt.blockmachines.basicmachine.macerator.tier.01
            addToStackMap("gt.recipe.magicfuels", "gregtech", "gregtech:gt.blockmachines:1123");                     // gt.blockmachines.basicgenerator.magicenergyconverter.tier.01
            addToStackMap("gt.recipe.massfab", "gregtech", "gregtech:gt.blockmachines:461");                         // gt.blockmachines.basicmachine.massfab.tier.01
            addToStackMap("gt.recipe.metalbender", "gregtech", "gregtech:gt.blockmachines:221");                     // gt.blockmachines.basicmachine.bender.tier.01
            addToStackMap("gt.recipe.microwave", "gregtech", "gregtech:gt.blockmachines:311");                       // gt.blockmachines.basicmachine.microwave.tier.01
            addToStackMap("gt.recipe.mixer", "gregtech", "gregtech:gt.blockmachines:581");                           // gt.blockmachines.basicmachine.mixer.tier.01
            addToStackMap("gt.recipe.orewasher", "gregtech", "gregtech:gt.blockmachines:391");                       // gt.blockmachines.basicmachine.orewasher.tier.01
            addToStackMap("gt.recipe.packager", "gregtech", "gregtech:gt.blockmachines:401");                        // gt.blockmachines.basicmachine.boxinator.tier.01
            addToStackMap("gt.recipe.plasmaarcfurnace", "gregtech", "gregtech:gt.blockmachines:661");                // gt.blockmachines.basicmachine.plasmaarcfurnace.tier.01
            addToStackMap("gt.recipe.plasmageneratorfuels", "gregtech", "gregtech:gt.blockmachines:1196");           // gt.blockmachines.basicgenerator.plasmagenerator.tier.05
            addToStackMap("gt.recipe.polarizer", "gregtech", "gregtech:gt.blockmachines:551");                       // gt.blockmachines.basicmachine.polarizer.tier.01
            addToStackMap("gt.recipe.press", "gregtech", "gregtech:gt.blockmachines:601");                           // gt.blockmachines.basicmachine.press.tier.01
            addToStackMap("gt.recipe.primitiveblastfurnace", "gregtech", "gregtech:gt.blockmachines:140");           // gt.blockmachines.multimachine.brickedblastfurnace
            addToStackMap("gt.recipe.printer", "gregtech", "gregtech:gt.blockmachines:321");                         // gt.blockmachines.basicmachine.printer.tier.01
            addToStackMap("gt.recipe.pyro", "gregtech", "gregtech:gt.blockmachines:1159");                           // gt.blockmachines.multimachine.pyro
            addToStackMap("gt.recipe.replicator", "gregtech", "gregtech:gt.blockmachines:481");                      // gt.blockmachines.basicmachine.replicator.tier.01
            addToStackMap("gt.recipe.rockbreaker", "gregtech", "gregtech:gt.blockmachines:441");                     // gt.blockmachines.basicmachine.rockbreaker.tier.01
            addToStackMap("gt.recipe.scanner", "gregtech", "gregtech:gt.blockmachines:341");                         // gt.blockmachines.basicmachine.scanner.tier.01
            addToStackMap("gt.recipe.semifluidboilerfuels", "gregtech", "gregtech:gt.blockmachines:837");            // gt.blockmachines.basicgenerator.semifluid.tier.01
            addToStackMap("gt.recipe.semifluidgeneratorfuels", "gregtech", "gregtech:gt.blockmachines:837");         // gt.blockmachines.basicgenerator.semifluid.tier.01
            addToStackMap("gt.recipe.sifter", "gregtech", "gregtech:gt.blockmachines:641");                          // gt.blockmachines.basicmachine.sifter.tier.01
            addToStackMap("gt.recipe.slicer", "gregtech", "gregtech:gt.blockmachines:631");                          // gt.blockmachines.basicmachine.slicer.tier.01
            addToStackMap("gt.recipe.slowfusionreactor", "gregtech", "gregtech:gt.blockmachines:31015");             // gt.blockmachines.gtplusplus.fusion.single
            addToStackMap("gt.recipe.smallnaquadahreactor", "gregtech", "gregtech:gt.blockmachines:1188");           // gt.blockmachines.basicgenerator.naquadah.tier.07
            addToStackMap("gt.recipe.thermalcentrifuge", "gregtech", "gregtech:gt.blockmachines:381");               // gt.blockmachines.basicmachine.thermalcentrifuge.tier.01
            addToStackMap("gt.recipe.thermalgeneratorfuel", "gregtech", "gregtech:gt.blockmachines:830");            // gt.blockmachines.advancedgenerator.geothermalfuel.tier.01
            addToStackMap("gt.recipe.unpackager", "gregtech", "gregtech:gt.blockmachines:411");                      // gt.blockmachines.basicmachine.unboxinator.tier.01
            addToStackMap("gt.recipe.vacuumfreezer", "gregtech", "gregtech:gt.blockmachines:1002");                  // gt.blockmachines.multimachine.vacuumfreezer
            addToStackMap("gt.recipe.wiremill", "gregtech", "gregtech:gt.blockmachines:351");                        // gt.blockmachines.basicmachine.wiremill.tier.01
            addToStackMap("ic.recipe.recycler", "gregtech", "gregtech:gt.blockmachines:331");                        // gt.blockmachines.basicmachine.recycler.tier.01

//            // Maybe acid generator
//            //tabItemIcon.put("null", stackDB("gt.blockmachines.acidgeneratormv"));
//
//            // Maybe not release
//            tabItemIcon.put("gt.recipe.largenaquadahreactor", stackDB("gt.blockmachines.1nr.controller.single"));
//            tabItemIcon.put("gt.recipe.fluidnaquadahreactor", stackDB("gt.blockmachines.1nr.controller.single"));
//            tabItemIcon.put("gt.recipe.hugenaquadahreactor", stackDB("gt.blockmachines.1nr.controller.single"));
//            tabItemIcon.put("gt.recipe.extrahugenaquadahreactor", stackDB("gt.blockmachines.1nr.controller.single"));
            
        }

        if (Loader.isModLoaded("appliedenergistics2")) {

            addToStackMap("appeng.integration.modules.NEIHelpers.NEIAEShapedRecipeHandler", "AppliedEnergistics2","appliedenergistics2:item.ItemMultiPart:380");
            addToStackMap("appeng.integration.modules.NEIHelpers.NEIAEShapelessRecipeHandler","AppliedEnergistics2", "appliedenergistics2:item.ItemMultiPart:380");
            addToStackMap("appeng.integration.modules.NEIHelpers.NEIFacadeRecipeHandler", "AppliedEnergistics2", "appliedenergistics2:item.ItemFacade:192");
            addToStackMap("appeng.integration.modules.NEIHelpers.NEIInscriberRecipeHandler", "AppliedEnergistics2", "appliedenergistics2:tile.BlockInscriber");
            addToStackMap("appeng.integration.modules.NEIHelpers.NEIWorldCraftingHandler", "AppliedEnergistics2", "appliedenergistics2:item.ItemMultiMaterial:9");

        }

        if (Loader.isModLoaded("AdvancedSolarPanel")) {
            addToStackMap("advsolar.client.nei.MTRecipeHandler", "AdvancedSolarPanel", "AdvancedSolarPanel:BlockMolecularTransformer");
        }
        
        if (Loader.isModLoaded("WitcheryExtras")) {
            addToStackMap("alkalus.main.nei.NEI_Handler_Cauldron", "witchery", "witchery:cauldron");
            addToStackMap("alkalus.main.nei.NEI_Handler_Distillery", "witchery", "witchery:distilleryidle");
            addToStackMap("alkalus.main.nei.NEI_Handler_Kettle", "witchery", "witchery:kettle");
            addToStackMap("alkalus.main.nei.NEI_Handler_Oven", "witchery", "witchery:witchesovenidle");
            addToStackMap("alkalus.main.nei.NEI_Handler_SpinningWheel", "witchery", "witchery:spinningwheel");
        }

        if (Loader.isModLoaded("BuildCraft|Silicon")) {
            addToStackMap("buildcraft.compat.nei.RecipeHandlerAssemblyTable", "BuildCraft", "BuildCraft|Silicon:laserTableBlock");
            addToStackMap("buildcraft.compat.nei.RecipeHandlerIntegrationTable", "BuildCraft", "BuildCraft|Silicon:laserTableBlock:2");
        }
        if (Loader.isModLoaded("BuildCraft|Factory")) {
            addToStackMap("buildcraft.compat.nei.RecipeHandlerRefinery", "BuildCraft", "BuildCraft|Factory:refineryBlock");
        }

        if (Loader.isModLoaded("Thaumcraft")) {
            addToStackMap("com.djgiannuzz.thaumcraftneiplugin.nei.recipehandler.ArcaneShapelessRecipeHandler", "Thaumcraft", "Thaumcraft:blockTable:15");
            addToStackMap("com.djgiannuzz.thaumcraftneiplugin.nei.recipehandler.CrucibleRecipeHandler", "Thaumcraft", "Thaumcraft:blockMetalDevice");
            addToStackMap("com.djgiannuzz.thaumcraftneiplugin.nei.recipehandler.InfusionRecipeHandler", "Thaumcraft", "Thaumcraft:blockStoneDevice:2");
        }

        if (Loader.isModLoaded("bartworks")) {
            addToStackMap("com.github.bartimaeusnek.bartworks.neiHandler.BW_NEI_BioLabHandler", "BartWorks", "gregtech:gt.blockmachines:12699");
            addToStackMap("com.github.bartimaeusnek.bartworks.neiHandler.BW_NEI_BioVatHandler", "BartWorks", "gregtech:gt.blockmachines:12712");
            addToStackMap("com.github.bartimaeusnek.bartworks.neiHandler.BW_NEI_OreHandler", "BartWorks", "bartworks:bw.blockores.01:89");
        }
        if (Loader.isModLoaded("tectech")) {
            addToStackMap("com.github.technus.tectech.nei.TT_NEI_ResearchHandler", "TecTech", "gregtech:gt.blockmachines:15331");
            addToStackMap("com.github.technus.tectech.nei.TT_NEI_ScannerHandler", "TecTech", "tectech:item.em.definitionScanStorage");
        }
        if (Loader.isModLoaded("ExtraUtilities")) {
            addToStackMap("com.rwtema.extrautils.nei.EnderConstructorHandler", "ExtraUtilities", "ExtraUtilities:endConstructor");
            addToStackMap("com.rwtema.extrautils.nei.FMPMicroBlocksHandler", "ExtraUtilities", "ForgeMicroblock:sawIron");
            addToStackMap("com.rwtema.extrautils.nei.InfoHandler", "ExtraUtilities", "ExtraUtilities:chandelier");
            addToStackMap("com.rwtema.extrautils.nei.MicroBlocksHandler", "ExtraUtilities", "ExtraUtilities:microblocks:1");
            addToStackMap("com.rwtema.extrautils.nei.SoulHandler", "ExtraUtilities", "ExtraUtilities:mini-soul");
        }
        if (Loader.isModLoaded("EnderIO")) {
            addToStackMap("crazypants.enderio.nei.AlloySmelterRecipeHandler", "EnderIO", "EnderIO:blockAlloySmelter");
            addToStackMap("crazypants.enderio.nei.EnchanterRecipeHandler", "EnderIO", "EnderIO:blockEnchanter");
            addToStackMap("crazypants.enderio.nei.SagMillRecipeHandler", "EnderIO", "EnderIO:blockSagMill");
            addToStackMap("crazypants.enderio.nei.SliceAndSpliceRecipeHandler", "EnderIO", "EnderIO:blockSliceAndSplice");
            addToStackMap("crazypants.enderio.nei.SoulBinderRecipeHandler", "EnderIO", "EnderIO:blockSoulBinder");
            addToStackMap("crazypants.enderio.nei.VatRecipeHandler", "EnderIO", "EnderIO:blockVat");
        }

        if (Loader.isModLoaded("extracells")) {
            addToStackMap("extracells.integration.nei.UniversalTerminalRecipe", "ExtraCells", "extracells:part.base:9");
        }

        if (Loader.isModLoaded("Forestry")) {
            addToStackMap("forestry.factory.recipes.nei.NEIHandlerBottler", "Forestry", "Forestry:factory");
            addToStackMap("forestry.factory.recipes.nei.NEIHandlerCarpenter", "Forestry", "Forestry:factory:1");
            addToStackMap("forestry.factory.recipes.nei.NEIHandlerCentrifuge", "Forestry", "Forestry:factory:2");
            addToStackMap("forestry.factory.recipes.nei.NEIHandlerFabricator", "Forestry", "Forestry:factory2");
            addToStackMap("forestry.factory.recipes.nei.NEIHandlerFermenter", "Forestry", "Forestry:factory:3");
            addToStackMap("forestry.factory.recipes.nei.NEIHandlerMoistener", "Forestry", "Forestry:factory:4");
            addToStackMap("forestry.factory.recipes.nei.NEIHandlerSqueezer", "Forestry", "Forestry:factory:5");
            addToStackMap("forestry.factory.recipes.nei.NEIHandlerStill", "Forestry", "Forestry:factory:6");
        }
        if (Loader.isModLoaded("Avaritia")) {
            addToStackMap("fox.spiteful.avaritia.compat.nei.CompressionHandler", "Avaritia", "Avaritia:Neutronium_Compressor");
            addToStackMap("fox.spiteful.avaritia.compat.nei.ExtremeShapedRecipeHandler", "Avaritia", "Avaritia:Dire_Crafting");
            addToStackMap("fox.spiteful.avaritia.compat.nei.ExtremeShapelessRecipeHandler", "Avaritia", "Avaritia:Dire_Crafting");
        }


        if (Loader.isModLoaded("GalacticraftCore")) {
            addToStackMap("galaxyspace.core.nei.rocket.RocketT1RecipeHandler", "Galacticraft", "GalacticraftCore:item.spaceship");
        }
        if (Loader.isModLoaded("GalacticraftMars")) {
            addToStackMap("galaxyspace.core.nei.rocket.RocketT2RecipeHandler", "Galacticraft", "GalacticraftMars:item.spaceshipTier2:1");
            addToStackMap("galaxyspace.core.nei.rocket.RocketT3RecipeHandler", "Galacticraft", "GalacticraftMars:item.itemTier3Rocket");
        }
        if (Loader.isModLoaded("GalaxySpace")) {
            addToStackMap("galaxyspace.core.nei.AssemblyMachineRecipeHandler", "GalaxySpace", "GalaxySpace:assemblymachine");
            addToStackMap("galaxyspace.core.nei.rocket.RocketT4RecipeHandler", "GalaxySpace", "GalaxySpace:item.Tier4Rocket");
            addToStackMap("galaxyspace.core.nei.rocket.RocketT5RecipeHandler", "GalaxySpace", "GalaxySpace:item.Tier5Rocket");
            addToStackMap("galaxyspace.core.nei.rocket.RocketT6RecipeHandler", "GalaxySpace", "GalaxySpace:item.Tier6Rocket");
            addToStackMap("galaxyspace.core.nei.rocket.RocketT7RecipeHandler", "GalaxySpace", "GalaxySpace:item.Tier7Rocket");
            addToStackMap("galaxyspace.core.nei.rocket.RocketT8RecipeHandler", "GalaxySpace", "GalaxySpace:item.Tier8Rocket");
        }
        if (Loader.isModLoaded("ProjectBlue")) {
            addToStackMap("gcewing.projectblue.nei.NEIRecipeHandler", "ProjectBlue", "ProjectBlue:sprayCan");
        }

        if (Loader.isModLoaded("miscutils")) {
            addToStackMap("gtPlusPlus.nei.DecayableRecipeHandler", "GT++", "miscutils:dustRadium226");
            addToStackMap("gtPlusPlus.nei.GT_NEI_FlotationCell", "GT++", "gregtech:gt.blockmachines:31028");
            addToStackMap("gtPlusPlus.nei.GT_NEI_FluidReactor", "GT++", "gregtech:gt.blockmachines:998");
            addToStackMap("gtPlusPlus.nei.GT_NEI_MillingMachine", "GT++", "gregtech:gt.blockmachines:31027");
            addToStackMap("gt.recipe.cryogenicfreezer", "GT++", "gregtech:gt.blockmachines:910");
            addToStackMap("gt.recipe.multicentrifuge", "GT++", "gregtech:gt.blockmachines:790");
            addToStackMap("gt.recipe.multielectro", "GT++", "gregtech:gt.blockmachines:796");

            addToStackMap("gt.recipe.fissionfuel", "GT++", "gregtech:gt.blockmachines:835");
            addToStackMap("gt.recipe.vacfurnace", "GT++", "gregtech:gt.blockmachines:995");
            addToStackMap("gt.recipe.lftr", "GT++", "gregtech:gt.blockmachines:751");
            addToStackMap("gt.recipe.advanced.mixer", "GT++", "gregtech:gt.blockmachines:811");
            addToStackMap("gt.recipe.lftr.2", "GT++", "gregtech:gt.blockmachines:751");
            addToStackMap("gt.recipe.alloyblastsmelter", "GT++", "gregtech:gt.blockmachines:810");

            addToStackMap("gt.recipe.geothermalfuel", "GT++", "gregtech:gt.blockmachines:830");

            addToStackMap("gt.recipe.cyclotron", "GT++", "gregtech:gt.blockmachines:828");
            addToStackMap("gt.recipe.simplewasher", "GT++", "gregtech:gt.blockmachines:767");
            addToStackMap("gt.recipe.matterfab2", "GT++", "gregtech:gt.blockmachines:799");
            addToStackMap("gt.recipe.chemicaldehydrator", "GT++", "gregtech:gt.blockmachines:813");
            addToStackMap("gt.recipe.slowfusionreactor", "GT++", "gregtech:gt.blockmachines:31015");
            addToStackMap("gt.recipe.fishpond", "GT++", "gregtech:gt.blockmachines:829");
            addToStackMap("gt.recipe.geothermalfuel", "GT++", "gregtech:gt.blockmachines:830");
            addToStackMap("gt.recipe.cokeoven", "GT++", "gregtech:gt.blockmachines:791");
        }
        if (Loader.isModLoaded("Forestry")) {
            addToStackMap("hellfirepvp.beebetteratbees.client.gui.BBABGuiRecipeTreeHandler", "Bees", "Forestry:beeQueenGE");
        }

        if (Loader.isModLoaded("RandomThings")) {
            addToStackMap("lumien.randomthings.Handler.ModCompability.NEI.ImbuingStationRecipeHandler", "RandomThings", "RandomThings:imbuingStation");
        }

        if (Loader.isModLoaded("GalacticraftCore")) {
            addToStackMap("micdoodle8.mods.galacticraft.core.nei.BuggyRecipeHandler", "Galacticraft", "GalacticraftCore:tile.rocketWorkbench");
            addToStackMap("micdoodle8.mods.galacticraft.core.nei.CircuitFabricatorRecipeHandler", "Galacticraft", "GalacticraftCore:tile.machine2:4");
            addToStackMap("micdoodle8.mods.galacticraft.core.nei.ElectricIngotCompressorRecipeHandler", "Galacticraft", "GalacticraftCore:tile.machine2");
            addToStackMap("micdoodle8.mods.galacticraft.core.nei.IngotCompressorRecipeHandler", "Galacticraft", "GalacticraftCore:tile.machine:12");
            addToStackMap("micdoodle8.mods.galacticraft.core.nei.RefineryRecipeHandler", "Galacticraft", "GalacticraftCore:tile.refinery");
            addToStackMap("micdoodle8.mods.galacticraft.planets.asteroids.nei.AstroMinerRecipeHandler", "Galacticraft", "GalacticraftCore:tile.rocketWorkbench");
            addToStackMap("micdoodle8.mods.galacticraft.planets.mars.nei.CargoRocketRecipeHandler", "Galacticraft", "GalacticraftCore:tile.rocketWorkbench");
            addToStackMap("micdoodle8.mods.galacticraft.planets.mars.nei.GasLiquefierRecipeHandler", "Galacticraft", "GalacticraftMars:tile.marsMachineT2");
            addToStackMap("micdoodle8.mods.galacticraft.planets.mars.nei.MethaneSynthesizerRecipeHandler", "Galacticraft", "GalacticraftMars:tile.marsMachineT2:4");
        }
        if (Loader.isModLoaded("ProjRed|Transmission")) {
            addToStackMap("mrtjp.projectred.core.libmc.recipe.PRShapedRecipeHandler", "ProjectRed", "ProjRed|Transmission:projectred.transmission.wire:7");
            addToStackMap("mrtjp.projectred.core.libmc.recipe.PRShapelessRecipeHandler", "ProjectRed", "ProjRed|Transmission:projectred.transmission.wire:7");
        }

        if (Loader.isModLoaded("gendustry")) {
            addToStackMap("net.bdew.gendustry.nei.ExtractorHandler", "Gendustry", "gendustry:Extractor");
            addToStackMap("net.bdew.gendustry.nei.ImprinterHandler", "Gendustry", "gendustry:Imprinter");
            addToStackMap("net.bdew.gendustry.nei.LiquifierHandler", "Gendustry", "gendustry:Liquifier");
            addToStackMap("net.bdew.gendustry.nei.MutagenProducerHandler", "Gendustry", "gendustry:MutagenProducer");
            addToStackMap("net.bdew.gendustry.nei.MutatronHandler", "Gendustry", "gendustry:Mutatron");
            addToStackMap("net.bdew.gendustry.nei.ReplicatorHandler", "Gendustry", "gendustry:Replicator");
            addToStackMap("net.bdew.gendustry.nei.SamplerHandler", "Gendustry", "gendustry:Sampler");
            addToStackMap("net.bdew.gendustry.nei.TemplateCraftingHandler", "Gendustry", "gendustry:GeneTemplate");
            addToStackMap("net.bdew.gendustry.nei.TransposerHandler", "Gendustry", "gendustry:Transposer");
        }

        if (Loader.isModLoaded("Botany")) {
            addToStackMap("net.bdew.neiaddons.botany.flowers.FlowerBreedingHandler", "Botany", "Botany:flower");
        }

        if (Loader.isModLoaded("Forestry")) {
            addToStackMap("net.bdew.neiaddons.forestry.bees.BeeBreedingHandler", "Forestry", "Forestry:beeQueenGE");
            addToStackMap("net.bdew.neiaddons.forestry.bees.BeeProduceHandler", "Forestry", "Forestry:beeQueenGE");
            addToStackMap("net.bdew.neiaddons.forestry.butterflies.ButterflyBreedingHandler", "Forestry", "Forestry:butterflyGE");
            addToStackMap("net.bdew.neiaddons.forestry.trees.TreeBreedingHandler", "Forestry", "Forestry:saplingGE");
            addToStackMap("net.bdew.neiaddons.forestry.trees.TreeProduceHandler", "Forestry", "Forestry:saplingGE");

        }
        if (Loader.isModLoaded("ae2wct")) {
            addToStackMap("net.p455w0rd.wirelesscraftingterminal.integration.modules.NEIHelpers.NEIAEShapedRecipeHandler", "WirelessCraftingTerminal", "ae2wct:wirelessCraftingTerminal");
        }
        if (Loader.isModLoaded("gregtech")) {
            addToStackMap("pers.gwyog.gtneioreplugin.plugin.gregtech5.PluginGT5SmallOreStat", "Gregtech", "gregtech:gt.blockores:85");
            addToStackMap("pers.gwyog.gtneioreplugin.plugin.gregtech5.PluginGT5VeinStat", "Gregtech", "gregtech:gt.blockores:386");
        }
        
        if (Loader.isModLoaded("Thaumcraft")) {
            addToStackMap("ru.timeconqueror.tcneiadditions.nei.arcaneworkbench.ArcaneCraftingShapedHandler", "Thaumcraft", "Thaumcraft:blockTable:15");
            addToStackMap("ru.timeconqueror.tcneiadditions.nei.AspectCombinationHandler", "Thaumcraft", "Thaumcraft:ItemResearchNotes");
            addToStackMap("ru.timeconqueror.tcneiadditions.nei.AspectFromItemStackHandler", "Thaumcraft", "Thaumcraft:ItemResearchNotes");
        }

        if (Loader.isModLoaded("TConstruct")) {
            addToStackMap("tconstruct.plugins.nei.RecipeHandlerAlloying", "Tinker's Construct", "TConstruct:Smeltery");
            addToStackMap("tconstruct.plugins.nei.RecipeHandlerCastingBasin", "Tinker's Construct", "TConstruct:SearedBlock:2");
            addToStackMap("tconstruct.plugins.nei.RecipeHandlerCastingTable", "Tinker's Construct", "TConstruct:SearedBlock");
            addToStackMap("tconstruct.plugins.nei.RecipeHandlerDryingRack", "Tinker's Construct", "TConstruct:Armor.DryingRack");
            addToStackMap("tconstruct.plugins.nei.RecipeHandlerMelting", "Tinker's Construct", "TConstruct:LavaTank:2");
            addToStackMap("tconstruct.plugins.nei.RecipeHandlerToolMaterials", "Tinker's Construct", "TConstruct:pickaxe");
        }
        if (Loader.isModLoaded("chisel")) {
            addToStackMap("team.chisel.compat.nei.RecipeHandlerChisel", "Chisel", "chisel:chisel");
        }

        if (Loader.isModLoaded("harvestcraft")) {
            addToStackMap("tonius.neiintegration.mods.harvestcraft.RecipeHandlerApiary", "Pam's Harvestcraft", "harvestcraft:apiary");
            addToStackMap("tonius.neiintegration.mods.harvestcraft.RecipeHandlerChurn", "Pam's Harvestcraft", "harvestcraft:churn");
            addToStackMap("tonius.neiintegration.mods.harvestcraft.RecipeHandlerOven", "Pam's Harvestcraft", "harvestcraft:oven");
            addToStackMap("tonius.neiintegration.mods.harvestcraft.RecipeHandlerPresser", "Pam's Harvestcraft", "harvestcraft:presser");
            addToStackMap("tonius.neiintegration.mods.harvestcraft.RecipeHandlerQuern", "Pam's Harvestcraft", "harvestcraft:quern");
        }
        if (Loader.isModLoaded("harvestcraft")) {
            addToStackMap("tonius.neiintegration.mods.mcforge.RecipeHandlerFluidRegistry", "Minecraft", "minecraft:water");
            addToStackMap("tonius.neiintegration.mods.mcforge.RecipeHandlerOreDictionary", "Minecraft", "minecraft:iron_ore");
        }

        if (Loader.isModLoaded("GalacticraftCoRailcraftre")) {
            addToStackMap("tonius.neiintegration.mods.railcraft.RecipeHandlerBlastFurnace", "Railcraft", "Railcraft:machine.alpha:12");
            addToStackMap("tonius.neiintegration.mods.railcraft.RecipeHandlerCokeOven", "Railcraft", "Railcraft:machine.alpha:7");
            addToStackMap("tonius.neiintegration.mods.railcraft.RecipeHandlerRockCrusher", "Railcraft", "Railcraft:machine.alpha:15");
            addToStackMap("tonius.neiintegration.mods.railcraft.RecipeHandlerRollingMachineShaped", "Railcraft", "Railcraft:machine.alpha:8");
            addToStackMap("tonius.neiintegration.mods.railcraft.RecipeHandlerRollingMachineShapeless", "Railcraft", "Railcraft:machine.alpha:8");
        }

        if (Loader.isModLoaded("AWWayofTime")) {
            addToStackMap("WayofTime.alchemicalWizardry.client.nei.NEIAlchemyRecipeHandler", "BloodMagic", "AWWayofTime:blockWritingTable");
            addToStackMap("WayofTime.alchemicalWizardry.client.nei.NEIAltarRecipeHandler", "BloodMagic", "AWWayofTime:Altar");
            addToStackMap("WayofTime.alchemicalWizardry.client.nei.NEIBindingRitualHandler", "BloodMagic", "AWWayofTime:sacrificialKnife");
            addToStackMap("WayofTime.alchemicalWizardry.client.nei.NEIBloodOrbShapedHandler", "BloodMagic", "AWWayofTime:AlchemicalWizardrybloodRune:3");
            addToStackMap("WayofTime.alchemicalWizardry.client.nei.NEIBloodOrbShapelessHandler", "BloodMagic", "AWWayofTime:AlchemicalWizardrybloodRune:4");
        }

        if (Loader.isModLoaded("WitchingGadgets")) {
            addToStackMap("witchinggadgets.client.nei.NEIInfernalBlastfurnaceHandler", "WitchingGadgets", "WitchingGadgets:WG_StoneDevice:2");
            addToStackMap("witchinggadgets.client.nei.NEISpinningWheelHandler", "WitchingGadgets", "WitchingGadgets:WG_WoodenDevice");
        }
        /* Witchery */
        {
            addToStackMap("com.emoniph.witchery.integration.NEICauldronRecipeHandler", "witchery", "witchery:cauldron");
            addToStackMap("com.emoniph.witchery.integration.NEISpinningWheelRecipeHandler", "witchery", "witchery:spinningwheel");
            addToStackMap("com.emoniph.witchery.integration.NEIWitchesOvenRecipeHandler", "witchery", "witchery:witchesovenidle");
            addToStackMap("com.emoniph.witchery.integration.NEIDistilleryRecipeHandler", "witchery", "witchery:distilleryidle");
            addToStackMap("com.emoniph.witchery.integration.NEIKettleRecipeHandler", "witchery", "witchery:kettle");
        }
        /* Witchery Extras */
        if (Loader.isModLoaded("WitcheryExtras")) {
            addToStackMap("alkalus.main.nei.NEI_Handler_Cauldron ", "witchery", "witchery:cauldron");
            addToStackMap("alkalus.main.nei.NEI_Handler_Distillery ", "witchery", "witchery:spinningwheel");
            addToStackMap("alkalus.main.nei.NEI_Handler_Kettle ", "witchery", "witchery:witchesovenidle");
            addToStackMap("alkalus.main.nei.NEI_Handler_Oven", "witchery", "witchery:distilleryidle");
            addToStackMap("alkalus.main.nei.NEI_Handler_SpinningWheel ", "witchery", "witchery:kettle");
        }
        

    }

    private static void addToStackMap(String handler, String modname, String item_id) {
        final ItemStack itemStack;
        int meta = -1;
        if (!item_id.contains(":")) {
            throw new IllegalArgumentException("Item ID missing colon");
        }
        final String[] split = item_id.split(":");
        item_id = split[0] + ":" + split[1];

        if(split.length >= 3) {
            int mIdx = -1;
            if (split[2].matches("\\d+")) {
                mIdx = 2;
            } else {
                item_id += ":" + split[2];
                if (split.length >= 4) mIdx = 3;
            }
            if (mIdx != -1) meta = Integer.parseInt(split[mIdx]);
        }

        if (meta != -1) {
            itemStack = GameRegistry.makeItemStack(item_id, meta, 1, "");
        } else {
            itemStack = GameRegistry.findItemStack(split[0], split[1], 1);
        }
        if (itemStack != null)
            stackMap.put(handler, new ImmutablePair<>(modname, itemStack));
        else
            System.out.println("Couldn't find " + modname + " - " + item_id);
    }
    private static void addToIconMap(String handler, String modname, DrawableResource image) {
        imageMap.put(handler, new ImmutablePair<>(modname, image));
    }
}
