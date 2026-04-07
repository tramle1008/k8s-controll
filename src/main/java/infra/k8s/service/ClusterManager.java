package infra.k8s.service;
import io.fabric8.kubernetes.client.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import infra.k8s.Context.ClusterStatus;
import infra.k8s.event.ClusterSwitchedEvent;
import infra.k8s.exception.ClusterNotReadyException;
import infra.k8s.module.Cluster;
import infra.k8s.repository.ClusterRepository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Service
@RequiredArgsConstructor
@Slf4j
public class ClusterManager {

    private final ClusterRepository clusterRepository;
    private final CryptoService cryptoService;
    private final ApplicationEventPublisher eventPublisher;

    // Lưu nhiều client
    private final Map<Long, KubernetesClient> clients = new ConcurrentHashMap<>();

    private Long activeClusterId;

    @PostConstruct
    public void init() {
        loadAllActiveClusters();
    }

    // Load tất cả cluster ACTIVE khi app start
    public void loadAllActiveClusters() {
        List<Cluster> clusters = clusterRepository.findByStatus(ClusterStatus.ACTIVE);

        for (Cluster cluster : clusters) {
            try {
                loadClientForCluster(cluster);
                log.info("Loaded client cho cluster {}", cluster.getName());
            } catch (Exception e) {
                log.error("Không load được cluster {}", cluster.getName(), e);
            }
        }

        if (!clients.isEmpty()) {
            activeClusterId = clients.keySet().iterator().next();
            log.info("Default active cluster: {}", activeClusterId);

            //  Publish event để start informer lần đầu
            eventPublisher.publishEvent(
                    new ClusterSwitchedEvent(this, activeClusterId)
            );
        }
    }

    // Tạo client từ kubeconfig DB
    public void loadClientForCluster(Cluster cluster) throws Exception {

        if (cluster.getEncryptedKubeconfig() == null) {
            log.warn("Cluster {} chưa có kubeconfig", cluster.getName());
            return;
        }
        String decrypted = cryptoService.decrypt(cluster.getEncryptedKubeconfig());
        Config config = Config.fromKubeconfig(decrypted);
        KubernetesClient client = new KubernetesClientBuilder()
                .withConfig(config)
                .build();
        KubernetesClient oldClient = clients.put(cluster.getId(), client);
        if (oldClient != null) {
            oldClient.close();
        }
    }

    // Gọi khi cluster mới provision xong
    public void reloadCluster(Long clusterId) throws Exception {
        Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow();
        loadClientForCluster(cluster);
    }

    public KubernetesClient getClient(Long clusterId) {
        return clients.get(clusterId);
    }
    public KubernetesClient getActiveClient() {
        if (activeClusterId == null) return null;
        return clients.get(activeClusterId);
    }
    public void setActiveCluster(Long clusterId) {
        this.activeClusterId = clusterId;
        log.info("Active cluster switched to {}", clusterId);

        //  publish event
        eventPublisher.publishEvent(
                new ClusterSwitchedEvent(this, clusterId)
        );
    }

    public boolean hasActiveCluster() {
        return activeClusterId != null;
    }
    public Long getActiveClusterId() {
        return activeClusterId;
    }
    public KubernetesClient requireActiveClient() {
        KubernetesClient client = getActiveClient();
        if (client == null) {
            throw new ClusterNotReadyException("No active cluster selected");
        }
        return client;
    }
}
