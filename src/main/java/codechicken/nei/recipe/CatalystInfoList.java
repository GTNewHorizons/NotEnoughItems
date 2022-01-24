package codechicken.nei.recipe;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIServerUtils;
import com.google.common.collect.ForwardingList;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

// Do not directly extend ArrayList, see Effective Java Item 16
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
    public boolean add(@Nonnull CatalystInfo catalystInfo) {
        if (contains(catalystInfo)) {
            NEIClientConfig.logger.info(String.format("catalyst %s is already registered to handler %s", catalystInfo.getStack().getDisplayName(), handlerID));
            return false;
        }
        super.add(catalystInfo);
        return true;
    }

    @Override
    public boolean contains(Object object) {
        ItemStack stack;
        if (object instanceof CatalystInfo) {
            stack = ((CatalystInfo) object).getStack();
            return contains(stack);
        }
        return false;
    }

    public boolean contains(ItemStack stack) {
        return catalystInfoList.stream().anyMatch(c -> NEIServerUtils.areStacksSameType(c.getStack(), stack));
    }

    public void sort() {
        super.sort((c1, c2) -> c2.getPriority() - c1.getPriority());
    }
}
