package infra.k8s.service;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import infra.k8s.dto.TopicNodeSummary;
import infra.k8s.event.ClusterSwitchedEvent;
import infra.k8s.module.ClusterNode;
import infra.k8s.module.NodeEvent;
import infra.k8s.repository.ClusterNodeRepository;
import infra.k8s.repository.ClusterRepository;
import infra.k8s.repository.NodeEventRepository;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NodeInformerService {

    private final ClusterNodeRepository clusterNodeRepository;
    private final ClusterRepository clusterRepository;
    private final NodeEventRepository nodeEventRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ClusterManager clusterManager;

    private SharedIndexInformer<Node> informer;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // ================= INIT =================

    @PostConstruct
    public void init() {
        log.info("### NodeInformerService initialized");
      //  startInformer();
    }

    @PreDestroy
    public void shutdown() {
        stopInformer();
        executor.shutdownNow();
        log.info("### NodeInformerService stopped");
    }

    // ================= CLUSTER SWITCH =================

    @EventListener
    public void onClusterSwitched(ClusterSwitchedEvent event) {
        log.info("### Cluster switched → clusterId={}", event.getClusterId());
        stopInformer();
        startInformer();
    }

    // ================= INFORMER CONTROL =================

    private void startInformer() {

        KubernetesClient client = clusterManager.requireActiveClient();
        if (client == null ) {
            log.warn("####No active cluster → informer NOT started");
            return;
        }



        informer = client.nodes().inform(new ResourceEventHandler<>() {

            @Override
            public void onAdd(Node node) {
                log.info("### Node ADDED: {}", node.getMetadata().getName());
                handleNodeUpdate(node);
            }

            @Override
            public void onUpdate(Node oldNode, Node newNode) {
                log.info("### Node UPDATED: {}", newNode.getMetadata().getName());
                handleNodeUpdate(newNode);
            }

            @Override
            public void onDelete(Node node, boolean deletedFinalStateUnknown) {
                log.info("### Node DELETED: {}", node.getMetadata().getName());
                handleNodeDelete(node);
            }
        });

        executor.submit(informer::run);

        log.info("### Reconciling existing nodes...");
        reconcileExistingNodes();
    }

    private void stopInformer() {
        if (informer != null) {
            log.info("### Stopping old Node informer...");
            informer.stop();
            informer = null;
        }
    }

    // ================= NODE HANDLER =================

    private void handleNodeUpdate(Node k8sNode) {

        Long clusterId = clusterManager.getActiveClusterId();
        if (clusterId == null) {
            log.warn("### No active cluster → skip node update");
            return;
        }

        String nodeName = k8sNode.getMetadata().getName();
        boolean ready = extractReadyStatus(k8sNode);
        Instant now = Instant.now();

        log.info("### Processing node {} (ready={}) clusterId={}", nodeName, ready, clusterId);

        ClusterNode node = clusterNodeRepository
                .findByClusterIdAndName(clusterId, nodeName)
                .orElseGet(() -> createNewClusterNode(clusterId, nodeName, ready, now));

        Boolean oldReady = node.getReady();
        boolean statusChanged = oldReady == null || oldReady != ready;

        node.setReady(ready);
        node.setUpdatedAt(now);

        if (statusChanged) {

            node.setUpdatedAt(now);

            if (ready) {
                node.setAlerted(false);
            }

            saveNodeEvent(node, String.valueOf(oldReady), ready, now);

            log.info("###NODE STATUS CHANGED {} → {}", oldReady, ready);
        }

        clusterNodeRepository.save(node);

        log.info("### Saved node {} to DB", nodeName);

        // ===== WebSocket push =====
        messagingTemplate.convertAndSend(
                "/topic/nodes",
                new TopicNodeSummary(node)
        );

        log.info("### Pushed node update to /topic/nodes → {}", nodeName);
    }

    private void handleNodeDelete(Node k8sNode) {

        Long clusterId = clusterManager.getActiveClusterId();
        if (clusterId == null) {
            log.warn("No active cluster → skip delete");
            return;
        }

        String nodeName = k8sNode.getMetadata().getName();

        clusterNodeRepository
                .findByClusterIdAndName(clusterId, nodeName)
                .ifPresent(node -> {

                    node.setReady(false);
                    node.setUpdatedAt(Instant.now());
                    clusterNodeRepository.save(node);

                    messagingTemplate.convertAndSend(
                            "/topic/nodes",
                            new TopicNodeSummary(node)
                    );

                    log.info("### Node {} marked deleted (clusterId={})", nodeName, clusterId);
                });
    }

    // ================= UTIL =================

    private boolean extractReadyStatus(Node node) {

        if (node.getStatus() == null ||
                node.getStatus().getConditions() == null) {
            return false;
        }

        return node.getStatus()
                .getConditions()
                .stream()
                .filter(c -> "Ready".equals(c.getType()))
                .findFirst()
                .map(c -> "True".equals(c.getStatus()))
                .orElse(false);
    }

    private ClusterNode createNewClusterNode(Long clusterId,
                                             String name,
                                             boolean ready,
                                             Instant now) {

        var cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() ->
                        new IllegalStateException("Cluster not found: " + clusterId));

        ClusterNode node = new ClusterNode();
        node.setCluster(cluster);
        node.setName(name);
        node.setReady(ready);
        node.setUpdatedAt(now);

        log.info("### Created new ClusterNode {}", name);

        return node;
    }

    private void saveNodeEvent(ClusterNode node,
                               String oldStatus,
                               boolean newStatus,
                               Instant now) {

        NodeEvent event = new NodeEvent();
        event.setNode(node);
        event.setOldStatus(oldStatus);
        event.setNewStatus(String.valueOf(newStatus));
        event.setCreatedAt(now);

        nodeEventRepository.save(event);

        log.info("### Saved NodeEvent {} → {}", oldStatus, newStatus);
    }

    private void reconcileExistingNodes() {

        KubernetesClient client = clusterManager.getActiveClient();
        Long clusterId = clusterManager.getActiveClusterId();

        if (client == null || clusterId == null) {
            log.warn("### No active cluster → skip reconcile");
            return;
        }

        List<Node> nodes = client.nodes().list().getItems();

        log.info("### Reconciling {} existing nodes", nodes.size());

        for (Node node : nodes) {
            handleNodeUpdate(node);
        }
    }
}