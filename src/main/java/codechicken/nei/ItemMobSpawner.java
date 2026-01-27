package codechicken.nei;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemMobSpawner extends ItemBlock {

    private static final Map<Integer, EntityLiving> entityHashMap = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Integer, String> IDtoNameMap = new ConcurrentHashMap<>();

    public static int idPig = 90;
    private static boolean loaded = false;

    private static final ThreadLocal<PlacementContext> placementContext = ThreadLocal
            .withInitial(PlacementContext::new);

    private static class PlacementContext {
        int x, y, z;
    }

    public ItemMobSpawner() {
        super(Blocks.mob_spawner);
        hasSubtypes = true;
        MinecraftForgeClient.registerItemRenderer(this, new SpawnerRenderer());
    }

    @Override
    public IIcon getIconFromDamage(int meta) {
        return Blocks.mob_spawner.getBlockTextureFromSide(0);
    }

    public boolean onItemUse(ItemStack itemstack, EntityPlayer entityplayer, World world, int x, int y, int z, int side,
            float hitX, float hitY, float hitZ) {
        boolean placed = super.onItemUse(itemstack, entityplayer, world, x, y, z, side, hitX, hitY, hitZ);

        if (placed && world.isRemote) {
            TileEntityMobSpawner spawner = (TileEntityMobSpawner) world.getTileEntity(x, y, z);
            if (spawner != null) {
                String mobType = getMobTypeFromItemStack(itemstack);
                if (mobType != null) {
                    NEICPH.sendMobSpawnerID(x, y, z, mobType);
                    spawner.func_145881_a()
                            .setEntityName(mobType);
                }
            }
            return true;
        }
        return placed;
    }

    private String getMobTypeFromItemStack(ItemStack stack) {
        int meta = getValidMetaData(stack);
        return IDtoNameMap.get(meta);
    }

    private int getValidMetaData(ItemStack stack) {
        int meta = stack.getItemDamage();
        if (meta == 0 || !IDtoNameMap.containsKey(meta)) {
            return idPig;
        }
        return meta;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack itemstack, EntityPlayer player, List<String> tooltip,
            boolean advanced) {
        int meta = getValidMetaData(itemstack);
        EntityLiving entity = getEntity(meta);
        String mobName = IDtoNameMap.get(meta);

        if (entity != null && mobName != null) {
            EnumChatFormatting color = (entity instanceof IMob) ? EnumChatFormatting.DARK_RED
                    : EnumChatFormatting.DARK_AQUA;
            tooltip.add(color + mobName);
        }
    }

    public static EntityLiving getEntity(int id) {
        EntityLiving cached = entityHashMap.get(id);
        if (cached != null) {
            return cached;
        }

        loadSpawners();

        Class<?> entityClass = (Class<?>) EntityList.IDtoClassMapping.get(id);
        World world = NEIClientUtils.mc() != null ? NEIClientUtils.mc().theWorld : null;

        if (isInvalidEntityClass(entityClass)) {
            return cacheFallback(id);
        }

        EntityLiving entity = createEntityInstance(entityClass, world, id);
        if (entity != null) {
            entityHashMap.put(id, entity);
        }

        return entity;
    }

    private static boolean isInvalidEntityClass(Class<?> entityClass) {
        if (entityClass == null) {
            return false;
        }

        int modifiers = entityClass.getModifiers();
        if (Modifier.isAbstract(modifiers) || Modifier.isInterface(modifiers)) {
            NEIClientConfig.logger.warn(
                    "Skipping abstract entity class: " + entityClass.getName());
            return true;
        }

        return false;
    }

    private static EntityLiving createEntityInstance(Class<?> entityClass, World world, int id) {
        if (entityClass == null || world == null) {
            return getFallbackEntity(id);
        }

        try {
            return (EntityLiving) entityClass.getConstructor(World.class)
                    .newInstance(world);
        } catch (Throwable t) {
            logEntityCreationError(entityClass, id, t);
            return getFallbackEntity(id);
        }
    }

    private static void logEntityCreationError(Class<?> entityClass, int id, Throwable t) {
        if (entityClass == null) {
            NEIClientConfig.logger.error(
                    "Null class for entity (ID: {}, Name: {})",
                    id, IDtoNameMap.getOrDefault(id, "unknown"));
        } else {
            NEIClientConfig.logger.error(
                    "Error creating instance of entity: " + entityClass.getName(), t);
        }
    }

    private static EntityLiving cacheFallback(int id) {
        EntityLiving fallback = getFallbackEntity(id);
        if (fallback != null) {
            entityHashMap.put(id, fallback);
        }
        return fallback;
    }

    private static EntityLiving getFallbackEntity(int originalId) {
        EntityLiving pig = entityHashMap.get(idPig);
        if (pig == null) {
            World world = NEIClientUtils.mc() != null ? NEIClientUtils.mc().theWorld : null;

            if (world != null) {
                try {
                    pig = new EntityPig(world);
                    entityHashMap.put(idPig, pig);
                } catch (Exception e) {
                    NEIClientConfig.logger.error(
                            "Failed to create fallback pig entity", e);
                }
            }
        }

        if (pig == null && originalId != idPig) {
            NEIClientConfig.logger.warn(
                    "Using null fallback for entity ID: {}, original fallback ID: {}",
                    originalId, idPig);
        }

        return pig;
    }

    public static void clearEntityReferences() {
        entityHashMap.clear();
    }

    public static void loadSpawners() {
        if (loaded) {
            return;
        }

        synchronized (ItemMobSpawner.class) {
            if (loaded) {
                return;
            }

            try {
                for (Object entry : EntityList.classToStringMapping.entrySet()) {
                    Map.Entry<Class<? extends Entity>, String> mapEntry = (Map.Entry<Class<? extends Entity>, String>) entry;

                    Class<? extends Entity> clazz = mapEntry.getKey();
                    String name = mapEntry.getValue();

                    if (shouldRegisterEntity(clazz, name)) {
                        Integer id = (Integer) EntityList.classToIDMapping.get(clazz);
                        if (id != null) {
                            IDtoNameMap.put(id, name);
                            if ("Pig".equals(name)) {
                                idPig = id;
                            }
                        }
                    }
                }
                loaded = true;
            } catch (Exception e) {
                NEIClientConfig.logger.error("Error loading spawners", e);
            }
        }
    }

    private static boolean shouldRegisterEntity(Class<? extends Entity> clazz, String name) {
        if (clazz == null || name == null) {
            return false;
        }

        if (!EntityLiving.class.isAssignableFrom(clazz)) {
            return false;
        }

        if ("EnderDragon".equals(name)) {
            return false;
        }

        return true;
    }

    @Override
    public void getSubItems(Item item, CreativeTabs tab, List<ItemStack> list) {
        if (!NEIClientConfig.hasSMPCounterPart()) {
            list.add(new ItemStack(item));
        } else {
            for (int id : IDtoNameMap.keySet()) {
                list.add(new ItemStack(item, 1, id));
            }
        }
    }

    public static String getEntityNameForMeta(int meta) {
        loadSpawners();
        return IDtoNameMap.getOrDefault(meta, IDtoNameMap.getOrDefault(idPig, "Pig"));
    }

    public static boolean isValidEntityMeta(int meta) {
        loadSpawners();
        return IDtoNameMap.containsKey(meta) || meta == 0;
    }
}
