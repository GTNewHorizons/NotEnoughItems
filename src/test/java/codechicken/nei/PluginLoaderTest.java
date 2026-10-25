package codechicken.nei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import codechicken.nei.api.IConfigureNEI;

class PluginLoaderTest {

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void loadsPluginsAndLogsFailuresWithTheGameClassLoader(boolean parallel) throws Exception {
        ClassLoader workerClassLoader = new ClassLoader(null) {};
        Set<Thread> workers = ConcurrentHashMap.newKeySet();
        ForkJoinPool pool = new ForkJoinPool(2, owner -> {
            ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(owner);
            worker.setContextClassLoader(workerClassLoader);
            workers.add(worker);
            return worker;
        }, null, false);
        Logger logger = mock(Logger.class);
        doAnswer(invocation -> {
            assertSame(IConfigureNEI.class.getClassLoader(), Thread.currentThread().getContextClassLoader());
            return null;
        }).when(logger).error(eq("Failed to Load {}"), anyString(), any(Throwable.class));

        List<Class<?>> plugins = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            plugins.add(HealthyPlugin.class);
            plugins.add(ConstructorFailurePlugin.class);
            plugins.add(ConfigurationFailurePlugin.class);
        }
        List<IConfigureNEI> loaded = new CopyOnWriteArrayList<>();

        try {
            pool.submit(() -> PluginLoader.load(parallel ? plugins.parallelStream() : plugins.stream(), loaded, logger))
                    .get(10, TimeUnit.SECONDS);

            assertEquals(16, loaded.size());
            verify(logger, times(32)).error(eq("Failed to Load {}"), anyString(), any(Throwable.class));
            workers.forEach(worker -> assertSame(workerClassLoader, worker.getContextClassLoader()));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void restoresTheClassLoaderEvenIfErrorLoggingThrows() {
        Thread thread = Thread.currentThread();
        ClassLoader previousClassLoader = thread.getContextClassLoader();
        ClassLoader workerClassLoader = new ClassLoader(null) {};
        Logger logger = mock(Logger.class);
        doThrow(new LinkageError("Logging failed")).when(logger)
                .error(eq("Failed to Load {}"), anyString(), any(Throwable.class));

        try {
            thread.setContextClassLoader(workerClassLoader);
            assertThrows(
                    LinkageError.class,
                    () -> PluginLoader.load(Stream.of(ConfigurationFailurePlugin.class), new ArrayList<>(), logger));
            assertSame(workerClassLoader, thread.getContextClassLoader());
        } finally {
            thread.setContextClassLoader(previousClassLoader);
        }
    }

    public static class HealthyPlugin implements IConfigureNEI {

        public HealthyPlugin() {
            checkClassLoader();
        }

        @Override
        public void loadConfig() {
            checkClassLoader();
        }

        private static void checkClassLoader() {
            if (Thread.currentThread().getContextClassLoader() != IConfigureNEI.class.getClassLoader()) {
                throw new IllegalStateException("Plugin cannot see the game classloader");
            }
        }

        @Override
        public String getName() {
            return "Test plugin";
        }

        @Override
        public String getVersion() {
            return "1";
        }
    }

    public static class ConstructorFailurePlugin extends HealthyPlugin {

        public ConstructorFailurePlugin() {
            throw new IllegalStateException("Constructor failed");
        }
    }

    public static class ConfigurationFailurePlugin extends HealthyPlugin {

        @Override
        public void loadConfig() {
            super.loadConfig();
            throw new IllegalStateException("Configuration failed");
        }
    }
}
