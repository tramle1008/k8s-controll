package infra.k8s.service.iml;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import infra.k8s.Context.ClusterStatus;
import infra.k8s.module.Cluster;
import infra.k8s.repository.ClusterRepository;
import infra.k8s.service.AnsibleService;
import infra.k8s.service.ClusterManager;
import infra.k8s.service.CryptoService;
import infra.k8s.service.Informer.ClusterLogService;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterDeployService {

    private final AnsibleService ansibleService;
    private final ClusterRepository clusterRepository;
    private final ClusterManager clusterManager;
    private final ClusterLogService clusterLogService;
    private final CryptoService cryptoService;


    @Async
    @Transactional
    public void deployClusterAsync(Cluster cluster) {

        Long clusterId = cluster.getId();

        try {

            clusterLogService.sendLog(clusterId,
                    " Bắt đầu triển khai cluster với id " + clusterId);

            String remoteInventoryPath =
                    ansibleService.uploadInventory(cluster);

            List<String> playbooks = List.of(
                    "common.yml",
                    "containerd-registry-config.yml",
                    "master-init.yml",
                    "workers-join.yml",
                    "network-2.yml",
                    "install-longhorn.yml",
                    "install-metrics.yml"
            );

            for (String playbook : playbooks) {

                clusterLogService.sendLog(clusterId,
                        "Chạy playbook: " + playbook);

                boolean ok = ansibleService.runPlaybook(
                        clusterId,
                        remoteInventoryPath,
                        playbook
                );

                if (!ok) {
                    throw new RuntimeException("Playbook failed: " + playbook);
                }

                clusterLogService.sendLog(clusterId,
                        "Hoàn thành: " + playbook);
            }

            clusterLogService.sendLog(clusterId,
                    "Fetch kubeconfig...");

            ansibleService.fetchAndStoreAdminConf(clusterId);

            clusterRepository.updateStatus(clusterId, ClusterStatus.ACTIVE);

            clusterManager.reloadCluster(clusterId);
            clusterManager.setActiveCluster(clusterId);

            clusterLogService.sendLog(clusterId,
                    "Cluster deploy hoàn tất. Cluster ACTIVE.");

        } catch (Exception e) {

            clusterRepository.updateStatus(clusterId, ClusterStatus.FAILED);

            log.error("Deploy failed", e);

            clusterLogService.sendLog(clusterId,
                    "Deploy FAILED: " + e.getMessage());
        }
    }
}