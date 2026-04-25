package codechicken.nei.commands;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
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
import codechicken.nei.recipe.TemplateRecipeHandler;
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
        protected final boolean fastDump;

        public ProcessDumpThread(ICommandSender sender, File currFile, boolean fastDump) {
            this.sender = sender;
            this.currFile = currFile;
            this.fastDump = fastDump;

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
                if (fastDump) {
                    processFastDump(writer);
                } else {
                    processDump(writer);
                }

            } catch (Exception e) {
                NEIClientConfig.logger.error("Error dumping RecipeId", e);
            }

            NEIClientConfig.logger.info("Finished processing recipe handlers!");
            sendChatInfoMessage(sender, "nei.chat.recipeid.dump.finish");
        }

        /**
         * + technically more reliable and supports some weird handlers
         * <p>
         * - very very very slow, took 1 hour to get a dump in GTNH on M1 in background
         * <p>
         * - generates a lot of duplicates, up to 75% of file size is duplicates (and it's 300mb out of 400mb...)
         */
        private void processDump(BufferedWriter writer) throws IOException {
            final int total = ItemList.items.size();
            int count = 0;

            for (ItemStack stack : ItemList.items) {
                if (count % 1000 == 0) {
                    NEIClientConfig.logger
                            .info("({}/{}). Processing {} crafting recipes...", count, total, stack.getDisplayName());
                }

                count++;

                for (ICraftingHandler handler : getCraftingHandlers(stack)) {
                    writeLines(writer, collectRecipeLines(handler, stack.toString()));
                }
            }
        }

        /**
         * - may skip some handlers if they're not TemplateRecipeHandler / just weird ones (like gendustry handlers)
         * <p>
         * + but in terms of GT recipes everything is dumped, and surprisingly it catches even more GT recipes than the
         * regular dumper (in particular, it has a lot of new hammer, extruder, blast furnace, etc. recipes)
         * <p>
         * + way faster, took like 2.5 minutes
         * <p>
         * + doesn't have nearly as many duplicates, but something can still slip in, for example from
         * FuelRecipeHandler. The dump has a size of only 100 MB instead of 400 MB from the regular dumper
         */
        private void processFastDump(BufferedWriter writer) throws IOException {
            final ArrayList<ICraftingHandler> handlers = getCraftingHandlers();
            final int total = handlers.size();
            final AtomicInteger count = new AtomicInteger();

            try {
                final List<List<String>> linesByHandler = ItemList.forkJoinPool.submit(() -> {
                    return handlers.parallelStream().map(handler -> {
                        final String handlerName = GuiRecipeTab.getHandlerInfo(handler).getHandlerName();
                        final List<String> lines = collectRecipeLines(handler, handlerName);
                        final int current = count.incrementAndGet();
                        NEIClientConfig.logger
                                .info("({}/{}). Processed {} handler recipes.", current, total, handlerName);
                        return lines;
                    }).collect(Collectors.toList());
                }).get();

                for (List<String> lines : linesByHandler) {
                    writeLines(writer, lines);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while dumping RecipeId", e);
            } catch (ExecutionException e) {
                throw new IOException("Error while dumping RecipeId", e);
            }
        }

        private List<String> collectRecipeLines(ICraftingHandler handler, String context) {
            final List<String> lines = new ArrayList<>(handler.numRecipes());

            for (int index = 0, num = handler.numRecipes(); index < num; index++) {
                try {
                    final Recipe recipe = Recipe.of(handler, index);
                    if (!recipe.getIngredients().isEmpty() && !recipe.getResults().isEmpty()) {
                        lines.add(NBTJson.toJson(recipe.getRecipeId().toJsonObject()));
                    }
                } catch (Exception ex) {
                    NEIClientConfig.logger.error(
                            "Found Broken RecipeId {}:{}",
                            GuiRecipeTab.getHandlerInfo(handler).getHandlerName(),
                            context,
                            ex);
                }
            }

            return lines;
        }

        private void writeLines(BufferedWriter writer, List<String> lines) throws IOException {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }

        private ArrayList<ICraftingHandler> getCraftingHandlers(Object... results) {
            return new RecipeHandlerQuery<>(
                    handler -> handler.getRecipeHandler("item", results),
                    this.craftinghandlers,
                    this.serialCraftingHandlers,
                    "Error while looking up crafting recipe",
                    "outputId: item").runWithProfiling(NEIClientUtils.translate("recipe.concurrent.crafting"));
        }

        private ArrayList<ICraftingHandler> getCraftingHandlers() {
            return new RecipeHandlerQuery<>(handler -> {
                if (handler instanceof TemplateRecipeHandler templateHandler) {
                    return templateHandler.getAllRecipeHandler();
                }
                return handler.getRecipeHandler("all");
            },
                    this.craftinghandlers,
                    this.serialCraftingHandlers,
                    "Error while looking up crafting recipe",
                    "outputId: all").runWithProfiling(NEIClientUtils.translate("recipe.concurrent.crafting"));
        }

    }

    @Override
    public String getCommandName() {
        return "recipeid";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/recipeid [dump|fast-dump] <filename> OR /recipeid diff <prev-filename> <curr-filename> <subset name>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "dump", "fast-dump", "diff");
        }

        if ("diff".equals(args[0]) && (args.length == 2 || args.length == 3)) {
            return getListOfStringsMatchingLastWord(args, getRecipeIdFilenames());
        }

        return Collections.emptyList();
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        final String command = args.length == 0 ? null : args[0];

        if ("dump".equals(command)) {
            processDumpCommand(sender, args, false);
        } else if ("fast-dump".equals(command)) {
            processDumpCommand(sender, args, true);
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

    protected void processDumpCommand(ICommandSender sender, String[] args, boolean fastDump) {

        if (args.length > 2) {
            sendChatErrorMessage(sender, "nei.chat.recipeid.many_params", getCommandUsage(sender));
            return;
        }

        final File dir = new File(CommonUtils.getMinecraftDir(), "recipeid");
        final String currFilename = args.length == 2 ? args[1] : "recipeId";
        if (!dir.exists()) dir.mkdirs();

        (new ProcessDumpThread(sender, getFile(currFilename), fastDump)).start();
    }

    private static File getFile(String filename) {
        return new File(CommonUtils.getMinecraftDir(), "recipeid/" + filename + ".json");
    }

    private static String[] getRecipeIdFilenames() {
        final File dir = new File(CommonUtils.getMinecraftDir(), "recipeid");
        final File[] files = dir.listFiles((currentDir, name) -> name.endsWith(".json"));

        if (files == null || files.length == 0) {
            return new String[] {};
        }

        final String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            final String name = files[i].getName();
            names[i] = name.substring(0, name.length() - ".json".length());
        }

        return names;
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
