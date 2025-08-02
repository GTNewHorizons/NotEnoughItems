package codechicken.nei.commands;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import org.apache.commons.io.IOUtils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import codechicken.core.CommonUtils;
import codechicken.nei.ClientHandler;
import codechicken.nei.ItemList;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.util.NBTJson;

public class CommandRecipeId extends CommandBase {

    protected static class ProcessDiffThread extends Thread {

        protected final ICommandSender sender;
        protected final File prevFile;
        protected final File currFile;
        protected final File diffFile;

        public ProcessDiffThread(ICommandSender sender, File prevFile, File currFile, File diffFile) {
            this.sender = sender;
            this.prevFile = prevFile;
            this.currFile = currFile;
            this.diffFile = diffFile;
        }

        @Override
        public void run() {
            final Set<String> prevRecipes = loadFileContent(this.prevFile);
            final Set<String> currRecipes = loadFileContent(this.currFile);

            if (prevRecipes == null || currRecipes == null) {
                return;
            }

            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "Generate subset diff!"));

            final Set<String> notAllowedRecipes = prevRecipes.stream().filter(recipe -> !currRecipes.contains(recipe))
                    .collect(Collectors.toSet());
            final List<String> subsetsList = new ArrayList<>();

            for (Map.Entry<String, Set<String>> entry : generateSubsets(notAllowedRecipes).entrySet()) {
                subsetsList.add("; " + entry.getKey());
                subsetsList.addAll(entry.getValue());
            }

            saveFile(this.diffFile, subsetsList);
            saveFile(getFile("not-allowed-recipes"), new ArrayList<>(notAllowedRecipes));

            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "Finished processing recipe diff!"));
        }

        private Set<String> loadFileContent(File file) {
            try (FileReader reader = new FileReader(file)) {
                return new HashSet<>(IOUtils.readLines(reader));
            } catch (IOException e) {
                NEIClientConfig.logger.error("Failed to load '{}' file {}", file.getName(), file, e);
            }
            return null;
        }

        private Map<String, Set<String>> generateSubsets(Set<String> notAllowedRecipes) {
            final JsonParser parser = new JsonParser();
            final Map<String, Set<String>> subsetsBuilder = new HashMap<>();

            for (String recipe : notAllowedRecipes) {
                try {
                    final RecipeId recipeId = RecipeId.of((JsonObject) parser.parse(recipe));
                    final NBTTagCompound nbtStack = StackInfo.itemStackToNBT(recipeId.getResult(), false);
                    nbtStack.removeTag("Count");

                    subsetsBuilder.computeIfAbsent(recipeId.getHandleName(), rn -> new HashSet<>())
                            .add(NBTJson.toJson(nbtStack));
                } catch (Exception ex) {
                    NEIClientConfig.logger.error("Found Blocken RecipeId {}", recipe, ex);
                }
            }

            return subsetsBuilder;
        }

        private void saveFile(File file, List<String> content) {
            try (FileOutputStream output = new FileOutputStream(file)) {
                IOUtils.writeLines(content, "\n", output, StandardCharsets.UTF_8);
            } catch (IOException e) {
                NEIClientConfig.logger.error("Filed to save recipeid diff list to file {}", file, e);
            }
        }

    }

    protected static class ProcessDumpThread extends Thread {

        private Set<String> blacklist;
        protected final ICommandSender sender;
        protected final File currFile;

        public ProcessDumpThread(ICommandSender sender, File currFile) {
            this.sender = sender;
            this.currFile = currFile;
            ClientHandler
                    .loadSettingsFile("hiddenhandlers.cfg", lines -> blacklist = lines.collect(Collectors.toSet()));
        }

        @Override
        public void run() {
            NEIClientConfig.logger.info("Start processing recipe handlers!");
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.AQUA + "Start processing recipe handlers!"));

            try {
                if (!this.currFile.exists()) this.currFile.createNewFile();

                final PrintWriter recipes = new PrintWriter(this.currFile);
                int total = ItemList.items.size();
                int count = 0;

                for (ItemStack stack : ItemList.items) {

                    if ((count % 100) == 0) {
                        NEIClientConfig.logger.info(
                                "({}/{}). Processing {} crafting recipes...",
                                count++,
                                total,
                                stack.getDisplayName());
                    }

                    count++;

                    for (ICraftingHandler handler : GuiCraftingRecipe.getCraftingHandlers("item", stack)) {
                        if (!blacklist.contains(GuiRecipeTab.getHandlerInfo(handler).getHandlerName())) {
                            for (int index = 0; index < handler.numRecipes(); index++) {
                                try {
                                    final Recipe recipe = Recipe.of(handler, index);
                                    if (recipe != null) {
                                        recipes.println(NBTJson.toJson(recipe.getRecipeId().toJsonObject()));
                                    }
                                } catch (Exception ex) {
                                    NEIClientConfig.logger.error(
                                            "Found Blocken RecipeId {}:{}",
                                            GuiRecipeTab.getHandlerInfo(handler).getHandlerName(),
                                            stack,
                                            ex);
                                }
                            }
                        }
                    }
                }

                recipes.close();
            } catch (Exception e) {
                NEIClientConfig.logger.error("Error dumping RecipeId", e);
            }

            NEIClientConfig.logger.info("Finished processing recipe handlers!");
            sender.addChatMessage(
                    new ChatComponentText(EnumChatFormatting.AQUA + "Finished processing recipe handlers!"));
        }

    }

    @Override
    public String getCommandName() {
        return "recipeid";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/recipeid dump <filename> OR /recipeid diff <prev-filename> <curr-filename> [subset name]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        final String command = args.length == 0 ? null : args[0];

        if ("dump".equals(command)) {
            processDumpCommand(sender, args);
        } else if ("diff".equals(command)) {
            processDiffCommand(sender, args);
        } else {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + getCommandUsage(sender)));
        }

    }

    protected void processDiffCommand(ICommandSender sender, String[] args) {

        if (args.length > 4) {
            sender.addChatMessage(new ChatComponentText("Too many parameters! Usage: " + getCommandUsage(sender)));
            return;
        }

        if (args.length < 3) {
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + getCommandUsage(sender)));
            return;
        }

        final File prevFilename = getFile(args[1]);
        final File currFilename = getFile(args[2]);
        final File diffFilename = getFile(args.length > 3 ? args[3] : "subsets");

        if (!prevFilename.exists()) {
            sender.addChatMessage(
                    new ChatComponentText("File `" + args[1] + "` not found! Usage: " + getCommandUsage(sender)));
            return;
        }

        if (!currFilename.exists()) {
            sender.addChatMessage(
                    new ChatComponentText("File `" + args[2] + "` not found! Usage: " + getCommandUsage(sender)));
            return;
        }

        (new ProcessDiffThread(sender, prevFilename, currFilename, diffFilename)).start();
    }

    protected void processDumpCommand(ICommandSender sender, String[] args) {
        if (args.length > 2) {
            sender.addChatMessage(new ChatComponentText("Too many parameters! Usage: " + getCommandUsage(sender)));
            return;
        }

        final File dir = new File(CommonUtils.getMinecraftDir(), "recipeid");
        final String currFilename = args.length == 2 ? args[1] : "recipeId";
        if (!dir.exists()) dir.mkdirs();

        (new ProcessDumpThread(sender, getFile(currFilename))).start();
    }

    private static File getFile(String filename) {
        return new File(CommonUtils.getMinecraftDir(), "recipeid/" + filename + ".json");
    }

}
