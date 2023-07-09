package codechicken.nei.recipe;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import com.google.common.base.Objects;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import cpw.mods.fml.common.Loader;

public class RecipeCatalysts {

    private static final Map<String, CatalystInfoList> catalystsAdderFromAPI = new HashMap<>();
    private static final Map<String, List<ItemStack>> catalystsRemoverFromAPI = new HashMap<>();
    public static final Map<String, CatalystInfoList> catalystsAdderFromIMC = new HashMap<>();
    public static final Map<String, List<ItemStack>> catalystsRemoverFromIMC = new HashMap<>();
    private static final Map<String, CatalystInfoList> recipeCatalystMap = new HashMap<>();
    private static Map<String, List<PositionedStack>> positionedRecipeCatalystMap = new HashMap<>();
    private static final List<String> forceClassNameList = new ArrayList<>();
    private static int heightCache;

    public static void addRecipeCatalyst(String catalystHandlerID, CatalystInfo catalystInfo) {
        if (catalystHandlerID == null || catalystHandlerID.isEmpty() || catalystInfo.getStack() == null) return;
        addOrPut(catalystsAdderFromAPI, catalystHandlerID, catalystInfo);
    }

    public static void removeRecipeCatalyst(String catalystHandlerID, ItemStack stack) {
        if (catalystHandlerID == null || catalystHandlerID.isEmpty() || stack == null) return;
        if (catalystsRemoverFromAPI.containsKey(catalystHandlerID)) {
            catalystsRemoverFromAPI.get(catalystHandlerID).add(stack);
        } else {
            catalystsRemoverFromAPI.put(catalystHandlerID, new ArrayList<>(Collections.singletonList(stack)));
        }
    }

    public static Map<String, List<PositionedStack>> getPositionedRecipeCatalystMap() {
        return positionedRecipeCatalystMap;
    }

    public static List<PositionedStack> getRecipeCatalysts(IRecipeHandler handler) {
        return getRecipeCatalysts(getCatalystHandlerId(handler));
    }

    public static List<PositionedStack> getRecipeCatalysts(String catalystHandlerID) {
        if (!NEIClientConfig.areJEIStyleTabsVisible() || !NEIClientConfig.areJEIStyleRecipeCatalystsVisible()) {
            return Collections.emptyList();
        }
        return positionedRecipeCatalystMap.getOrDefault(catalystHandlerID, Collections.emptyList());
    }

    public static boolean containsCatalyst(IRecipeHandler handler, ItemStack candidate) {
        return containsCatalyst(getCatalystHandlerId(handler), candidate);
    }

    public static boolean containsCatalyst(String catalystHandlerID, ItemStack candidate) {
        if (recipeCatalystMap.containsKey(catalystHandlerID)) {
            return recipeCatalystMap.get(catalystHandlerID).contains(candidate);
        }
        return false;
    }

    public static void updatePosition(int availableHeight, boolean force) {
        if (availableHeight == heightCache && !force) return;

        Map<String, List<PositionedStack>> newMap = new HashMap<>();
        for (Map.Entry<String, CatalystInfoList> entry : recipeCatalystMap.entrySet()) {
            CatalystInfoList catalysts = entry.getValue();
            catalysts.sort();
            List<PositionedStack> newStacks = new ArrayList<>();
            int rowCount = getRowCount(availableHeight, catalysts.size());

            for (int index = 0; index < catalysts.size(); index++) {
                ItemStack catalyst = catalysts.get(index).getStack();
                int column = index / rowCount;
                int row = index % rowCount;
                newStacks.add(
                        new PositionedStack(
                                catalyst,
                                -column * GuiRecipeCatalyst.ingredientSize,
                                row * GuiRecipeCatalyst.ingredientSize));
            }
            newMap.put(entry.getKey(), newStacks);
        }
        positionedRecipeCatalystMap = newMap;
        heightCache = availableHeight;
    }

    public static void updatePosition(int availableHeight) {
        updatePosition(availableHeight, false);
    }

    public static int getHeight() {
        return heightCache;
    }

    public static int getColumnCount(int availableHeight, int catalystsSize) {
        int maxItemsPerColumn = availableHeight / GuiRecipeCatalyst.ingredientSize;
        return NEIServerUtils.divideCeil(catalystsSize, maxItemsPerColumn);
    }

    public static int getRowCount(int availableHeight, int catalystsSize) {
        int columnCount = getColumnCount(availableHeight, catalystsSize);
        return NEIServerUtils.divideCeil(catalystsSize, columnCount);
    }

    public static void loadCatalystInfo() {
        final boolean fromJar = NEIClientConfig.loadCatalystsFromJar();
        NEIClientConfig.logger.info("Loading catalyst info from {}", (fromJar ? "JAR" : "Config"));
        recipeCatalystMap.clear();
        URL handlerUrl = RecipeCatalysts.class.getResource("/assets/nei/csv/catalysts.csv");

        URL url;
        if (fromJar) {
            url = handlerUrl;
            if (url == null) {
                NEIClientConfig.logger.warn("Invalid URL for catalysts csv.");
                return;
            }
        } else {
            File catalystFile = NEIClientConfig.catalystFile;
            if (!catalystFile.exists()) {
                NEIClientConfig.logger.info("Config file doesn't exist, creating");
                try {
                    assert handlerUrl != null;
                    ReadableByteChannel readableByteChannel = Channels.newChannel(handlerUrl.openStream());
                    FileOutputStream fileOutputStream = new FileOutputStream(catalystFile.getAbsoluteFile());
                    FileChannel fileChannel = fileOutputStream.getChannel();
                    fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            }
            try {
                url = NEIClientConfig.catalystFile.toURI().toURL();
            } catch (MalformedURLException e) {
                NEIClientConfig.logger.warn("Invalid URL for catalysts.csv (via config).");
                e.printStackTrace();
                return;
            }
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
            CSVParser csvParser = CSVFormat.EXCEL.withFirstRecordAsHeader().parse(reader);
            if (csvParser.getHeaderNames().contains("handlerID")) {
                NEIClientConfig.logger
                        .warn("Found column 'handlerID' in catalysts.csv. Please rename to 'catalystHandlerID'");
            }
            for (CSVRecord record : csvParser) {
                final String catalystHandlerID;
                if (record.isMapped("handlerID")) {
                    catalystHandlerID = record.get("handlerID");
                } else {
                    catalystHandlerID = record.get("catalystHandlerID");
                }
                final String modId = record.get("modId");
                final boolean requiresMod = Boolean.parseBoolean(record.get("modRequired"));
                final String excludedModId = record.get("excludedModId");
                final int priority = intOrDefault(record.get("priority"), 0);
                // todo
                final String minVersion = record.get("minVersion");
                final String maxVersion = record.get("maxVersion");
                //
                final boolean forceClassName = Boolean.parseBoolean(record.get("forceClassName"));

                if (requiresMod && !Loader.isModLoaded(modId)) continue;
                if (excludedModId != null && Loader.isModLoaded(excludedModId)) continue;

                String itemName = record.get("itemName");
                if (itemName == null || itemName.isEmpty()) continue;
                ItemStack stack = NEIServerUtils.getModdedItem(itemName, record.get("nbtInfo"));
                if (stack == null) {
                    NEIClientConfig.logger.warn("Couldn't find ItemStack " + itemName);
                    continue;
                }
                CatalystInfo catalystInfo = new CatalystInfo(stack, priority);

                String handlerID;
                try {
                    // gently handling copy&paste from handlers.csv
                    Class<?> clazz = Class.forName(catalystHandlerID);
                    Object object = clazz.newInstance();
                    if (object instanceof IRecipeHandler) {
                        if (forceClassName) {
                            forceClassNameList.add(catalystHandlerID);
                        }
                        handlerID = getCatalystHandlerId((IRecipeHandler) object);
                    } else {
                        handlerID = catalystHandlerID;
                    }
                } catch (ClassNotFoundException ignored) {
                    handlerID = catalystHandlerID;
                } catch (InstantiationException ignored) {
                    NEIClientConfig.logger.warn("failed to create instance for " + catalystHandlerID);
                    handlerID = catalystHandlerID;
                }

                addOrPut(recipeCatalystMap, handlerID, catalystInfo);
            }
        } catch (Exception e) {
            NEIClientConfig.logger.warn("Error parsing catalysts.csv");
            e.printStackTrace();
        }

        for (Map.Entry<String, CatalystInfoList> entry : catalystsAdderFromAPI.entrySet()) {
            String catalystHandlerID = entry.getKey();
            for (CatalystInfo catalyst : entry.getValue()) {
                addOrPut(recipeCatalystMap, catalystHandlerID, catalyst);
            }
        }

        for (Map.Entry<String, CatalystInfoList> entry : catalystsAdderFromIMC.entrySet()) {
            String catalystHandlerID = entry.getKey();
            for (CatalystInfo catalyst : entry.getValue()) {
                addOrPut(recipeCatalystMap, catalystHandlerID, catalyst);
            }
        }

        if (!catalystsAdderFromIMC.isEmpty()) {
            // If we're getting an IMC message, it probably means it should match
            Set<String> handlerIds = new HashSet<>(GuiRecipeTab.handlerMap.keySet());
            catalystsAdderFromIMC.keySet().forEach(handlerName -> {
                if (!handlerIds.contains(handlerName)) {
                    NEIClientConfig.logger.warn(
                            "Could not find a registered catalystHandlerID that matches '{}' as received via IMC",
                            handlerName);
                    handlerIds.forEach(handler -> {
                        if (handler.equalsIgnoreCase(handlerName)) {
                            NEIClientConfig.logger.warn("  -- Did you mean: {}", handler);
                        }
                    });
                }
            });
        }
        for (Map.Entry<String, List<ItemStack>> entry : catalystsRemoverFromAPI.entrySet()) {
            String catalystHandlerID = entry.getKey();
            if (recipeCatalystMap.containsKey(catalystHandlerID)) {
                CatalystInfoList catalysts = recipeCatalystMap.get(catalystHandlerID);
                entry.getValue().forEach(catalysts::remove);
            }
        }

        for (Map.Entry<String, List<ItemStack>> entry : catalystsRemoverFromIMC.entrySet()) {
            String catalystHandlerID = entry.getKey();
            if (recipeCatalystMap.containsKey(catalystHandlerID)) {
                CatalystInfoList catalysts = recipeCatalystMap.get(catalystHandlerID);
                entry.getValue().forEach(catalysts::remove);
            }
        }

        updatePosition(getHeight(), true);
    }

    /**
     * Computes the catalyst handler identifier for matching a catalyst to its corresponding recipes.
     *
     * @return defaults to {@link IRecipeHandler#getOverlayIdentifier()}, but falls back to the results of
     *         {@link IRecipeHandler#getHandlerId()} if the former is null. If forceClassName is enabled in
     *         catalysts.csv, {@link IRecipeHandler#getHandlerId()} is returned always.
     */
    public static String getCatalystHandlerId(IRecipeHandler handler) {
        if (forceClassNameList.stream().anyMatch(s -> s.equals(handler.getHandlerId()))) {
            return handler.getHandlerId();
        }
        return Objects.firstNonNull(handler.getOverlayIdentifier(), handler.getHandlerId());
    }

    public static void addOrPut(Map<String, CatalystInfoList> map, String catalystHandlerID, CatalystInfo catalyst) {
        if (map.containsKey(catalystHandlerID)) {
            map.get(catalystHandlerID).add(catalyst);
        } else {
            map.put(catalystHandlerID, new CatalystInfoList(catalystHandlerID, catalyst));
        }
    }

    private static int intOrDefault(String str, int defaultValue) {
        if (str == null || str.equals("")) return defaultValue;
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Deprecated
    public static void addRecipeCatalyst(List<ItemStack> stacks, Class<? extends IRecipeHandler> handler) {}

    @Deprecated
    public static List<PositionedStack> getRecipeCatalysts(Class<? extends IRecipeHandler> handler) {
        return Collections.emptyList();
    }

    @Deprecated
    public static boolean containsCatalyst(Class<? extends IRecipeHandler> handler, ItemStack candidate) {
        return false;
    }
}
