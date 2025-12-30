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

    private static final Map<Integer, EntityLiving> entityHashMap = new HashMap<>();
    private static final Map<Integer, String> IDtoNameMap = new HashMap<>();
    public static int idPig;  // 改回 public 静态变量，供 SpawnerRenderer 使用
    private static boolean loaded;

    public ItemMobSpawner() {
        super(Blocks.mob_spawner);
        hasSubtypes = true;
        MinecraftForgeClient.registerItemRenderer(this, new SpawnerRenderer());
    }

    /**
     * These are ASM translated from BlockMobSpawner
     */
    public static int placedX;
    public static int placedY;
    public static int placedZ;

    @Override
    public IIcon getIconFromDamage(int par1) {
        return Blocks.mob_spawner.getBlockTextureFromSide(0);
    }

    public boolean onItemUse(ItemStack itemstack, EntityPlayer entityplayer, World world, int x, int y, int z, int par7,
            float par8, float par9, float par10) {
        if (super.onItemUse(itemstack, entityplayer, world, x, y, z, par7, par8, par9, par10) && world.isRemote) {
            TileEntityMobSpawner tileentitymobspawner = (TileEntityMobSpawner) world
                    .getTileEntity(placedX, placedY, placedZ);
            if (tileentitymobspawner != null) {
                setDefaultTag(itemstack);
                String mobtype = IDtoNameMap.get(itemstack.getItemDamage());
                if (mobtype != null) {
                    // 直接调用静态方法，而不是通过 instance
                    NEICPH.sendMobSpawnerID(placedX, placedY, placedZ, mobtype);
                    tileentitymobspawner.func_145881_a().setEntityName(mobtype);
                }
            }
            return true;
        }
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack itemstack, EntityPlayer par2EntityPlayer, List<String> list, boolean par4) {
        setDefaultTag(itemstack);
        int meta = itemstack.getItemDamage();
        if (meta == 0) {
            meta = idPig;
        }
        Entity e = getEntity(meta);
        if (e != null) {
            list.add(
                    (e instanceof IMob ? EnumChatFormatting.DARK_RED : EnumChatFormatting.DARK_AQUA)
                            + IDtoNameMap.get(meta));
        }
    }

    public static EntityLiving getEntity(int ID) {
        EntityLiving e = entityHashMap.get(ID);
        if (e == null) {
            loadSpawners();
            Class<?> clazz = (Class<?>) EntityList.IDtoClassMapping.get(ID);
            World world = NEIClientUtils.mc() != null ? NEIClientUtils.mc().theWorld : null;
            
            if (clazz != null) {
                int modifiers = clazz.getModifiers();
                if (Modifier.isAbstract(modifiers) || Modifier.isInterface(modifiers)) {
                    NEIClientConfig.logger.warn("Skipping abstract entity class: " + clazz.getName());
                    e = getEntity(idPig);
                    if (e != null) {
                        entityHashMap.put(ID, e);
                    }
                    return e;
                }
            }
            
            try {
                if (clazz != null && world != null) {
                    e = (EntityLiving) clazz.getConstructor(new Class[] { World.class }).newInstance(world);
                } else {
                    // 如果无法创建实体，返回猪实体
                    e = getEntity(idPig);
                }
            } catch (Throwable t) {
                if (clazz == null) {
                    NEIClientConfig.logger.error("Null class for entity (" + ID + ", " + IDtoNameMap.get(ID));
                } else {
                    NEIClientConfig.logger.error("Error creating instance of entity: " + clazz.getName(), t);
                }
                e = getEntity(idPig);
            }
            if (e != null) {
                entityHashMap.put(ID, e);
            }
        }
        return e;
    }

    public static void clearEntityReferences() {
        entityHashMap.clear();
    }

    private void setDefaultTag(ItemStack itemstack) {
        if (!IDtoNameMap.containsKey(itemstack.getItemDamage())) itemstack.setItemDamage(idPig);
    }

    public static void loadSpawners() {
        if (loaded) return;
        loaded = true;
        for (Object entry : EntityList.classToStringMapping.entrySet()) {
            Map.Entry<Class<? extends Entity>, String> mapEntry = (Map.Entry<Class<? extends Entity>, String>) entry;
            final Class<? extends Entity> clazz = mapEntry.getKey();
            if (EntityLiving.class.isAssignableFrom(clazz)) {
                Integer id = (Integer) EntityList.classToIDMapping.get(clazz);
                if (id == null) continue;
                String name = mapEntry.getValue();
                if (name == null) continue;
                if (name.equals("EnderDragon")) continue;
                if (name.equals("Pig")) idPig = id;
                if (clazz != EntityPig.class || name.equals("Pig")) {
                    IDtoNameMap.put(id, name);
                }
            }
        }
    }

    @Override
    public void getSubItems(Item item, CreativeTabs tab, List<ItemStack> list) {
        if (!NEIClientConfig.hasSMPCounterPart()) list.add(new ItemStack(item));
        else for (int i : IDtoNameMap.keySet()) list.add(new ItemStack(item, 1, i));
    }
}
