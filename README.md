# NotEnoughItems Unofficial - By the GTNH Team

A continuation of NotEnoughItems for 1.7.10 by the developers of Gregtech: New Horizons modpack, with features either inspired and/or backported from JustEnoughItems

We've tested this against all the mods included in GTNH, as well as a limited set of other mods (like Reika's mods). Every effort has been made to maintain backwards compatibility, however the focus is on the mods contained in the GTNH modpack.

If you have issues with NEI outside the GTNH modpack, please report them in the [GTNH NEI GitHub](https://github.com/GTNewHorizons/NotEnoughItems).

## Changes in this fork

### New Features:

* Speed
  - Uses a parallel stream to search the item list over multiple cores, resulting in 2-6x faster searches on average
  - Loads the recipe handlers in parallel
* A textbox for search with most of the features you'd expect - moving forward, backwards, selection, etc
* Bookmarks! Are you in the process of crafting? Bookmark it using either 'A' or configure your own key.
* Toggle bookmark pane.  Default shortcut key `B`.  Item Subsets menu is only available if bookmarks are not visible.
* Utility/Cheat buttons line up and wrap based on GUI size
* ItemList is no longer regenerated from the ItemRegistry on every inventory load
* JEI (Or Creative) Style tabs [Optional]  Note: Requires explicit support to be added for an ItemStack to render, otherwise falls back to the first two letters of the handler name.
* Tabs/Handlers are loaded from a CSV config in the JAR (or optionally from the config folder).  NBT IMCEvents `registerHandlerInfo` and `removeHandlerInfo` are available for mod authors to add handler information, using the same fields as the CSV file
* `@[Mod]->[item]` searching.  ex: `@Mod.gregtech->iron ingot`
* Cycle between Recipe, Utility, and Cheat mode by ctrl clicking on the Wrench Icon
* GT5u Tools/Items and GT6 tools should now properly work with the Overlay Recipe Transfer

### Other items of note:

* Remove TMI style
* Removed inventory Load/Save state

## Adding support for GTNH NEI features to your mod

### Help! My mod is broken in your version of NEI! How can I fix it?

Our version of NEI changes the recipe UI to be a bit more similar to [JEI][JEI]. As such, some legacy recipe handlers that assume the specific UI layout of old NEI might break even after providing additional metadata. Changes to the UI include the following: the recipe screen no longer has a fixed height, but can stretch to show more recipes at once. Additionally, the screen is no longer vertically centered, but at a fixed offset from the top of the MC window. Furthermore, the recipe paging widget has been moved from the bottom to the top of the recipe screen.

The following sections list strategies for fixing broken handlers. You are also always welcome to open an issue in our repository if you are stuck with a broken recipe handler.

#### My mod is broken and I can't fix it, because I'm not a developer, or the mod's license doesn't allow modification

Some mods are no longer maintained and cannot be updated due to licensing. For those we provide two ways of fixing broken recipe handlers. The first one are the `handlers.csv` and `catalysts.csv` files that are packaged in the NEI mod jar. These files include recipe handler metadata for mods that don't ship it on their own. If the broken recipe handler is not already in there, you can enable the config options under `NEI Options > Tools > Load handlers/catalysts from Config` that will put a copy of those files into the config directory which you then can edit. The columns are described in the sections below.

Second, if tweak handler parameters via `handlers.csv` doesn't help and there are still some glitches, we provide a last-ditch effort called the "height hack". This tries to emulate the old NEI recipe layout, specifically on the vertical axis where our version of NEI had the most significant changes. This should help with misaligned tooltips and click zones especially. The height hack can be enabled by adding the fully-qualified class name of the recipe handler to the `heighthackhandlers.cfg` config file. If you encounter a recipe handler that requires this measure please open an issue and let us know.

#### I'm a developer working on my own mod or maintaining a forked mod

Please consider adding support for our IMC messages or events to automatically register handler metadata for your mod. The fields available in the new metadata are described in the following section.

Even after providing handler metadata, there may still be broken elements in your handler. In our experience this most commonly affects tooltips and custom click zones. **Please make sure to not rely on a fixed screen layout!** In particular, do not use `GuiScreen.height` to calculate the top edge of the recipe UI, instead use `guiTop`. Also, do not assume a fixed offset from the top edge of the recipe screen (the offset is different in legacy NEI compared to our version), instead use `GuiRecipe#getRecipePosition`. `GuiRecipe` may already be passed to certain handler methods if you're overriding `TemplateRecipeHandler`, alternatively, you can obtain a reference to it via:
```java
GuiRecipe gui = (GuiRecipe) Minecraft.getMinecraft().currentScreen;
```

Also, do not assume a fixed number of recipes will be shown at a time. Due to the flexible layout, the number might vary between 1 and `maxRecipesPerPage`. Again, make use of `GuiRecipe#getRecipePosition`.

Here is an example of a poorly implemented recipe handler:
```java
@Override
public List<String> handleTooltip(GuiRecipe gui, List<String> list, int recipeIdx) {
    CachedMachineRecipe r = (CachedMachineRecipe) this.arecipes.get(recipeIdx % this.arecipes.size());
    if (r != null) {
        Point mousePosition = GuiDraw.getMousePosition();
        int gleft = (gui.width - 176) / 2;
        int gtop = (gui.height - 134) / 2; // BAD: uses gui.height instead of gui.guiTop
        if (r.fluid != null) {
            if (mousePosition.x > gleft + 110
                && mousePosition.x <= gleft + 110 + 16
                && mousePosition.y > gtop + 13 // BAD: assumes a fixed offset from gtop/guiTop
                && mousePosition.y <= gtop + 13 + 47) {
                list.add(r.fluid.getLocalizedName());
                list.add(r.fluid.amount + " mB");
            }
        }
    }

    return super.handleTooltip(gui, list, recipeIdx);
}
```
Here is an improved version that should work both in our NEI and in legacy NEI:
```java
@Override
public List<String> handleTooltip(GuiRecipe gui, List<String> list, int recipeIdx) {
    CachedMachineRecipe r = (CachedMachineRecipe) this.arecipes.get(recipeIdx % this.arecipes.size());
    if (r != null) {
        Point mousePosition = GuiDraw.getMousePosition();
        Point recipePosition = gui.getRecipePosition(recipeIdx);
        if (r.fluid != null) {
            if (mousePosition.x > (gui.guiLeft + recipePosition.x + 110)
                && mousePosition.x <= (gui.guiLeft + recipePosition.x + 110 + 16)
                && mousePosition.y > (gui.guiTop + recipePosition.y + 13)
                && mousePosition.y <= (gui.guiTop + recipePosition.y + 13 + 47) {
                list.add(r.fluid.getLocalizedName());
                list.add(r.fluid.amount + " mB");
            }
        }
    }

    return super.handleTooltip(gui, list, recipeIdx);
}
```

### New Metadata for recipe handlers

Legacy NEI only had a small, fixed size recipe screen, showing one or two recipes at a time. Our version, borrowing from JEI, improves this by switching to a flexible screen height that allows showing up to 5 recipes at a time in many cases. In addition, the new JEI-style tab bar improves navigation between handlers by showing an icon for each recipe handler.

To enable these new capabilities, NEI has to be given additional metadata to make it work. This version of NEI includes new native APIs and additionally ships with two files `assets/nei/csv/handlers.csv`, and `assets/nei/csv/catalysts.csv` as a fallback solution to include the necessary information for mods that are no longer updated and do not take advantage of the native APIs that are provided. If you are working on a mod of your own that includes NEI integration, we encourage you to add IMC or event support as described below.

#### Recipe handler metadata

(values are strings if not specified otherwise)

- **handlerID:** handler identifier, usually the fully-qualified class name of the recipe handler (including package name); can differ to provide multiple handlers from a single class; check the handler dump described in a section below
  - example: `vazkii.botania.client.integration.nei.recipe.RecipeHandlerPureDaisy`
  - *required*
- **modName:** display name of the mod to which the recipe handler belongs to
  - example: `Botania`
  - *required*
- **itemName**, and **nbtInfo:** item used as an icon to represent the recipe handler in the tab bar
  - example: `Botania:specialFlower` (itemName), `{type:puredaisy}` (nbtInfo)
- **modId:** modid of the mod for which the recipe handler is responsible
  - example: `Botania`
  - *required*
- **modRequired:** whether the mod specified in `modId` needs to be present in order for NEI to load the recipe handler (boolean value; `TRUE`/`FALSE` in CSV)
- **excludedModId:** prevent the recipe handler from being loaded if the specified mod is present
- **yShift:** allows fine control over vertical alignment of the recipes from top of the recipe screen; value is in pixels (integer value)
  - default: `0`
- **handlerHeight:** height of a single recipe in pixels, used for tiling multiple recipes vertically according to `maxRecipesPerPage` (integer value)
  - default: `65`
- **handlerWidth:** width of a single recipe in pixels (integer value)
  - default: `166`
- **maxRecipesPerPage:** maximum number of recipes to show per page; less recipes will be shown if they do not fit on the screen (integer value)
  - default: `1`
- **imageResource**, **imageX**, **imageY**, **imageWidth**, and **imageHeight:** allow specifying an arbitrary texture as icon for this recipe handler instead of an item; `imageX`, `imageY`, `imageWidth`, and `imageHeight` allow indexing into a sprite sheet (integer values except for `imageResource`)
  - example: `nei:textures/nei_tabbed_sprites.png` (imageResource), `80` (imageX), `0` (imageY), `14` (imageWidth), `14` (imageHeight)
- **itemNotes:** ignored by NEI; this field is purely informational for development; not available via IMC/event

#### Catalyst metadata

The catalyst concept has been borrowed from JEI. The idea behind it is, that in addition to the recipe inputs and outputs, a recipe is also associated with the "machine" that processes it (also called a 'catalyst'). That way, if you look up a recipe you can also see which "machines" enable it. For example regular crafting recipes can be processed in a Crafting Table, however there may be many more blocks that provide the same functionality and NEI now lists them alongside the recipe. Similarly, if you look up uses for a "machine" or catalyst, it will automatically show all recipes that can be processed with it.

Again, this needs additional metadata work, either provided by mods themselves, or through default configuration in `assets/nei/csv/catalysts.csv`.

(values are strings if not specified otherwise)

- **catalystHandlerID:** identifier of the handler for which this catalyst can be used; by default, this should be the overlay identifier, or alternatively the handler identifier if the former is not defined, or `forceClassName` is true; appropriate values can be inferred from the handler information dump described in a following section
  - example: `vazkii.botania.client.integration.nei.recipe.RecipeHandlerPureDaisy`, or `crafting`
  - *required*
- **itemName:**, and **nbtInfo:** actual catalyst item
  - example: `Botania:specialFlower` (itemName), `{type:puredaisy}` (nbtInfo)
  - *required* (itemName)
- **modId:** modid of the mod which includes the catalyst
- **modRequired:** whether the mod specified in `modId` needs to be present in order for NEI to load the catalyst (boolean value; `TRUE`/`FALSE` in CSV)
- *required*
- **excludedModId:** prevent the catalyst from being loaded if the specified mod is present
- **priority:** position of the catalyst within the list of all possible catalysts for a given recipe handler; higher priority takes precedence (integer value)
  - default: `0`
- **minVersion:**, and **maxVersion:** only present in `catalysts.csv`, currently unused
- **forceClassName:** `catalystHandlerID` by default refers to the overlay identifier of the recipe handler and only falls back to the handler identifier if the former is undefined, so this options allows forcing matching the handler identifier even when an overlay identifier exists separately; only supported in `catalysts.csv` (boolean value)
- **itemNotes:** ignored by NEI; this field is purely informational for development; not available via IMC

#### Dumping recipe handler information for debugging

NEI can dump information about all currently loaded recipe handlers. This helps especially with finding the correct handler and overlay identifiers for matching handler and catalyst metadata.

This information can be dumped through NEI options > Tools > Dumps > Handler.

Among other things, this dump lists all loaded recipe handlers by their handler identifier and their overlay identifier (if defined).

#### Sending metadata via IMC

Our version of NEI adds a range of IMC messages for registering handler metadata. This is the preferred way of specifying handler metadata. Please consider adding support for this in your own mod.

The following messages are available:
- **registerHandlerInfo:** NBT IMC message; allowed keys are specified in the handler metadata section
- **removeHandlerInfo:** NBT IMC message; required key: `handlerID`, identifier string of the handler to be removed
- **registerCatalystInfo:** NBT IMC message; allowed keys are specified in the catalyst metadata section
- **removeCatalystInfo:** NBT IMC message; required keys: `catalystHandlerID` ( identifier string of the handler from which the catalyst is to be removed), `itemName` (optionally specify `nbtInfo` if necessary)

Here is an example of how to send the handler and catalyst metadata via IMC:
```java
NEIInscriberRecipeHandler inscriberHandler = new NEIInscriberRecipeHandler();
API.registerRecipeHandler(inscriberHandler);

NBTTagCompound handlerMetadata = new NBTTagCompound();
handlerMetadata.setString("handlerID", inscriberHandler.getHandlerId());
handlerMetadata.setString("modName", MOD_NAME);
handlerMetadata.setString("modId", MOD_ID);
handlerMetadata.setBoolean("modRequired", true);
handlerMetadata.setString("itemName", GameRegistry.findUniqueIdentifierFor(block));
handlerMetadata.setInteger("handlerHeight", 70);
handlerMetadata.setInteger("maxRecipesPerPage", 5);
FMLInterModComms.sendMessage("NotEnoughItems", "registerHandlerInfo", handlerMetadata);

NBTTagCompound catalystMetadata = new NBTTagCompound();
catalystMetadata.setString("catalystHandlerID", inscriberHandler.getOverlayIdentifier());
catalystMetadata.setString("itemName", GameRegistry.findUniqueIdentifierFor(block));
FMLInterModComms.sendMessage("NotEnoughItems", "registerCatalystInfo", catalystMetadata);
```

#### Sending metadata through event handling

Besides IMC, there exists another native path for adding metadata to recipe handlers: subscribing to `NEIRegisterHandlerInfosEvent`. This requires adding NEI as a dependency to your project.

This event provides the `registerHandlerInfo()` method for adding metadata to your handlers. It takes a `Consumer` function which receives a `HandlerInfo` builder (`HandlerInfo` is the NEI class representing handler metadata).

Here is an example for handling such an event:
```java
@SubscribeEvent
public void registerHandlers(NEIRegisterHandlerInfosEvent event) {
    event.registerHandlerInfo(
            handlerID,
            modName,
            modId,
            (builder) -> {
                return builder.setHeight(30).setMaxRecipesPerPage(5);
            });
    }
}
```

There is currently no event for registering catalysts.

## Developing NEI

Before launching, you need to add 
```
-Dfml.coreMods.load=codechicken.nei.asm.NEICorePlugin
``` 
as a command line argument to the VM.

## License

GTNH Modifications Copyright (c) 2019-2022 mitchej123 and the GTNH Team

Licensed under LGPL-3.0 or later - use this however you want, but please give back any modifications

Parts inspired/borrowed/backported from [JEI][JEI112Tree] under the MIT License.

Original code Copyright (c) 2014-2015 mezz and was licensed under the MIT License.

[JEI]: https://github.com/mezz/JustEnoughItems
[JEI112Tree]: https://github.com/mezz/JustEnoughItems/tree/1.12
