# Mob overlay TODO: spawn rules from other mods

`MobOverlayScan` reads the block, the light and the biome of each cell. Some mods set the result of Forge's
`LivingSpawnEvent.CheckSpawn` from the monster's position, and `MobOverlayScan` doesn't call these handlers.

A monster spawns when the result is `ALLOW`, or when the result is `DEFAULT` and `getCanSpawnHere()` returns
true. So `ALLOW` skips the light test.

## DENY: marked cells where a monster can't spawn

GregTech and Extra Utilities measure a range from the centre of the block.

| mod in 2.9.0 | handler | range | on the client? |
|---|---|---|---|
| GregTech Monster Repellator | `GTSpawnEventHandler.denyMobSpawn()` | sphere. The radius is 4 + 12 × tier without power and 16 + 48 × tier with power | no. `MTEMonsterRepellent.onPostTick()` registers the range on the server only |
| Extra Utilities magnum torch | `EventHandlerServer.magnumTorchDenySpawn()` | ellipsoid 128 blocks out and 32 blocks up and down | maybe. `TileEntityAntiMobTorch` adds itself to the static `magnumTorchRegistry` and doesn't check the side. The bytecode is the source of this row, and the row isn't tested in game |
| Extra Utilities chandelier | the same handler | sphere, radius 16 | maybe. The chandelier's tile entity is a `TileEntityAntiMobTorch` too |
| Gadomancy aura pylon with Lux | `EventHandlerEntityServer.on(CheckSpawn)` | sphere, radius `AuraEffects.LUX.getRange()` | not checked. `AuraEffects.LUX` adds the pylon to `registeredLuxPylons` when the pylon's effect runs on an entity |
| Blood Magic Ward of Sacrosanctity (`AW028SpawnWard`) | `AlchemicalWizardryEventHooks.onLivingSpawnEvent()` | box from the ritual's horizontal and vertical radii | not checked. `RitualEffectSpawnWard` fills `respawnMap` |
| Botania Emptiness potion | `PotionEmptiness.onSpawn()` | sphere around each player with the effect | the player's potion effects |
| ServerUtilities | `ServerUtilitiesWorldEventHandler.onMobSpawned()` | chunks of the safe spawn area, and claimed chunks by the team's settings | no. The claims are server data |
| Et Futurum Requiem | `ServerEventHandler.spawnEvent()` | in the Nether, a cell above `ModBlocks.NETHER_WART` at meta 0, for a mob that doesn't fly | yes. The block below is in the client world |

## ALLOW: monsters spawn in unmarked lit cells

| mod in 2.9.0 | handler | range |
|---|---|---|
| Blood Magic Veil of Evil (`AW029VeilOfEvil`) | `AlchemicalWizardryEventHooks.onLivingSpawnEvent()` | box from the ritual's radii. Creepers are excluded |
| Botania Bloodthirst potion | `PotionBloodthirst.onSpawn()` | sphere around each player with the effect |

Thaumcraft 4.2.3.5 doesn't have a `CheckSpawn` handler. The list comes from a scan of the 2.9.0 pack's jars for
`LivingSpawnEvent$CheckSpawn`. Handlers that test only the mob type, the dimension or the mob count are left out:
AMDIForge, EnderZoo, Automagy, the Extra Utilities Underdark, and the slime handlers of Biomes O' Plenty and Botania.
The Extra Utilities siege handler (`EventHandlerSiege.SiegeCheckSpawn()`) isn't checked.

## To do

- [ ] Add an NEI API: a mod registers a cell test that returns `DENY`, `ALLOW` or `DEFAULT`. `MobOverlayScan` calls
  each test for each cell in the box.
- [ ] Add an API call to mark an area dirty. `MobOverlayScan` then rescans the area when a range is added or
  removed.
- [ ] Ask GregTech, Gadomancy, Blood Magic and ServerUtilities to send their ranges to the client.
- [ ] Test the Extra Utilities registry on a client with a dedicated server.
- [ ] Find out how the handlers combine. `GTSpawnEventHandler` returns early on `DENY`, and Blood Magic's handler
  changes `ALLOW` to `DEFAULT`, so the result depends on the order of the handlers on the event bus.
