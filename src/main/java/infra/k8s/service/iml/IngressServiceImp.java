package infra.k8s.service.iml;

import infra.k8s.dto.ingress.IngressDTO;
import infra.k8s.service.ClusterManager;
import infra.k8s.service.IngressService;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IngressServiceImp implements IngressService {
    private final ClusterManager clusterManager;

    public List<IngressDTO> listIngresses() {
        KubernetesClient client = clusterManager.getActiveClient();

        return client.network()
                .v1()
                .ingresses()
                .inAnyNamespace()
                .list()
                .getItems()
                .stream()
                .map(ing -> new IngressDTO(
                        ing.getMetadata().getName(),
                        ing.getMetadata().getNamespace(),
                        ing.getSpec() != null && ing.getSpec().getRules() != null
                                ? ing.getSpec().getRules().stream()
                                .map(r -> r.getHost())
                                .toList()
                                : List.of(),
                        ing.getStatus() != null
                                && ing.getStatus().getLoadBalancer() != null
                                && ing.getStatus().getLoadBalancer().getIngress() != null
                                && !ing.getStatus().getLoadBalancer().getIngress().isEmpty()
                                ? ing.getStatus().getLoadBalancer().getIngress().get(0).getIp()
                                : null
                ))
                .toList();
    }

    @Override
    public void deleteIngress(String namespace, String name) {
        KubernetesClient client = clusterManager.getActiveClient();

        List<StatusDetails> result = client.network()
                .v1()
                .ingresses()
                .inNamespace(namespace)
                .withName(name)
                .delete();

        if (result == null || result.isEmpty()) {
            throw new RuntimeException("Delete ingress failed: " + name);
        }
    }
}
