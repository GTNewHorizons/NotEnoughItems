package mezz.jei.search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import org.apache.commons.lang3.concurrent.ConcurrentRuntimeException;

import codechicken.core.TaskProfiler;
import codechicken.nei.NEIClientConfig;

public class AsyncPrefixedSearchable extends PrefixedSearchable {

    private static ExecutorService service;

    public static void startService() {
        service = Executors.newSingleThreadExecutor();
    }

    public static void endService() {
        if (service == null) {
            return;
        }
        service.shutdown();
        try {
            if (!service.awaitTermination(90, TimeUnit.SECONDS)) {
                service.shutdownNow();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            service.shutdownNow();
            Thread.currentThread().interrupt();
        }
        service = null;
    }

    private boolean firstBuild = true;
    private List<IIngredientListElement<ItemStack>> leftovers; // strictly written by service thread and read by main
                                                               // thread

    public AsyncPrefixedSearchable(ISearchStorage<IIngredientListElement<ItemStack>> searchStorage,
            PrefixInfo prefixInfo) {
        super(searchStorage, prefixInfo);
    }

    @Override
    public void submitAll(Collection<IIngredientListElement<ItemStack>> ingredients) {
        if (service != null) {
            service.submit(() -> {
                if (firstBuild) {
                    start();
                    firstBuild = false;
                }
                for (IIngredientListElement<ItemStack> ingredient : ingredients) {
                    try {
                        submit(ingredient);
                    } catch (ConcurrentRuntimeException e) {
                        NEIClientConfig.logger.error(
                                prefixInfo + " building failed on ingredient: " + ingredient.getDisplayName(),
                                e);
                        if (leftovers == null) {
                            this.leftovers = new ArrayList<>();
                        }
                        this.leftovers.add(ingredient);
                    }
                }
                stop();
            });
        } else {
            super.submitAll(ingredients);
        }
    }

    @Override
    public void start() {
        this.timer = new TaskProfiler();
        this.timer.start("Asynchronously building [" + prefixInfo.getDesc() + "] search tree");
    }

    @Override
    public void stop() {
        if (this.timer != null) {
            super.stop();
        }
        if (Minecraft.getMinecraft().func_152345_ab() && this.leftovers != null && !this.leftovers.isEmpty()) {
            NEIClientConfig.logger.info(
                    "{} search tree had {} errors, moving onto the main thread to process these errors.",
                    prefixInfo,
                    this.leftovers.size());
            this.leftovers.forEach(this::submit);
            this.leftovers = null;
        }
    }

}
