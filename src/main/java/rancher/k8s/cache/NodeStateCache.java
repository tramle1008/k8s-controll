package rancher.k8s.cache;

import org.springframework.stereotype.Component;
import rancher.k8s.module.NodeState;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Component
public class NodeStateCache {

    private final Map<String, NodeState> cache = new ConcurrentHashMap<>();

    public NodeState get(String name) {
        return cache.get(name);
    }

    public void put(String name, NodeState state) {
        cache.put(name, state);
    }

    public Collection<NodeState> getAll() {
        return cache.values();
    }
}