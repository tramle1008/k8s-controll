package rancher.k8s.cache;

import org.springframework.stereotype.Component;
import rancher.k8s.module.PodState;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PodStateCache {
    private final ConcurrentHashMap<String, PodState> cache = new ConcurrentHashMap<>();

    public PodState get(String key) {  // key = name + "-" + namespace
        return cache.get(key);
    }

    public void put(String key, PodState state) {
        cache.put(key, state);
    }

    public PodState remove(String key) {
        return cache.remove(key);
    }

    public Collection<PodState> getAll() {
        return cache.values();
    }

    public long getRunningCount() {
        return cache.values().stream()
                .filter(state -> "Running".equals(state.getPhase()))
                .count();
    }

    public long getAlertedCount() {
        return cache.values().stream()
                .filter(PodState::isAlerted)
                .count();
    }

    public long getTotalCount() {
        return cache.size();
    }
}