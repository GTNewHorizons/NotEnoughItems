package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import com.google.common.collect.ForwardingList;

import codechicken.nei.NEIServerUtils;

public class CatalystInfoList extends ForwardingList<CatalystInfo> {

    private final String handlerID;
    private final List<CatalystInfo> catalystInfoList = new ArrayList<>();

    public CatalystInfoList(String handlerID) {
        this.handlerID = handlerID;
    }

    public CatalystInfoList(String handlerID, CatalystInfo catalystInfo) {
        this(handlerID);
        add(catalystInfo);
    }

    @Override
    protected List<CatalystInfo> delegate() {
        return catalystInfoList;
    }

    @Override
    public boolean add(@Nonnull CatalystInfo element) {
        return doAdd(element);
    }

    public boolean add(@Nonnull CatalystInfo catalystInfo, boolean overwrite) {
        if (overwrite || !contains(catalystInfo)) {
            return add(catalystInfo);
        }
        return false;
    }

    private boolean doAdd(@Nonnull CatalystInfo catalystInfo) {
        catalystInfoList
                .removeIf(c -> NEIServerUtils.areStacksSameTypeCraftingWithNBT(c.getStack(), catalystInfo.getStack()));
        catalystInfoList.add(catalystInfo);
        return true;
    }

    @Override
    public boolean contains(Object object) {
        if (object instanceof CatalystInfo) {
            ItemStack stack = ((CatalystInfo) object).getStack();
            return contains(stack);
        }
        return false;
    }

    public boolean contains(ItemStack stack) {
        return catalystInfoList.stream()
                .anyMatch(c -> NEIServerUtils.areStacksSameTypeCraftingWithNBT(c.getStack(), stack));
    }

    public boolean remove(ItemStack stack) {
        Iterator<CatalystInfo> iter = catalystInfoList.iterator();
        while (iter.hasNext()) {
            ItemStack next = iter.next().getStack();
            if (NEIServerUtils.areStacksSameTypeCraftingWithNBT(next, stack)) {
                iter.remove();
                return true;
            }
        }
        return false;
    }

    public void sort() {
        super.sort((c1, c2) -> c2.getPriority() - c1.getPriority());
    }
}
