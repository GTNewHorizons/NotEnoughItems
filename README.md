# NotEnoughItems Unofficial - By the GTNH Team

A continuation of NotEnoughItems for 1.7.10 by the developers of Gregtech: New Horizons modpack, with features either inspired and/or backported from JustEnoughItems

We've tested this against all of the mods included in GTNH, as well as a limited set of other mods (like Reika's mods). Every effort has been made to maintain backwards compatibility, however the focus is on the mods contained in the GTNH modpack.

If you have issues with NEI outside of the GTNH modpack you may report them in the [GTNH NEI GitHub](https://github.com/GTNewHorizons/NotEnoughItems).

## New Features:

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

### Information Page Handler
Want to add some information about a block or item without making a massive tooltip for it? You can add information about any block or item by registering it in the Information Handler.

Matching information pages are displayed when either the uses or recipes of an item are searched.

If multiple mods add pages to the same item, they will all appear together as if one mod added them with the Multiple Page Format.

Your mod can call:
```java
FMLInterModComms.sendMessage("NotEnoughItems", "addItemInfo", nbt);
```
Where nbt is an NBTTagCompound formatted as described below.

| Tag              | Type                 | Description                                                                                                                                                                                |
|------------------|----------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `itemName`       | String               | The registry name of the item in the format `modid:itemname` or `modid:itemname:meta`. Setting meta to OreDictionary.WILDCARD_VALUE (32767) will match *any metadata* value for that item. |
| `nbtInfo`        | String               | *(Optional)* Extra NBT data to display for the item, as an SNBT-like string. Currently not used for matching.                                                                              |
| `page` / `pages` | String / String List | The actual text to display. See below for differences between single and multi-page messages.<br/>Both formats will automatically translate provided strings.                              |

#### Single Page Format

Use the `"page"` string tag when you only want to add one page of information:
```java
NBTTagCompound tag = new NBTTagCompound();
tag.setString("itemName", "minecraft:stone");
tag.setString("page", "Stone is a common block.\nIt drops cobblestone unless mined with Silk Touch.");

FMLInterModComms.sendMessage("NotEnoughItems", "addItemInfo", tag);
```

#### Multiple Page Format

Use the `"pages"` tag as an `NBTTagList` of strings when you want more than one page:
```java
NBTTagCompound tag = new NBTTagCompound();
tag.setString("itemName", "minecraft:diamond_sword");

NBTTagList pages = new NBTTagList();
pages.appendTag(new NBTTagString("A powerful melee weapon."));
pages.appendTag(new NBTTagString("Can be enchanted for extra effects."));

tag.setTag("pages", pages);

FMLInterModComms.sendMessage("NotEnoughItems", "addItemInfo", tag);
```
* Each list entry is one page.
* Pages will appear in NEI with buttons to scroll through them.

#### Item Matching

`itemName` must match the registered name of the item.

If your item has subtypes (metadata), either:

* Include the metadata in the itemName string: "modid:itemname:3", or
* Omit the metadata to apply to only meta 0, or
* Set the meta to OreDictionary.WILDCARD_VALUE (32767) to match all subtypes.

#### Item Information Config File

If you're a modpack creator and want to make custom item information pages, they can easily be made in `config/NEI/informationpages.cfg`.

## Other items of note:

* Remove TMI style
* Removed inventory Load/Save state

## License

GTNH Modifications Copyright (c) 2019-2024 mitchej123 and the GTNH Team

Licensed under LGPL-3.0 or later - use this however you want, but please give back any modifications

Parts inspired/borrowed/backported from [JEI](https://github.com/mezz/JustEnoughItems/tree/1.12) under the MIT License.

Originial code Copyright (c) 2014-2015 mezz and was licensed under the MIT License.
