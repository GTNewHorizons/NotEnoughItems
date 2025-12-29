package codechicken.nei;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.lang.reflect.Modifier;

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
    public static int idPig;
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
        list.add(
                (e instanceof IMob ? EnumChatFormatting.DARK_RED : EnumChatFormatting.DARK_AQUA)
                        + IDtoNameMap.get(meta));
    }

public static EntityLiving getEntity(int ID) {
    EntityLiving e = entityHashMap.get(ID);
    if (e == null) {
        loadSpawners();
        Class<?> clazz = EntityList.IDtoClassMapping.get(ID);
        World world = NEIClientUtils.mc().theWorld;
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
            e = (EntityLiving) clazz.getConstructor(new Class[] { World.class }).newInstance(world);
        } catch (Throwable t) {
            if (clazz == null)
                NEIClientConfig.logger.error("Null class for entity (" + ID + ", " + IDtoNameMap.get(ID));
            else NEIClientConfig.logger.error("Error creating instance of entity: " + clazz.getName(), t);
            e = getEntity(idPig);
        }
        entityHashMap.put(ID, e);
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
        for (Map.Entry<Class<? extends Entity>, String> entry : EntityList.classToStringMapping.entrySet()) {
            final Class<? extends Entity> clazz = entry.getKey();
            if (EntityLiving.class.isAssignableFrom(clazz)) {
                Integer id = (Integer) EntityList.classToIDMapping.get(clazz);
                if (id == null) continue;
                String name = entry.getValue();
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
