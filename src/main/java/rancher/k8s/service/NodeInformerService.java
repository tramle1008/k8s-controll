package rancher.k8s.service;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import rancher.k8s.cache.NodeStateCache;
import rancher.k8s.module.NodeEvent;
import rancher.k8s.module.NodeState;
import rancher.k8s.module.NodeStatus;
import rancher.k8s.repository.NodeEventRepository;
import rancher.k8s.repository.NodeStatusRepository;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class NodeInformerService {

    private final NodeStateCache cache;
    private final NodeStatusRepository statusRepo;
    private final NodeEventRepository eventRepo;
    private final SimpMessagingTemplate messagingTemplate;

    private final KubernetesClient client = new KubernetesClientBuilder().build();


    @PostConstruct
    public void start() {

        SharedIndexInformer<Node> informer =
                client.informers().sharedIndexInformerFor(Node.class, 0);

        informer.addEventHandler(new ResourceEventHandler<>() {

            @Override
            public void onAdd(Node node) {
                handleNodeUpdate(node);
            }

            @Override
            public void onUpdate(Node oldNode, Node newNode) {
                handleNodeUpdate(newNode);
            }

            @Override
            public void onDelete(Node node, boolean deletedFinalStateUnknown) {
                handleNodeDelete(node);
            }
        });

        // Chạy informer async
        new Thread(informer::run).start();

        // Reconcile sau khi start
        reconcileExistingNodes();
    }


    private void handleNodeUpdate(Node node) {

        String name = node.getMetadata().getName();

        boolean ready = node.getStatus().getConditions().stream()
                .filter(c -> "Ready".equals(c.getType()))
                .findFirst()
                .map(c -> "True".equals(c.getStatus()))
                .orElse(false);

        Instant now = Instant.now();

        NodeState state = cache.get(name);

        if (state == null) {
            state = new NodeState(name, ready, now);
            cache.put(name, state);

            saveToDatabase(name, ready, now);
            saveEvent(name, null, ready, now);

            messagingTemplate.convertAndSend("/topic/nodes", state);
            return;
        }

        state.setLastSeenTime(now);

        if (state.isReady() != ready) {

            state.setReady(ready);
            state.setLastTransitionTime(now);
            state.setAlerted(false);

            saveToDatabase(name, ready, now);
            saveEvent(name, state, ready, now);

            messagingTemplate.convertAndSend("/topic/nodes", state);
        }
    }

    private void saveToDatabase(String name, boolean ready, Instant now) {
        NodeStatus status = new NodeStatus();
        status.setName(name);
        status.setReady(ready);
        status.setUpdatedAt(now);
        statusRepo.save(status);
    }
    private void handleNodeDelete(Node node) {
        String name = node.getMetadata().getName();

        NodeState state = cache.get(name);
        if (state != null) {
            state.setReady(false);
            state.setLastTransitionTime(Instant.now());

            messagingTemplate.convertAndSend("/topic/nodes", state);
        }
    }

    private void saveEvent(String name, NodeState oldState,
                           boolean newStatus, Instant now) {

        NodeEvent event = new NodeEvent();
        event.setNodeName(name);
        event.setOldStatus(oldState == null ? "UNKNOWN" :
                String.valueOf(oldState.isReady()));
        event.setNewStatus(String.valueOf(newStatus));
        event.setCreatedAt(now);

        eventRepo.save(event);
    }

    private void reconcileExistingNodes() {

        try {
            Thread.sleep(2000); // đợi informer sync
        } catch (InterruptedException ignored) {}

        List<Node> nodes = client.nodes().list().getItems();

        for (Node node : nodes) {
            handleNodeUpdate(node);
        }

        System.out.println("Reconcile completed. Synced "
                + nodes.size() + " nodes.");
    }
}