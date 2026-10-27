package codechicken.nei;

import java.util.Collection;
import java.util.stream.Stream;

import org.apache.logging.log4j.Logger;

import codechicken.nei.api.IConfigureNEI;

final class PluginLoader {

    private PluginLoader() {}

    static void load(Stream<Class<?>> plugins, Collection<IConfigureNEI> loadedPlugins, Logger logger) {
        final ClassLoader classLoader = IConfigureNEI.class.getClassLoader();

        plugins.forEach(clazz -> {
            final Thread thread = Thread.currentThread();
            final ClassLoader previousClassLoader = thread.getContextClassLoader();

            // Common-pool workers may use the system loader, which cannot see FML-only classes. Keep FML's loader
            // through error logging too: old Log4j can otherwise throw while resolving a plugin's stack frames.
            try {
                thread.setContextClassLoader(classLoader);
                try {
                    IConfigureNEI config = (IConfigureNEI) clazz.getConstructor().newInstance();
                    config.loadConfig();
                    loadedPlugins.add(config);
                    logger.debug("Loaded {}", clazz.getName());
                } catch (Throwable e) {
                    logger.error("Failed to Load {}", clazz.getName(), e);
                }
            } finally {
                thread.setContextClassLoader(previousClassLoader);
            }
        });
    }
}
