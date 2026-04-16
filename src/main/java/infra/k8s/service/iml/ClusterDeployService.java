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
    private final MetalLbService metalLbService;

    @Async
    @Transactional
    public void deployClusterAsync(Cluster cluster) {

        Long clusterId = cluster.getId();

        List<String> corePlaybooks = List.of(
                "common.yml",
                "master-init.yml",
                "workers-join.yml",
                "network-2.yml"
        );

        List<String> optionalPlaybooks = List.of(
                "containerd-registry-config.yml",
                "install-longhorn.yml",
                "install-metrics.yml",
                "install-metallb.yml",
                "install-ingress-nginx.yml"
        );

        int maxRetries = 2;
        int dem = 1;
        try {

            clusterLogService.sendLog(clusterId, " Bắt đầu deploy cluster gồm 8 playbook");


            String remoteInventoryPath =
                    ansibleService.uploadInventory(cluster);

            int attempt = 0;
            boolean coreSuccess = false;

            // =========================
            // CORE DEPLOY (retry toàn bộ)
            // =========================
            while (attempt <= maxRetries && !coreSuccess) {

                attempt++;

                log.warn("Rerun " + attempt);

                boolean failed = false;

                for (String playbook : corePlaybooks) {

                    clusterLogService.sendLog(clusterId,
                            "Chạy playbook số: " + dem + " tên " + playbook);

                    boolean ok = ansibleService.runPlaybook(
                            clusterId,
                            remoteInventoryPath,
                            playbook
                    );

                    if (!ok) {
                        failed = true;

                        clusterLogService.sendLog(clusterId,
                                "Fail: " + playbook);
                        break;
                    }

                    clusterLogService.sendLog(clusterId,
                            " OK: " + playbook);
                    dem++;
                }

                if (!failed) {
                    coreSuccess = true;
                    break;
                }

                if (attempt <= maxRetries) {
                    clusterLogService.sendLog(clusterId,
                            " Core lỗi rerun lại từ đầu");

                    ansibleService.runPlaybook(
                            clusterId,
                            remoteInventoryPath,
                            "reset.yml"
                    );

                    Thread.sleep(15000);
                    dem = 1;
                }
            }

            if (!coreSuccess) {
                throw new RuntimeException("Core deploy failed after retry");
            }

            // =========================
            // OPTIONAL ADDONS (fail vẫn chạy tiếp)
            // =========================
            for (String playbook : optionalPlaybooks) {

                clusterLogService.sendLog(clusterId,
                        "Cài addon: " + playbook);

                boolean ok = ansibleService.runPlaybook(
                        clusterId,
                        remoteInventoryPath,
                        playbook
                );

                if (!ok) {
                    clusterLogService.sendLog(clusterId,
                            " Addon failed (bỏ qua có thể tải lại sao): " + playbook);
                } else {
                    clusterLogService.sendLog(clusterId,
                            " Addon OK: " + playbook);
                }
            }

            // =========================
            // FINALIZE
            // =========================
            clusterLogService.sendLog(clusterId, " Fetch kubeconfig...");

            ansibleService.fetchAndStoreAdminConf(clusterId);

            clusterRepository.updateStatus(clusterId, ClusterStatus.ACTIVE);

            clusterManager.reloadCluster(clusterId);
            clusterManager.setActiveCluster(clusterId);


            clusterLogService.sendLog(clusterId,
                    " Cluster ACTIVE");

            try{
                metalLbService.autoGeneratePool(clusterId);
            }catch (Exception e){
                log.warn("MetalLB auto config failed but ignored", e);
            }

        } catch (Exception e) {

            clusterRepository.updateStatus(clusterId, ClusterStatus.FAILED);

            log.error("Deploy failed", e);

            clusterLogService.sendLog(clusterId,
                    "Deploy FAILED: " + e.getMessage());
        }

    }

}