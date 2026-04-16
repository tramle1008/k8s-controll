package infra.k8s.service.iml;


import infra.k8s.service.ClusterManager;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
@RequiredArgsConstructor
@Service
public class Fabric8DeploymentService {

    private final ClusterManager clusterManager;

    public void applyDeployment(Deployment deployment) {
        KubernetesClient client = clusterManager.requireActiveClient();

        String namespace = deployment.getMetadata().getNamespace();
        if (namespace == null || namespace.isEmpty()) {
            namespace = "default";
        }
        String name = deployment.getMetadata().getName();

        // Xử lý containers
        deployment.getSpec().getTemplate().getSpec().getContainers().forEach(container -> {
            // Ports = 0 -> bỏ
            if (container.getPorts() != null) {
                container.setPorts(container.getPorts().stream()
                        .filter(p -> p.getContainerPort() != null && p.getContainerPort() > 0)
                        .toList());
            }

            // Env rỗng -> bỏ
            if (container.getEnv() != null && container.getEnv().isEmpty()) {
                container.setEnv(null);
            }

            if (container.getEnvFrom() != null) {
                container.setEnvFrom(
                        container.getEnvFrom()
                                .stream()
                                .filter(java.util.Objects::nonNull)
                                .toList()
                );
            }

            // VolumeMounts rỗng -> bỏ
            if (container.getVolumeMounts() != null && container.getVolumeMounts().isEmpty()) {
                container.setVolumeMounts(null);
            }

            // Resources rỗng -> bỏ
            ResourceRequirements res = container.getResources();
            if (res != null && (res.getLimits() == null || res.getLimits().isEmpty())
                    && (res.getRequests() == null || res.getRequests().isEmpty())) {
                container.setResources(null);
            }
        });

        // Volumes rỗng -> bỏ
        PodSpec podSpec = deployment.getSpec().getTemplate().getSpec();
        if (podSpec.getVolumes() != null && podSpec.getVolumes().isEmpty()) {
            podSpec.setVolumes(null);
        }

        try {
            Deployment existing = client.apps().deployments().inNamespace(namespace).withName(name).get();
            if (existing != null) {
                client.apps().deployments().inNamespace(namespace).withName(name).replace(deployment);
            } else {
                client.apps().deployments().inNamespace(namespace).create(deployment);
            }
        } catch (KubernetesClientException e) {
            throw new RuntimeException("Failed to apply deployment: " + e.getMessage(), e);
        }
    }
}