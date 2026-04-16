package infra.k8s.service.iml;

import infra.k8s.dto.cluster.AddonStatusDTO;
import infra.k8s.repository.ClusterRepository;
import infra.k8s.service.AddonService;
import infra.k8s.service.AnsibleService;
import infra.k8s.service.ClusterManager;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@Service
@RequiredArgsConstructor
public class AddonServiceImp implements AddonService {
    private final ClusterManager clusterManager;
    private final ClusterRepository clusterRepository;
    private final AnsibleService ansibleService;
    @Override
    public List<AddonStatusDTO> checkAddons() {
        KubernetesClient client = clusterManager.getActiveClient();

        return List.of(
                checkAddon(client, "Longhorn", "longhorn-system", null),
                checkAddon(client, "Metrics Server", "kube-system", "metrics-server"),
                checkAddon(client, "MetalLB", "metallb-system", null),
                checkIngressController(client)
        );
    }

    private AddonStatusDTO checkAddon(KubernetesClient client,
                                      String name,
                                      String namespace,
                                      String keyword) {

        var pods = client.pods()
                .inNamespace(namespace)
                .list()
                .getItems();

        // filter nếu có keyword
        if (keyword != null) {
            pods = pods.stream()
                    .filter(p -> p.getMetadata().getName().contains(keyword))
                    .toList();
        }

        boolean installed = !pods.isEmpty();

        boolean healthy = pods.stream().allMatch(p ->
                p.getStatus() != null &&
                        p.getStatus().getContainerStatuses() != null &&
                        p.getStatus().getContainerStatuses().stream()
                                .allMatch(cs -> Boolean.TRUE.equals(cs.getReady()))
        );

        return new AddonStatusDTO(name, namespace, installed, healthy);
    }

    private AddonStatusDTO checkIngressController(KubernetesClient client) {
        String namespace = "ingress-nginx";
        String deployName = "ingress-nginx-controller";

        var deploy = client.apps().deployments()
                .inNamespace(namespace)
                .withName(deployName)
                .get();

        boolean installed = deploy != null;

        boolean healthy = false;

        if (installed && deploy.getStatus() != null) {
            Integer available = deploy.getStatus().getAvailableReplicas();
            Integer desired = deploy.getSpec().getReplicas();

            healthy = available != null && available.equals(desired);
        }

        return new AddonStatusDTO("NGINX Ingress", namespace, installed, healthy);
    }


    @Override
    public void installAddon(String name) {

        switch (name.toLowerCase()) {

            case "longhorn":
                runAddonPlaybook("install-longhorn.yml");
                break;

            case "metrics":
                runAddonPlaybook("install-metrics.yml");
                break;

            case "metallb":
                runAddonPlaybook("install-metallb.yml");
                break;

            case "ingress":
            case "ingress-nginx":
                runAddonPlaybook("install-ingress-nginx.yml");
                break;

            default:
                throw new RuntimeException("Addon không hợp lệ: " + name);
        }
    }

    private String buildInventoryPath(String clusterName) {
        return "/home/luanvan/k8s-ansible-2/inventory/inventory-" + clusterName + ".yaml";
    }
    private void runAddonPlaybook(String playbookName) {
        Long clusterId = clusterManager.getActiveClusterId();

        String clusterName = clusterRepository.findNameById(clusterId)
                .orElseThrow(() -> new RuntimeException("Cluster name not found"));

        String inventoryPath = buildInventoryPath(clusterName);

        boolean success = ansibleService.runPlaybook(
                clusterId,
                inventoryPath,
                playbookName
        );

        if (!success) {
            throw new RuntimeException("Install " + playbookName + " failed");
        }
    }

}