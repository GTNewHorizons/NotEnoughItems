package codechicken.nei.commands;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;

import org.apache.commons.io.IOUtils;

import com.google.gson.JsonParser;

import codechicken.core.CommonUtils;
import codechicken.nei.ClientHandler;
import codechicken.nei.ItemList;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.RecipeHandlerQuery;
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
            sendChatInfoMessage(sender, "nei.chat.recipeid.diff.start");

            final Set<String> prevRecipes = loadFileContent(this.prevFile);
            final Set<String> currRecipes = loadFileContent(this.currFile);
            final Set<String> notAllowedRecipes = prevRecipes.stream().filter(recipe -> !currRecipes.contains(recipe))
                    .collect(Collectors.toCollection(TreeSet::new));
            final List<String> subsetsList = new ArrayList<>();

            generateSubsets(notAllowedRecipes).forEach((handlerName, recipes) -> {
                subsetsList.add("; " + handlerName);
                subsetsList.addAll(recipes);
            });

            saveFile(this.diffFile, subsetsList);
            saveFile(getFile("not-allowed-recipes"), new ArrayList<>(notAllowedRecipes));

            sendChatInfoMessage(sender, "nei.chat.recipeid.diff.finish");
        }

        private Set<String> loadFileContent(File file) {
            try (FileReader reader = new FileReader(file)) {
                return new HashSet<>(IOUtils.readLines(reader));
            } catch (IOException e) {
                NEIClientConfig.logger.error("Failed to load '{}' file {}", file.getName(), file, e);
            }
            return new HashSet<>();
        }

        private Map<String, Set<String>> generateSubsets(Set<String> notAllowedRecipes) {
            final JsonParser parser = new JsonParser();
            final Map<String, Set<String>> subsetsBuilder = new TreeMap<>();

            for (String recipeStr : notAllowedRecipes) {
                try {
                    final NBTTagCompound nbtRecipe = (NBTTagCompound) NBTJson.toNbt(parser.parse(recipeStr));

                    if (nbtRecipe.hasKey("result")) {
                        final NBTTagCompound nbtStack = nbtRecipe.getCompoundTag("result");
                        nbtStack.removeTag("Count");
                        subsetsBuilder.computeIfAbsent(nbtRecipe.getString("handlerName"), rn -> new TreeSet<>())
                                .add(NBTJson.toJson(nbtStack));
                    } else {
                        NEIClientConfig.logger.error("Found Broken RecipeId {}", recipeStr);
                    }
                } catch (Exception ex) {
                    NEIClientConfig.logger.error("Found Broken RecipeId {}", recipeStr, ex);
                }
            }

            return subsetsBuilder;
        }

        private void saveFile(File file, List<String> content) {
            try (FileOutputStream output = new FileOutputStream(file)) {
                IOUtils.writeLines(content, "\n", output, StandardCharsets.UTF_8);
            } catch (IOException e) {
                NEIClientConfig.logger.error("Failed to save recipeid diff list to file {}", file, e);
            }
        }

    }

    protected static class ProcessDumpThread extends Thread {

        private ArrayList<ICraftingHandler> craftinghandlers = new ArrayList<>();
        private ArrayList<ICraftingHandler> serialCraftingHandlers = new ArrayList<>();
        protected final ICommandSender sender;
        protected final File currFile;

        public ProcessDumpThread(ICommandSender sender, File currFile) {
            this.sender = sender;
            this.currFile = currFile;
            ClientHandler.loadSettingsFile("recipeidblacklist.cfg", lines -> {
                final Set<String> names = lines.collect(Collectors.toSet());

                this.craftinghandlers = GuiCraftingRecipe.craftinghandlers.stream()
                        .filter(h -> !names.contains(GuiRecipeTab.getHandlerInfo(h).getHandlerName()))
                        .collect(Collectors.toCollection(ArrayList::new));

                this.serialCraftingHandlers = GuiCraftingRecipe.serialCraftingHandlers.stream()
                        .filter(h -> !names.contains(GuiRecipeTab.getHandlerInfo(h).getHandlerName()))
                        .collect(Collectors.toCollection(ArrayList::new));
            });
        }

        @Override
        public void run() {
            NEIClientConfig.logger.info("Start processing recipe handlers!");
            sendChatInfoMessage(sender, "nei.chat.recipeid.dump.start");

            try (BufferedWriter writer = Files.newBufferedWriter(currFile.toPath(), StandardCharsets.UTF_8)) {
                int total = ItemList.items.size();
                int count = 0;

                for (ItemStack stack : ItemList.items) {

                    if (count % 1000 == 0) {
                        NEIClientConfig.logger.info(
                                "({}/{}). Processing {} crafting recipes...",
                                count,
                                total,
                                stack.getDisplayName());
                    }

                    count++;

                    for (ICraftingHandler handler : getCraftingHandlers(stack)) {
                        for (int index = 0, num = handler.numRecipes(); index < num; index++) {
                            try {
                                final Recipe recipe = Recipe.of(handler, index);
                                if (!recipe.getIngredients().isEmpty() && !recipe.getResults().isEmpty()) {
                                    writer.write(NBTJson.toJson(recipe.getRecipeId().toJsonObject()));
                                    writer.newLine();
                                }
                            } catch (Exception ex) {
                                NEIClientConfig.logger.error(
                                        "Found Broken RecipeId {}:{}",
                                        GuiRecipeTab.getHandlerInfo(handler).getHandlerName(),
                                        stack,
                                        ex);
                            }
                        }
                    }
                }

            } catch (Exception e) {
                NEIClientConfig.logger.error("Error dumping RecipeId", e);
            }

            NEIClientConfig.logger.info("Finished processing recipe handlers!");
            sendChatInfoMessage(sender, "nei.chat.recipeid.dump.finish");
        }

        private ArrayList<ICraftingHandler> getCraftingHandlers(Object... results) {
            return new RecipeHandlerQuery<>(
                    h -> h.getRecipeHandler("item", results),
                    this.craftinghandlers,
                    this.serialCraftingHandlers,
                    "Error while looking up crafting recipe",
                    "outputId: item",
                    "results: " + Arrays.toString(results))
                            .runWithProfiling(NEIClientUtils.translate("recipe.concurrent.crafting"));
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
            sendChatErrorMessage(sender, getCommandUsage(sender));
        }

    }

    protected void processDiffCommand(ICommandSender sender, String[] args) {

        if (args.length > 4) {
            sendChatErrorMessage(sender, "nei.chat.recipeid.many_params", getCommandUsage(sender));
            return;
        }

        if (args.length < 3) {
            sendChatErrorMessage(sender, getCommandUsage(sender));
            return;
        }

        final File prevFilename = getFile(args[1]);
        final File currFilename = getFile(args[2]);
        final File diffFilename = getFile(args.length > 3 ? args[3] : "changelog");

        if (!prevFilename.exists()) {
            sendChatErrorMessage(sender, "nei.chat.recipeid.not_found", prevFilename.getName());
            return;
        }

        if (!currFilename.exists()) {
            sendChatErrorMessage(sender, "nei.chat.recipeid.not_found", currFilename.getName());
            return;
        }

        (new ProcessDiffThread(sender, prevFilename, currFilename, diffFilename)).start();
    }

    protected void processDumpCommand(ICommandSender sender, String[] args) {

        if (args.length > 2) {
            sendChatErrorMessage(sender, "nei.chat.recipeid.many_params", getCommandUsage(sender));
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

    private static void sendChatInfoMessage(ICommandSender sender, String translationKey, Object... args) {
        sender.addChatMessage(
                new ChatComponentTranslation(translationKey, args)
                        .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA)));
    }

    private static void sendChatErrorMessage(ICommandSender sender, String translationKey, Object... args) {
        sender.addChatMessage(
                new ChatComponentTranslation(translationKey, args)
                        .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED)));
    }

}
