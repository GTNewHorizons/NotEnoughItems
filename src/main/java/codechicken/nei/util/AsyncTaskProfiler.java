package codechicken.nei.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import codechicken.core.TaskProfiler;

public class AsyncTaskProfiler extends TaskProfiler {

    public ThreadLocal<TaskProfiler> threadedProfiler = ThreadLocal.withInitial(TaskProfiler::new);

    public Map<String, Long> times = new ConcurrentHashMap<>();

    @Override
    public void start(String section) {
        if (section == null) section = "<unnamed>";
        threadedProfiler.get().start(section);
    }

    @Override
    public void end() {
        TaskProfiler profiler = threadedProfiler.get();
        if (profiler != null) {
            profiler.end();
            if (profiler.times != null && !profiler.times.isEmpty()) {
                times.putAll(threadedProfiler.get().times);
            }
        }
    }

    @Override
    public List<ProfilerResult> getResults() {
        ArrayList<ProfilerResult> results = new ArrayList<>(times.size());
        long totalTime = times.values().stream().reduce(0L, Long::sum);
        for (Entry<String, Long> e : times.entrySet())
            results.add(new ProfilerResult(e.getKey(), e.getValue(), totalTime));
        return results;
    }

    @Override
    public void clear() {
        if (threadedProfiler.get().currentSection != null) threadedProfiler.get().end();
        threadedProfiler.get().clear();
        times.clear();
    }

    public void clearCurrent() {
        if (threadedProfiler.get().currentSection != null) threadedProfiler.get().end();
        threadedProfiler.get().clear();
    }
}
