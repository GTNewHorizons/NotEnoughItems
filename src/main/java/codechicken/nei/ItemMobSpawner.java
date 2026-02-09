package codechicken.nei;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemMobSpawner extends ItemBlock {

    private static final Map<Integer, EntityLiving> entityHashMap = new ConcurrentHashMap<>();
    private static final Map<Integer, String> IDtoNameMap = new ConcurrentHashMap<>();
    private static final Map<Class<? extends Entity>, String> ENTITY_CLASS_TO_NAME_CACHE = new ConcurrentHashMap<>();

    public static int idPig = 90;
    private static volatile boolean loaded = false;

    // For asm
    public static int placedX;
    public static int placedY;
    public static int placedZ;

    public static void setLastPlacedPosition(int x, int y, int z) {
        placedX = x;
        placedY = y;
        placedZ = z;
    }

    public static int[] getLastPlacedPosition() {
        return new int[] { placedX, placedY, placedZ };
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

    @Override
    public boolean onItemUse(ItemStack itemstack, EntityPlayer entityplayer, World world, int x, int y, int z, int side,
            float hitX, float hitY, float hitZ) {

        placedX = x;
        placedY = y;
        placedZ = z;

        boolean placed = super.onItemUse(itemstack, entityplayer, world, x, y, z, side, hitX, hitY, hitZ);

        if (placed && !world.isRemote) {
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity instanceof TileEntityMobSpawner) {
                TileEntityMobSpawner spawner = (TileEntityMobSpawner) tileEntity;
                String mobType = getMobTypeFromItemStack(itemstack);
                if (mobType != null) {
                    spawner.func_145881_a().setEntityName(mobType);
                    world.markBlockForUpdate(x, y, z);
                    NEICPH.sendMobSpawnerID(x, y, z, mobType);
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
    public void addInformation(ItemStack itemstack, EntityPlayer player, List<String> tooltip, boolean advanced) {
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

        Class<?> entityClass = EntityList.IDtoClassMapping.get(id);
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
            return true;
        }

        int modifiers = entityClass.getModifiers();
        if (Modifier.isAbstract(modifiers) || Modifier.isInterface(modifiers)) {
            NEIClientConfig.logger.warn("Skipping abstract/interface entity class: {}", entityClass.getName());
            return true;
        }

        if (!EntityLiving.class.isAssignableFrom(entityClass)) {
            NEIClientConfig.logger.warn("Skipping non-EntityLiving class: {}", entityClass.getName());
            return true;
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private static EntityLiving createEntityInstance(Class<?> entityClass, World world, int id) {
        if (entityClass == null || world == null) {
            NEIClientConfig.logger.debug("World or entity class is null, using fallback for ID: {}", id);
            return getFallbackEntity(id);
        }

        try {
            Class<? extends EntityLiving> livingClass = (Class<? extends EntityLiving>) entityClass;
            return livingClass.getConstructor(World.class).newInstance(world);
        } catch (Throwable t) {
            logEntityCreationError(entityClass, id, t);
            return getFallbackEntity(id);
        }
    }

    private static void logEntityCreationError(Class<?> entityClass, int id, Throwable t) {
        String entityName = IDtoNameMap.getOrDefault(id, "unknown");
        if (entityClass == null) {
            NEIClientConfig.logger
                    .error("Failed to create entity instance: Class is null for ID {}, Name {}", id, entityName, t);
        } else {
            NEIClientConfig.logger.error(
                    "Failed to create instance of entity: Class {}, ID {}, Name {}",
                    entityClass.getName(),
                    id,
                    entityName,
                    t);
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
                    NEIClientConfig.logger.debug("Created fallback pig entity for ID: {}", originalId);
                } catch (Exception e) {
                    NEIClientConfig.logger
                            .error("Failed to create fallback pig entity for original ID: {}", originalId, e);
                }
            } else {
                NEIClientConfig.logger.warn("World is null, cannot create fallback entity for ID: {}", originalId);
            }
        }

        return pig;
    }

    public static void clearEntityReferences() {
        entityHashMap.clear();
        NEIClientConfig.logger.debug("Cleared all entity references from cache");
    }

    @SuppressWarnings("unchecked")
    public static void loadSpawners() {
        if (loaded) {
            return;
        }

        synchronized (ItemMobSpawner.class) {
            try {
                for (Map.Entry<Class<? extends Entity>, String> entry : ((Map<Class<? extends Entity>, String>) EntityList.classToStringMapping)
                        .entrySet()) {

                    Class<? extends Entity> clazz = entry.getKey();
                    String name = entry.getValue();

                    if (shouldRegisterEntity(clazz, name)) {
                        Integer id = (Integer) EntityList.classToIDMapping.get(clazz);
                        if (id != null) {
                            IDtoNameMap.put(id, name);
                            ENTITY_CLASS_TO_NAME_CACHE.put(clazz, name);

                            if ("Pig".equals(name)) {
                                idPig = id;
                                NEIClientConfig.logger.debug("Set pig entity ID to: {}", id);
                            }
                        }
                    }
                }
                loaded = true;
                NEIClientConfig.logger.info("Loaded {} spawner entity mappings", IDtoNameMap.size());

            } catch (Exception e) {
                NEIClientConfig.logger.error("Error loading spawners from entity list", e);
            }
        }
    }

    private static boolean shouldRegisterEntity(Class<? extends Entity> clazz, String name) {
        if (clazz == null || name == null || name.trim().isEmpty()) {
            return false;
        }

        if (!EntityLiving.class.isAssignableFrom(clazz)) {
            return false;
        }

        if ("EnderDragon".equals(name) || "Giant".equals(name)) {
            return false;
        }

        return true;
    }

    @Override
    public void getSubItems(Item item, CreativeTabs tab, List<ItemStack> list) {
        if (!NEIClientConfig.hasSMPCounterPart()) {
            list.add(new ItemStack(item));
        } else {
            loadSpawners();
            for (int id : IDtoNameMap.keySet()) {
                ItemStack stack = new ItemStack(item, 1, id);
                list.add(stack);
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

    public static Map<Integer, String> getRegisteredSpawnerEntities() {
        loadSpawners();
        return Collections.unmodifiableMap(IDtoNameMap);
    }

    public static boolean isEntityValidForSpawner(Class<? extends Entity> entityClass) {
        if (entityClass == null) return false;

        String name = ENTITY_CLASS_TO_NAME_CACHE.get(entityClass);
        if (name == null) {
            name = (String) EntityList.classToStringMapping.get(entityClass);
        }

        return name != null && shouldRegisterEntity(entityClass, name);
    }
}
