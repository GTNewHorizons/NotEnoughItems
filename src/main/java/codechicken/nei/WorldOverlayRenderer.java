package codechicken.nei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

import org.lwjgl.opengl.GL11;

import codechicken.lib.math.MathHelper;
import codechicken.nei.KeyManager.IKeyStateTracker;

public class WorldOverlayRenderer implements IKeyStateTracker {

    public static int chunkOverlay = 0;

    public static int mobOverlay = 0;
    private static byte[] mobSpawnCache;
    private static long mobOverlayUpdateTime;
    private static String oregenPatternName;
    private static boolean hasOregenPatternBeenSet = false;

    public static void reset() {
        mobOverlay = 0;
        chunkOverlay = 0;
        hasOregenPatternBeenSet = false;
    }

    @Override
    public void tickKeyStates() {
        if (Minecraft.getMinecraft().currentScreen != null) return;

        if (KeyManager.keyStates.get("world.moboverlay").down) mobOverlay = (mobOverlay + 1) % 2;
        if (KeyManager.keyStates.get("world.chunkoverlay").down){
            chunkOverlay = (chunkOverlay + 1) % (NEIModContainer.isGT5Loaded() ? 4 : 3);
            // 0-2 (or 3 with gt)
            NEIClientUtils.printChatMessage(new ChatComponentText(NEIClientUtils.translate("chat.chunkoverlay." + chunkOverlay)));
        }


    }

    public static void render(float frame) {
        GL11.glPushMatrix();
        Entity entity = Minecraft.getMinecraft().renderViewEntity;

        double interpPosX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * frame;
        double interpPosY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * frame;
        double interpPosZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * frame;

        int intOffsetX = (int) interpPosX;
        int intOffsetY = (int) interpPosY;
        int intOffsetZ = (int) interpPosZ;

        GL11.glTranslated(-interpPosX + intOffsetX, -interpPosY + intOffsetY, -interpPosZ + intOffsetZ);

        renderChunkBounds(entity, intOffsetX, intOffsetY, intOffsetZ);
        renderMobSpawnOverlay(entity, intOffsetX, intOffsetY, intOffsetZ);
        GL11.glPopMatrix();
    }

    private static void buildMobSpawnOverlay(Entity entity) {
        if (mobSpawnCache == null) {
            mobSpawnCache = new byte[33 * 33 * 33]; // 35kB
        }

        World world = entity.worldObj;
        int x1 = (int) entity.posX;
        int z1 = (int) entity.posZ;
        int y1 = (int) MathHelper.clip(entity.posY, 16, world.getHeight() - 16);

        for (int i = 0; i <= 32; i++) {
            int x = x1 - 16 + i;
            for (int j = 0; j <= 32; j++) {
                int z = z1 - 16 + j;
                int bufIndex = (i * 33 + j) * 33;

                Chunk chunk = world.getChunkFromBlockCoords(x, z);
                BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
                if (biome.getSpawnableList(EnumCreatureType.monster).isEmpty() || biome.getSpawningChance() <= 0) {
                    mobSpawnCache[bufIndex] = -1;
                    continue;
                }

                int maxHeight = chunk.worldObj.getHeightValue(x, z);

                for (int k = 0; k <= 32; k++) {
                    int y = y1 - 16 + k;
                    if (y > maxHeight) {
                        mobSpawnCache[bufIndex + k] = -1;
                        break;
                    }
                    mobSpawnCache[bufIndex + k] = getSpawnMode(chunk, x, y, z);
                }
            }
        }
    }

    private static void renderMobSpawnOverlay(Entity entity, int intOffsetX, int intOffsetY, int intOffsetZ) {
        if (mobOverlay == 0) {
            if (mobSpawnCache != null) {
                mobSpawnCache = null;
            }
            return;
        }

        long worldTime = entity.worldObj.getTotalWorldTime();
        if (mobSpawnCache == null || mobOverlayUpdateTime != worldTime) {
            mobOverlayUpdateTime = worldTime;
            buildMobSpawnOverlay(entity);
        }

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glLineWidth(1.5F);

        int curSpawnMode = 2;

        World world = entity.worldObj;
        int x1 = (int) entity.posX - intOffsetX;
        int z1 = (int) entity.posZ - intOffsetZ;
        int y1 = (int) MathHelper.clip(entity.posY, 16, world.getHeight() - 16) - intOffsetY;

        final Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_LINES);
        tess.setColorOpaque(255, 0, 0);

        for (int i = 0; i <= 32; i++) {
            int x = x1 - 16 + i;
            for (int j = 0; j <= 32; j++) {
                int z = z1 - 16 + j;
                int cacheIndex = (i * 33 + j) * 33;

                for (int k = 0; k <= 32; k++) {
                    int y = y1 - 16 + k;
                    int spawnMode = mobSpawnCache[cacheIndex + k];
                    if (spawnMode == 0) {
                        continue;
                    } else if (spawnMode == -1) {
                        break;
                    }

                    if (spawnMode != curSpawnMode) {
                        if (spawnMode == 1) {
                            tess.setColorOpaque(255, 255, 0);
                        } else {
                            tess.setColorOpaque(255, 0, 0);
                        }
                        curSpawnMode = spawnMode;
                    }

                    tess.addVertex(x, y + 0.02, z);
                    tess.addVertex(x + 1, y + 0.02, z + 1);
                    tess.addVertex(x + 1, y + 0.02, z);
                    tess.addVertex(x, y + 0.02, z + 1);
                }
            }
        }

        tess.draw();
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    private static final Entity dummyEntity = new EntityPig(null);
    private static final AxisAlignedBB aabb = AxisAlignedBB.getBoundingBox(0, 0, 0, 0, 0, 0);

    private static byte getSpawnMode(Chunk chunk, int x, int y, int z) {
        if (chunk.getSavedLightValue(EnumSkyBlock.Block, x & 15, y, z & 15) >= 8
                || !SpawnerAnimals.canCreatureTypeSpawnAtLocation(EnumCreatureType.monster, chunk.worldObj, x, y, z)) {
            return 0;
        }

        aabb.minX = x + 0.2;
        aabb.maxX = x + 0.8;
        aabb.minY = y + 0.01;
        aabb.maxY = y + 1.8;
        aabb.minZ = z + 0.2;
        aabb.maxZ = z + 0.8;
        if (!chunk.worldObj.getCollidingBoundingBoxes(dummyEntity, aabb).isEmpty()
                || chunk.worldObj.isAnyLiquid(aabb)) {
            return 0;
        }

        return (byte) (chunk.getSavedLightValue(EnumSkyBlock.Sky, x & 15, y, z & 15) >= 8 ? 1 : 2);
    }

    private static void renderChunkBounds(Entity entity, int intOffsetX, int intOffsetY, int intOffsetZ) {
        if (chunkOverlay == 0) return;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glLineWidth(1.5F);
        GL11.glBegin(GL11.GL_LINES);

        for (int cx = -4; cx <= 4; cx++) for (int cz = -4; cz <= 4; cz++) {
            double x1 = ((entity.chunkCoordX + cx) << 4) - intOffsetX;
            double z1 = ((entity.chunkCoordZ + cz) << 4) - intOffsetZ;
            double x2 = x1 + 16;
            double z2 = z1 + 16;

            double dy = 128;
            double y1 = Math.floor(entity.posY - dy / 2);
            double y2 = y1 + dy;
            if (y1 < 0) {
                y1 = 0;
                y2 = dy;
            }

            if (y1 > entity.worldObj.getHeight()) {
                y2 = entity.worldObj.getHeight();
                y1 = y2 - dy;
            }

            y1 -= intOffsetY;
            y2 -= intOffsetY;

            double dist = Math.pow(1.5, -(cx * cx + cz * cz));

            GL11.glColor4d(0.9, 0, 0, dist);
            if (cx >= 0 && cz >= 0) {
                GL11.glVertex3d(x2, y1, z2);
                GL11.glVertex3d(x2, y2, z2);
            }
            if (cx >= 0 && cz <= 0) {
                GL11.glVertex3d(x2, y1, z1);
                GL11.glVertex3d(x2, y2, z1);
            }
            if (cx <= 0 && cz >= 0) {
                GL11.glVertex3d(x1, y1, z2);
                GL11.glVertex3d(x1, y2, z2);
            }
            if (cx <= 0 && cz <= 0) {
                GL11.glVertex3d(x1, y1, z1);
                GL11.glVertex3d(x1, y2, z1);
            }

            if (cx == 0 && cz == 0) {
                if (chunkOverlay == 2) {
                    dy = 32;
                    y1 = Math.floor(entity.posY - dy / 2);
                    y2 = y1 + dy;
                    if (y1 < 0) {
                        y1 = 0;
                        y2 = dy;
                    }

                    if (y1 > entity.worldObj.getHeight()) {
                        y2 = entity.worldObj.getHeight();
                        y1 = y2 - dy;
                    }

                    y1 -= intOffsetY;
                    y2 -= intOffsetY;

                    GL11.glColor4d(0, 0.9, 0, 0.4);
                    for (double y = (int) y1; y <= y2; y++) {
                        GL11.glVertex3d(x2, y, z1);
                        GL11.glVertex3d(x2, y, z2);
                        GL11.glVertex3d(x1, y, z1);
                        GL11.glVertex3d(x1, y, z2);
                        GL11.glVertex3d(x1, y, z2);
                        GL11.glVertex3d(x2, y, z2);
                        GL11.glVertex3d(x1, y, z1);
                        GL11.glVertex3d(x2, y, z1);
                    }
                    for (double h = 1; h <= 15; h++) {
                        GL11.glVertex3d(x1 + h, y1, z1);
                        GL11.glVertex3d(x1 + h, y2, z1);
                        GL11.glVertex3d(x1 + h, y1, z2);
                        GL11.glVertex3d(x1 + h, y2, z2);
                        GL11.glVertex3d(x1, y1, z1 + h);
                        GL11.glVertex3d(x1, y2, z1 + h);
                        GL11.glVertex3d(x2, y1, z1 + h);
                        GL11.glVertex3d(x2, y2, z1 + h);
                    }
                } else if (chunkOverlay == 3) {
                    if (!hasOregenPatternBeenSet) {
                        try {
                            oregenPatternName = ((Enum<?>) Class.forName("gregtech.common.GT_Worldgenerator")
                                    .getDeclaredField("oregenPattern").get(null)).name();
                        } catch (Exception ignored) {
                            oregenPatternName = "AXISSYMMETRICAL";
                        }
                        hasOregenPatternBeenSet = true;
                    }
                    int gx1;
                    int gz1;
                    if (oregenPatternName.equals("EQUAL_SPACING")) {
                        // gt oregen uses new regular pattern
                        gx1 = ((Math.floorDiv(entity.chunkCoordX, 3) * 3) << 4) - intOffsetX;
                        gz1 = ((Math.floorDiv(entity.chunkCoordZ, 3) * 3) << 4) - intOffsetZ;
                    } else {
                        // gt oregen uses old bugged pattern
                        gx1 = (((entity.chunkCoordX < 0 ? entity.chunkCoordX - 3 : entity.chunkCoordX) / 3 * 3) << 4)
                                - intOffsetX;
                        gz1 = (((entity.chunkCoordZ < 0 ? entity.chunkCoordZ - 3 : entity.chunkCoordZ) / 3 * 3) << 4)
                                - intOffsetZ;
                        if (entity.chunkCoordX < 0) {
                            gx1 += 16;
                        }
                        if (entity.chunkCoordZ < 0) {
                            gz1 += 16;
                        }
                    }
                    int gx2 = gx1 + 48;
                    int gz2 = gz1 + 48;

                    GL11.glColor4d(0, 0.9, 0, 0.4);
                    for (double y = (int) y1; y <= y2; y++) {
                        GL11.glVertex3d(gx2, y, gz1);
                        GL11.glVertex3d(gx2, y, gz2);
                        GL11.glVertex3d(gx1, y, gz1);
                        GL11.glVertex3d(gx1, y, gz2);
                        GL11.glVertex3d(gx1, y, gz2);
                        GL11.glVertex3d(gx2, y, gz2);
                        GL11.glVertex3d(gx1, y, gz1);
                        GL11.glVertex3d(gx2, y, gz1);
                    }
                    for (double h = 4; h <= 44; h += 4) {
                        if (h % 16 == 0) {
                            continue;
                        }
                        GL11.glVertex3d(gx1 + h, y1, gz1);
                        GL11.glVertex3d(gx1 + h, y2, gz1);
                        GL11.glVertex3d(gx1 + h, y1, gz2);
                        GL11.glVertex3d(gx1 + h, y2, gz2);
                        GL11.glVertex3d(gx1, y1, gz1 + h);
                        GL11.glVertex3d(gx1, y2, gz1 + h);
                        GL11.glVertex3d(gx2, y1, gz1 + h);
                        GL11.glVertex3d(gx2, y2, gz1 + h);
                    }

                    GL11.glColor4d(0, 0, 0.9, 0.4);
                    gx1 += 23;
                    gz1 += 23;
                    gx2 = gx1 + 1;
                    gz2 = gz1 + 1;

                    GL11.glVertex3d(gx1, y1, gz1);
                    GL11.glVertex3d(gx1, y2, gz1);
                    GL11.glVertex3d(gx2, y1, gz1);
                    GL11.glVertex3d(gx2, y2, gz1);
                    GL11.glVertex3d(gx1, y1, gz2);
                    GL11.glVertex3d(gx1, y2, gz2);
                    GL11.glVertex3d(gx2, y1, gz2);
                    GL11.glVertex3d(gx2, y2, gz2);
                }
            }
        }

        GL11.glEnd();
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    public static void load() {
        KeyManager.trackers.add(new WorldOverlayRenderer());
    }
}
