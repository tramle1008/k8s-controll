package infra.k8s.service.iml;

import infra.k8s.module.ClusterNode;
import infra.k8s.repository.ClusterNodeRepository;
import infra.k8s.service.ClusterManager;
import infra.k8s.service.MetalLbYamlBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetalLbService {

    private final ClusterNodeRepository nodeRepo;
    private final ClusterManager clusterManager;

    public String autoGeneratePool(Long clusterId) {
        // Lấy danh sách node trong cluster
        List<ClusterNode> nodes = nodeRepo.findByClusterId(clusterId);

        if (nodes.isEmpty()) {
            throw new RuntimeException("Cluster has no nodes");
        }

        // Lấy IP của node đầu tiên
        String ip = nodes.get(0).getIpAddress();  // VD: 192.168.235.147

        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            throw new RuntimeException("Invalid node IP format: " + ip);
        }

        String prefix = parts[0] + "." + parts[1] + "." + parts[2];

        // Auto chọn dải IP 200–250
        String range = prefix + ".200-" + prefix + ".250";

        log.info("Auto MetalLB range: {}", range);

        // Build YAML
        String yaml = MetalLbYamlBuilder.build(range);

        // Apply YAML lên cluster qua Fabric8
        KubernetesClient client = clusterManager.getClient(clusterId);
        client.load(new ByteArrayInputStream(yaml.getBytes()))
                .inNamespace("metallb-system")
                .createOrReplace();

        return range;
    }

    public void applyPool(Long clusterId, String range) {
        KubernetesClient client = clusterManager.getClient(clusterId);

        String yaml = MetalLbYamlBuilder.build(range);

        client.load(new ByteArrayInputStream(yaml.getBytes()))
                .inNamespace("metallb-system")
                .createOrReplace();
    }
}