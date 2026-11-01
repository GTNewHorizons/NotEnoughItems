package codechicken.nei;

import java.util.Collection;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

import org.apache.logging.log4j.Logger;

import codechicken.nei.api.IConfigureNEI;

final class PluginLoader {

    private PluginLoader() {}

    static void load(Collection<Class<?>> plugins, boolean parallel, Collection<IConfigureNEI> loadedPlugins,
            Logger logger) {
        if (parallel && canSetContextClassLoader()) {
            // Common pool workers use the system loader, which cannot see FML-only classes (old Log4j then throws
            // while logging a plugin's stack frames), and under a SecurityManager they forbid changing it.
            final ClassLoader classLoader = IConfigureNEI.class.getClassLoader();
            final ForkJoinPool pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors(), owner -> {
                final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(owner);
                worker.setContextClassLoader(classLoader);
                return worker;
            }, null, false);

            try {
                pool.submit(() -> plugins.parallelStream().forEach(clazz -> load(clazz, loadedPlugins, logger))).join();
            } finally {
                pool.shutdown();
            }
        } else {
            if (parallel) {
                logger.warn("Context class loader cannot be changed, loading plugins sequentially");
            }

            plugins.forEach(clazz -> load(clazz, loadedPlugins, logger));
        }
    }

    private static void load(Class<?> clazz, Collection<IConfigureNEI> loadedPlugins, Logger logger) {
        try {
            IConfigureNEI config = (IConfigureNEI) clazz.getConstructor().newInstance();
            config.loadConfig();
            loadedPlugins.add(config);
            logger.debug("Loaded {}", clazz.getName());
        } catch (Throwable e) {
            logger.error("Failed to Load {}", clazz.getName(), e);
        }
    }

    private static boolean canSetContextClassLoader() {
        final SecurityManager securityManager = System.getSecurityManager();

        if (securityManager == null) {
            return true;
        }

        try {
            securityManager.checkPermission(new RuntimePermission("setContextClassLoader"));
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }
}
