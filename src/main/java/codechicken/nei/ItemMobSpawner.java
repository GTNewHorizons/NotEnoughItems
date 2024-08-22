package codechicken.nei;

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
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.client.MinecraftForgeClient;

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

    @Override
    public void addInformation(ItemStack itemstack, EntityPlayer par2EntityPlayer, List<String> list, boolean par4) {
        setDefaultTag(itemstack);
        int meta = itemstack.getItemDamage();
        if (meta == 0) {
            meta = idPig;
        }
        Entity e = getEntity(meta);
        list.add("\u00A7" + (e instanceof IMob ? "4" : "3") + IDtoNameMap.get(meta));
    }

    public static EntityLiving getEntity(int ID) {
        EntityLiving e = entityHashMap.get(ID);
        if (e == null) {
            World world = NEIClientUtils.mc().theWorld;
            loadSpawners(world);
            Class<?> clazz = (Class<?>) EntityList.IDtoClassMapping.get(ID);
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

    public static void clearEntityReferences(World newWorld) {
        entityHashMap.values().removeIf(e -> e.worldObj != newWorld);
    }

    private void setDefaultTag(ItemStack itemstack) {
        if (!IDtoNameMap.containsKey(itemstack.getItemDamage())) itemstack.setItemDamage(idPig);
    }

    public static void loadSpawners(World world) {
        if (loaded) return;
        loaded = true;
        Map<Class<? extends Entity>, String> classToStringMapping = EntityList.classToStringMapping;
        @SuppressWarnings("unchecked")
        Map<Class<? extends Entity>, Integer> classToIDMapping = (Map<Class<? extends Entity>, Integer>) EntityList.classToIDMapping;
        for (Class<? extends Entity> eclass : classToStringMapping.keySet()) {
            if (!EntityLiving.class.isAssignableFrom(eclass)) continue;
            try {
                EntityLiving entityliving = (EntityLiving) eclass.getConstructor(new Class[] { World.class })
                        .newInstance(world);
                entityliving.isChild();

                int id = classToIDMapping.get(eclass);
                String name = classToStringMapping.get(eclass);

                if (name.equals("EnderDragon")) continue;

                IDtoNameMap.put(id, name);

                if (name.equals("Pig")) idPig = id;
            } catch (Throwable ignored) {}
        }

        IDtoNameMap.entrySet()
                .removeIf(e -> getEntity(e.getKey()).getClass() == EntityPig.class && !e.getValue().equals("Pig"));
    }

    @Override
    public void getSubItems(Item item, CreativeTabs tab, List<ItemStack> list) {
        if (!NEIClientConfig.hasSMPCounterPart()) list.add(new ItemStack(item));
        else for (int i : IDtoNameMap.keySet()) list.add(new ItemStack(item, 1, i));
    }
}
