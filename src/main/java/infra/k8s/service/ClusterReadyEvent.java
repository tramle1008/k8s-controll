package infra.k8s.service;

import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ClusterReadyEvent extends ApplicationEvent {
    private final KubernetesClient client;
    private final Long clusterId;  // ← Thêm clusterId

    public ClusterReadyEvent(Object source, KubernetesClient client, Long clusterId) {
        super(source);
        this.client = client;
        this.clusterId = clusterId;
    }
}