# NotEnoughItems Unofficial - By the GTNH Team

A continuation of NotEnoughItems for 1.7.10 by the developers of Gregtech: New Horizons modpack, with features either inspired and/or backported from JustEnoughItems

We've tested this against all of the mods included in GTNH, as well as a limited set of other mods (like Reika's mods). Every effort has been made to maintain backwards compatibility, however the focus is on the mods contained in the GTNH modpack.

If you have issues with NEI outside of the GTNH modpack you may report them in the [GTNH NEI GitHub](https://github.com/GTNewHorizons/NotEnoughItems).

## New Features:

* Speed
  - Uses a parallel stream to search the item list over multiple cores, resulting in 2-6x faster searches on average
  - Loads the recipe handlers in parallel
* A textbox for search with most of the features you'd expect - moving forward, backwards, selection, etc
* Bookmarks!  What are you in the process of crafting? Bookmark it using either 'A' or configure your own key.
* Toggle bookmark pane.  Default shortcut key `B`.  Item Subsets menu is only available if bookmarks are not visible.
* Utility/Cheat buttons line up and wrap based on GUI size
* ItemList is no longer regenerated from the ItemRegistry on every inventory load
* JEI (Or Creative) Style tabs [Optional]  Note: Requires explicit support to be added for an ItemStack to render, otherwise falls back to the first two letters of the handler name.
* Tabs/Handlers are loaded from a CSV config in the JAR (or optionally from the config folder).  NBT IMCEvents `registerHandlerInfo` and `removeHandlerInfo` are available for mod authors to add handler information, using the same fields as the CSV file
* `@[Mod]->[item]` searching.  ex: `@Mod.gregtech->iron ingot`
* Cycle between Recipe, Utility, and Cheat mode by ctrl clicking on the Wrench Icon
* GT5u Tools/Items and GT6 tools should now properly work with the Overlay Recipe Transfer

## Other items of note:

* Remove TMI style
* Removed inventory Load/Save state

## Development
Before launching, you need to add 
```
-Dfml.coreMods.load=codechicken.nei.asm.NEICorePlugin
``` 
as a command line argument to the VM.

## License

GTNH Modifications Copyright (c) 2019-2022 mitchej123 and the GTNH Team

Licensed under LGPL-3.0 or later - use this however you want, but please give back any modifications

Parts inspired/borrowed/backported from [JEI](https://github.com/mezz/JustEnoughItems/tree/1.12) under the MIT License.

Originial code Copyright (c) 2014-2015 mezz and was licensed under the MIT License.
