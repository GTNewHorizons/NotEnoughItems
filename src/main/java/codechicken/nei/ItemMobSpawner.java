package codechicken.nei;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private static final Map<Integer, EntityLiving> ENTITY_CACHE = new HashMap<>();
    private static final Map<Integer, String> ID_TO_NAME_MAP = new HashMap<>();
    private static final int DEFAULT_ID_PIG = 90; // 猪的默认ID
    public static final int PIG_ID = DEFAULT_ID_PIG;
    private static boolean loaded = false;

    public ItemMobSpawner() {
        super(Blocks.mob_spawner);
        this.hasSubtypes = true;
        MinecraftForgeClient.registerItemRenderer(this, new SpawnerRenderer());
    }

    public static int placedX;
    public static int placedY;
    public static int placedZ;

    @Override
    public IIcon getIconFromDamage(int damage) {
        return Blocks.mob_spawner.getBlockTextureFromSide(0);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, 
                             int x, int y, int z, int side, 
                             float hitX, float hitY, float hitZ) {
        boolean result = super.onItemUse(stack, player, world, x, y, z, side, hitX, hitY, hitZ);
        
        if (result && world.isRemote) {
            TileEntityMobSpawner spawner = (TileEntityMobSpawner) world.getTileEntity(placedX, placedY, placedZ);
            if (spawner != null) {
                ensureValidDamage(stack);
                String mobType = ID_TO_NAME_MAP.get(stack.getItemDamage());
                if (mobType != null && spawner.func_145881_a() != null) {
                    // 发送数据包
                    if (NEICPH.instance != null) {
                        NEICPH.instance.sendMobSpawnerID(placedX, placedY, placedZ, mobType);
                    }
                    spawner.func_145881_a().setEntityName(mobType);
                }
            }
        }
        return result;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltip, boolean advanced) {
        ensureValidDamage(stack);
        int meta = stack.getItemDamage();
        if (meta == 0) {
            meta = PIG_ID;
        }
        
        String mobName = ID_TO_NAME_MAP.get(meta);
        if (mobName != null) {
            Entity e = getEntity(meta);
            EnumChatFormatting color = (e instanceof IMob) 
                ? EnumChatFormatting.DARK_RED 
                : EnumChatFormatting.DARK_AQUA;
            tooltip.add(color + mobName);
        }
    }

    public static EntityLiving getEntity(int id) {
        EntityLiving entity = ENTITY_CACHE.get(id);
        if (entity == null) {
            loadSpawners();
            
            Class<?> entityClass = EntityList.getClassFromID(id);
            if (entityClass == null) {
                NEIClientConfig.logger.warn("Invalid entity ID: " + id);
                return getEntity(PIG_ID);
            }
            
            int modifiers = entityClass.getModifiers();
            if (Modifier.isAbstract(modifiers) || Modifier.isInterface(modifiers)) {
                NEIClientConfig.logger.warn("Skipping abstract entity class: " + entityClass.getName());
                entity = getEntity(PIG_ID);
                ENTITY_CACHE.put(id, entity);
                return entity;
            }
            
            try {
                World world = NEIClientUtils.getClientWorld();
                if (world != null) {
                    entity = (EntityLiving) entityClass.getConstructor(World.class).newInstance(world);
                } else {
                    NEIClientConfig.logger.error("World is null, cannot create entity instance");
                    return getEntity(PIG_ID);
                }
            } catch (Exception e) {
                NEIClientConfig.logger.error("Error creating instance of entity: " + entityClass.getName(), e);
                return getEntity(PIG_ID);
            }
            
            ENTITY_CACHE.put(id, entity);
        }
        return entity;
    }

    public static void clearEntityReferences() {
        ENTITY_CACHE.clear();
    }

    private void ensureValidDamage(ItemStack stack) {
        int damage = stack.getItemDamage();
        if (!ID_TO_NAME_MAP.containsKey(damage) || damage == 0) {
            stack.setItemDamage(PIG_ID);
        }
    }

    public static synchronized void loadSpawners() {
        if (loaded) return;
        
        for (Object entry : EntityList.IDtoClassMapping.entrySet()) {
            Map.Entry<Integer, Class<?>> mapEntry = (Map.Entry<Integer, Class<?>>) entry;
            Integer id = mapEntry.getKey();
            Class<?> clazz = mapEntry.getValue();
            
            if (EntityLiving.class.isAssignableFrom(clazz) && !Modifier.isAbstract(clazz.getModifiers())) {
                String name = EntityList.getStringFromID(id);
                if (name != null && !"EnderDragon".equals(name)) {
                    if ("Pig".equals(name)) {
                        // PIG_ID 已经在静态变量中设置
                    }
                    ID_TO_NAME_MAP.put(id, name);
                }
            }
        }
        
        loaded = true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item item, CreativeTabs tab, List<ItemStack> list) {
        if (!NEIClientConfig.hasSMPCounterPart()) {
            list.add(new ItemStack(item));
        } else {
            for (int id : ID_TO_NAME_MAP.keySet()) {
                if (id > 0) { // 跳过无效ID
                    list.add(new ItemStack(item, 1, id));
                }
            }
        }
    }
}
