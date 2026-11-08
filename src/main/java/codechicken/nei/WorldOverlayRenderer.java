package codechicken.nei;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSnow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import org.lwjgl.opengl.GL11;

import com.cardinalstar.cubicchunks.world.api.IMinMaxHeight;

import codechicken.nei.KeyManager.IKeyStateTracker;
import cpw.mods.fml.relauncher.ReflectionHelper;

public class WorldOverlayRenderer implements IKeyStateTracker {

    public static int chunkOverlay = 0;

    public static int mobOverlay = 0;
    private static final MobSpawnScan mobSpawnScan = new MobSpawnScan();
    private static final MobSpawnMarkedFace markedFace = new MobSpawnMarkedFace();
    // display list of the overlay quads, compiled when the scan or the style changes
    private static int mobSpawnList = -1;
    private static int mobSpawnListChange;
    private static MobSpawnOverlayStyle mobSpawnListStyle;

    public static void reset() {
        mobOverlay = 0;
        chunkOverlay = 0;
    }

    @Override
    public void tickKeyStates() {
        if (Minecraft.getMinecraft().currentScreen != null) return;

        if (KeyManager.isPressed("world.moboverlay")) {
            mobOverlay = (mobOverlay + 1) % 2;
            NEIClientUtils
                    .printChatMessage(new ChatComponentText(NEIClientUtils.translate("chat.moboverlay." + mobOverlay)));
        }

        if (KeyManager.isPressed("world.chunkoverlay")) {
            chunkOverlay = (chunkOverlay + 1) % (NEIModContainer.isGT5Loaded() ? 4 : 3);
            // 0-2 (or 3 with gt)
            NEIClientUtils.printChatMessage(
                    new ChatComponentText(NEIClientUtils.translate("chat.chunkoverlay." + chunkOverlay)));
        }

    }

    public static void render(float frame) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT);
        GL11.glPushMatrix();
        Entity entity = Minecraft.getMinecraft().renderViewEntity;

        double interpPosX = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * frame;
        double interpPosY = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * frame;
        double interpPosZ = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * frame;

        int intOffsetX = (int) interpPosX;
        int intOffsetY = (int) interpPosY;
        int intOffsetZ = (int) interpPosZ;

        GL11.glTranslated(-interpPosX + intOffsetX, -interpPosY + intOffsetY, -interpPosZ + intOffsetZ);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        renderChunkBounds(entity, intOffsetX, intOffsetY, intOffsetZ);
        renderMobSpawnOverlay(entity, intOffsetX, intOffsetY, intOffsetZ);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    private static double getOverlayYOffset(World world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        if (block instanceof BlockSnow) {
            int meta = world.getBlockMetadata(x, y, z) & 7;
            return (1 + meta) / 8.0 + 0.02;
        }
        return 0.02;
    }

    private static void renderMobSpawnOverlay(Entity entity, int intOffsetX, int intOffsetY, int intOffsetZ) {
        if (mobOverlay == 0) {
            mobSpawnScan.clear();
            deleteMobSpawnList();
            return;
        }

        mobSpawnScan.update(entity, NEIClientConfig.getIntSetting("world.overlays.mobSpawnRadius"));
        final MobSpawnOverlayStyle style = MobSpawnOverlayStyle
                .parseStyle(NEIClientConfig.getSetting("world.overlays.mobSpawnStyle").getValue());
        if (!(mobSpawnList >= 0 && mobSpawnListChange == mobSpawnScan.getChangeCount() && mobSpawnListStyle == style)) {
            compileMobSpawnList(entity.worldObj, style);
        }

        beginMobSpawnDraw(style);
        GL11.glPushMatrix();
        GL11.glTranslated(
                mobSpawnScan.getMinX() - intOffsetX,
                mobSpawnScan.getMinY() - intOffsetY,
                mobSpawnScan.getMinZ() - intOffsetZ);
        GL11.glCallList(mobSpawnList);
        GL11.glPopMatrix();
        endMobSpawnDraw(style);
    }

    /**
     * Quad positions are relative to ({@link MobSpawnScan#getMinX}, {@link MobSpawnScan#getMinY},
     * {@link MobSpawnScan#getMinZ}).
     */
    private static void compileMobSpawnList(World world, MobSpawnOverlayStyle style) {
        if (mobSpawnList < 0) mobSpawnList = GLAllocation.generateDisplayLists(1);
        mobSpawnListChange = mobSpawnScan.getChangeCount();
        mobSpawnListStyle = style;

        GL11.glNewList(mobSpawnList, GL11.GL_COMPILE);
        final float alpha = style.computeAlpha();
        final Tessellator tess = Tessellator.instance;
        tess.startDrawing(style.glMode);
        final int span = mobSpawnScan.getSpan();
        for (int i = 0; i < span; i++) {
            for (int j = 0; j < span; j++) {
                final int column = i * span + j;

                for (int k = 0; k < mobSpawnScan.getColumnEnd(column); k++) {
                    if (mobSpawnScan.readSpawnMode(column, k) == 0) continue;

                    final double yOffset = getOverlayYOffset(
                            world,
                            mobSpawnScan.getMinX() + i,
                            mobSpawnScan.getMinY() + k,
                            mobSpawnScan.getMinZ() + j);
                    markedFace.moveTo(i, k + yOffset, j);
                    loadNeighbourModes(column, k);
                    markedFace.applyColor(style.colouring, alpha);
                    style.addVertices(markedFace);
                }
            }
        }
        tess.draw();
        GL11.glEndList();
    }

    private static void deleteMobSpawnList() {
        if (mobSpawnList < 0) return;

        GLAllocation.deleteDisplayLists(mobSpawnList);
        mobSpawnList = -1;
    }

    /** {@link #endMobSpawnDraw} undoes every change here except the texture binding. */
    private static void beginMobSpawnDraw(MobSpawnOverlayStyle style) {
        final ResourceLocation texture = style.texture;
        if (texture == null) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        } else {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        }
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glLineWidth(1.5F);
        if (style.isFilled()) {
            // a translucent quad that writes depth hides the overlay behind the quad. Turn depth writes off
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            // `EntityRenderer` leaves the alpha test on at GL_GREATER 0.1. Turn the test off, or a faint tint is
            // discarded
            GL11.glDisable(GL11.GL_ALPHA_TEST);
        }
    }

    private static void loadNeighbourModes(int column, int k) {
        final int span = mobSpawnScan.getSpan();
        final int i = column / span;
        final int j = column % span;
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                final int neighbourI = i + offsetX;
                final int neighbourJ = j + offsetZ;
                final boolean isInScan = neighbourI >= 0 && neighbourI < span && neighbourJ >= 0 && neighbourJ < span;
                final byte spawnMode = isInScan ? mobSpawnScan.readSpawnMode(neighbourI * span + neighbourJ, k)
                        : MobSpawnMarkedFace.OUTSIDE_SCAN;
                markedFace.setNeighbourMode(offsetX, offsetZ, spawnMode);
            }
        }
    }

    private static void endMobSpawnDraw(MobSpawnOverlayStyle style) {
        if (style.isFilled()) {
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(true);
        }
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    private static final Entity dummyEntity = new EntityPig(null);
    private static final AxisAlignedBB aabb = AxisAlignedBB.getBoundingBox(0, 0, 0, 0, 0, 0);

    static byte getSpawnMode(Chunk chunk, int x, int y, int z) {
        if (y >= chunk.worldObj.getHeight()) {
            return 0;
        }

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

        int worldMinY = worldMinHeight(entity.worldObj);
        int worldMaxY = worldMaxHeight(entity.worldObj);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glLineWidth(1.5F);

        final Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_LINES);

        for (int cx = -4; cx <= 4; cx++) for (int cz = -4; cz <= 4; cz++) {
            double x1 = ((entity.chunkCoordX + cx) << 4) - intOffsetX;
            double z1 = ((entity.chunkCoordZ + cz) << 4) - intOffsetZ;
            double x2 = x1 + 16;
            double z2 = z1 + 16;

            double dy = 128;
            double y1 = Math.floor(entity.posY - dy / 2);
            double y2 = y1 + dy;
            if (y1 < worldMinY) {
                y1 = worldMinY;
                y2 = worldMinY + dy;
            }
            if (y2 > worldMaxY) {
                y2 = worldMaxY;
                y1 = Math.max(worldMaxY - dy, worldMinY);
            }

            y1 -= intOffsetY;
            y2 -= intOffsetY;

            double dist = Math.pow(1.5, -(cx * cx + cz * cz));

            tess.setColorRGBA_F(0.9F, 0, 0, (float) dist);
            if (cx >= 0 && cz >= 0) {
                tess.addVertex(x2, y1, z2);
                tess.addVertex(x2, y2, z2);
            }
            if (cx >= 0 && cz <= 0) {
                tess.addVertex(x2, y1, z1);
                tess.addVertex(x2, y2, z1);
            }
            if (cx <= 0 && cz >= 0) {
                tess.addVertex(x1, y1, z2);
                tess.addVertex(x1, y2, z2);
            }
            if (cx <= 0 && cz <= 0) {
                tess.addVertex(x1, y1, z1);
                tess.addVertex(x1, y2, z1);
            }

            if (NEIModContainer.isCCLoaded()) {
                tess.setColorRGBA_F(0, 0.5F, 0.9F, (float) dist * 0.5F);
                int cubeBase = (int) Math.floor((y1 + intOffsetY) / 16.0) * 16;
                for (int cubeY = cubeBase; cubeY <= y2 + intOffsetY; cubeY += 16) {
                    double ry = cubeY - intOffsetY;
                    tess.addVertex(x1, ry, z1);
                    tess.addVertex(x2, ry, z1);
                    tess.addVertex(x1, ry, z2);
                    tess.addVertex(x2, ry, z2);
                    tess.addVertex(x1, ry, z1);
                    tess.addVertex(x1, ry, z2);
                    tess.addVertex(x2, ry, z1);
                    tess.addVertex(x2, ry, z2);
                }
            }

            if (cx == 0 && cz == 0) {
                if (chunkOverlay == 2) {
                    dy = 32;
                    y1 = Math.floor(entity.posY - dy / 2);
                    y2 = y1 + dy;
                    if (y1 < worldMinY) {
                        y1 = worldMinY;
                        y2 = worldMinY + dy;
                    }
                    if (y2 > worldMaxY) {
                        y2 = worldMaxY;
                        y1 = Math.max(worldMaxY - dy, worldMinY);
                    }

                    y1 -= intOffsetY;
                    y2 -= intOffsetY;
                    double centerX = (x1 + x2) / 2;
                    double centerZ = (z1 + z2) / 2;

                    tess.setColorRGBA_F(0.9F, 0.9F, 0, (float) dist);
                    tess.addVertex(centerX, y1, centerZ);
                    tess.addVertex(centerX, y2, centerZ);

                    tess.setColorRGBA_F(0, 0.9F, 0, 0.4F);
                    for (double y = (int) y1; y <= y2; y++) {
                        tess.addVertex(x2, y, z1);
                        tess.addVertex(x2, y, z2);
                        tess.addVertex(x1, y, z1);
                        tess.addVertex(x1, y, z2);
                        tess.addVertex(x1, y, z2);
                        tess.addVertex(x2, y, z2);
                        tess.addVertex(x1, y, z1);
                        tess.addVertex(x2, y, z1);
                    }
                    for (double h = 1; h <= 15; h++) {
                        tess.addVertex(x1 + h, y1, z1);
                        tess.addVertex(x1 + h, y2, z1);
                        tess.addVertex(x1 + h, y1, z2);
                        tess.addVertex(x1 + h, y2, z2);
                        tess.addVertex(x1, y1, z1 + h);
                        tess.addVertex(x1, y2, z1 + h);
                        tess.addVertex(x2, y1, z1 + h);
                        tess.addVertex(x2, y2, z1 + h);
                    }
                } else if (chunkOverlay == 3) {
                    int gx1;
                    int gz1;
                    if (getOregenPatternName().equals("EQUAL_SPACING")) {
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

                    tess.setColorRGBA_F(0, 0.9F, 0, 0.4F);
                    for (double y = (int) y1; y <= y2; y++) {
                        tess.addVertex(gx2, y, gz1);
                        tess.addVertex(gx2, y, gz2);
                        tess.addVertex(gx1, y, gz1);
                        tess.addVertex(gx1, y, gz2);
                        tess.addVertex(gx1, y, gz2);
                        tess.addVertex(gx2, y, gz2);
                        tess.addVertex(gx1, y, gz1);
                        tess.addVertex(gx2, y, gz1);
                    }
                    for (double h = 4; h <= 44; h += 4) {
                        if (h % 16 == 0) {
                            continue;
                        }
                        tess.addVertex(gx1 + h, y1, gz1);
                        tess.addVertex(gx1 + h, y2, gz1);
                        tess.addVertex(gx1 + h, y1, gz2);
                        tess.addVertex(gx1 + h, y2, gz2);
                        tess.addVertex(gx1, y1, gz1 + h);
                        tess.addVertex(gx1, y2, gz1 + h);
                        tess.addVertex(gx2, y1, gz1 + h);
                        tess.addVertex(gx2, y2, gz1 + h);
                    }

                    tess.setColorRGBA_F(0, 0, 0.9F, 0.4F);
                    gx1 += 23;
                    gz1 += 23;
                    gx2 = gx1 + 1;
                    gz2 = gz1 + 1;

                    tess.addVertex(gx1, y1, gz1);
                    tess.addVertex(gx1, y2, gz1);
                    tess.addVertex(gx2, y1, gz1);
                    tess.addVertex(gx2, y2, gz1);
                    tess.addVertex(gx1, y1, gz2);
                    tess.addVertex(gx1, y2, gz2);
                    tess.addVertex(gx2, y1, gz2);
                    tess.addVertex(gx2, y2, gz2);
                }
            }
        }

        tess.draw();
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }

    private static int worldMinHeight(World world) {
        if (NEIModContainer.isCCLoaded() && world instanceof IMinMaxHeight worldHeights) {
            return worldHeights.getMinHeight();
        } else {
            return 0;
        }
    }

    private static int worldMaxHeight(World world) {
        if (NEIModContainer.isCCLoaded() && world instanceof IMinMaxHeight worldHeights) {
            return worldHeights.getMaxHeight();
        } else {
            return world.getHeight();
        }
    }

    private static Callable<String> oregenPatternReader;

    private static String getOregenPatternName() {
        if (oregenPatternReader == null) {
            oregenPatternReader = resolveOregenPatternReader();
        }
        try {
            return oregenPatternReader.call();
        } catch (Exception ignored) {
            return "AXISSYMMETRICAL";
        }
    }

    private static Callable<String> resolveOregenPatternReader() {
        try {
            final Class<?> gtWorldGenerator = ReflectionHelper.getClass(
                    WorldOverlayRenderer.class.getClassLoader(),
                    "gregtech.common.GTWorldgenerator",
                    "gregtech.common.GT_Worldgenerator");

            try {
                // GT5-Unofficial >= 5.09.54.29 support
                final Method accessor = gtWorldGenerator.getMethod("getOregenPattern");
                return () -> ((Enum<?>) accessor.invoke(null)).name();
            } catch (NoSuchMethodException legacy) {
                // Older GT
                final Field oregenPattern = gtWorldGenerator.getDeclaredField("oregenPattern");
                oregenPattern.setAccessible(true);
                return () -> ((Enum<?>) oregenPattern.get(null)).name();
            }
        } catch (Exception ignored) {
            return () -> "AXISSYMMETRICAL";
        }
    }

    public static void load() {
        KeyManager.trackers.add(new WorldOverlayRenderer());
    }
}
