# NotEnoughItems Unofficial - By the GTNH Team

A continuation of NotEnoughItems for 1.7.10 by the developers of Gregtech: New Horizons modpack, with features either inspired and/or backported from JustEnoughItems

 

We've tested this against all of the mods included in GTNH, as well as a limited set of other mods (like Reika's mods).  Every effort has been made to maintain backwards compatibility, however the focus is on the mods contained in the GTNH modpack.

 

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
* `@[Mod]->[item]` searching.  ex: `@Gregtech->iron ingot`
* Cycle between Recipe, Utility, and Cheat mode by ctrl clicking on the Wrench Icon 
* GT5u Tools/Items should now properly work with the Overlay Recipe Transfer

## Other items of note:

* Licensed under LGPL - Use this however you want, but please give back any modifications
* Remove TMI style
* Removed inventory Load/Save state
