package codechicken.nei.config;

import java.io.File;

import net.minecraft.nbt.NBTTagCompound;

import codechicken.lib.config.ConfigTagParent;
import codechicken.nei.NEIServerUtils;

public class ConfigSet {

    private final File nbtFile;
    public NBTTagCompound nbt;
    public final ConfigTagParent config;

    public ConfigSet(File nbtFile) {
        this(nbtFile, null);
    }

    public ConfigSet(File nbtFile, ConfigTagParent config) {
        this.nbtFile = nbtFile;
        this.config = config;
        loadNBT();
    }

    public void loadNBT() {
        nbt = new NBTTagCompound();
        try {
            if (!nbtFile.getParentFile().exists()) nbtFile.getParentFile().mkdirs();
            if (!nbtFile.exists()) nbtFile.createNewFile();
            if (nbtFile.length() > 0) nbt = NEIServerUtils.readNBT(nbtFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveNBT() {
        try {
            NEIServerUtils.writeNBT(nbt, nbtFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
