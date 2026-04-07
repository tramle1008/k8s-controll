package infra.k8s.service.iml;

import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import infra.k8s.dto.DeploymentPodsDto;
import infra.k8s.dto.mapper.StatefulSetMapper;
import infra.k8s.dto.statefulset.StatefulSetCreateRequest;
import infra.k8s.service.ClusterManager;
import infra.k8s.service.StatefulSetService;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatefulSetImpl implements StatefulSetService {

    private final ClusterManager clusterManager;

    private final StatefulSetMapper mapper;
    @Override
    public String create(StatefulSetCreateRequest request) {
        KubernetesClient client = clusterManager.requireActiveClient();
        StatefulSet statefulSet = mapper.toStatefulSet(request);

        StatefulSet created = client.apps()
                .statefulSets()
                .inNamespace(request.getMetadata().getNamespace())
                .resource(statefulSet)
                .create();

        return created.getMetadata().getName();
    }

    @Override
    public List<StatefulSet> getAllCluster() {

        KubernetesClient client = clusterManager.requireActiveClient();

        return client.apps()
                .statefulSets()
                .inAnyNamespace()
                .list()
                .getItems();
    }

    @Override
    public List<StatefulSet> getAll(String namespace) {

        KubernetesClient client = clusterManager.requireActiveClient();

        return client.apps()
                .statefulSets()
                .inNamespace(namespace)
                .list()
                .getItems();
    }

    @Override
    public List<DeploymentPodsDto> getStatefulSetPods(String namespace, String name) {

        KubernetesClient client = clusterManager.requireActiveClient();

        StatefulSet sts = client.apps()
                .statefulSets()
                .inNamespace(namespace)
                .withName(name)
                .get();

        if (sts == null) {
            throw new RuntimeException("StatefulSet not found");
        }

        Map<String, String> labels = sts.getSpec()
                .getSelector()
                .getMatchLabels();

        return client.pods()
                .inNamespace(namespace)
                .withLabels(labels)
                .list()
                .getItems()
                .stream()
                .map(pod -> {

                    int restartCount = pod.getStatus()
                            .getContainerStatuses()
                            .stream()
                            .mapToInt(cs -> cs.getRestartCount())
                            .sum();

                    return new DeploymentPodsDto(
                            pod.getMetadata().getName(),
                            pod.getStatus().getPhase(),
                            pod.getSpec().getNodeName(),
                            restartCount,
                            pod.getStatus().getPodIP()
                    );

                })
                .toList();
    }

    @Override
    public void delete(String namespace, String name) {

        KubernetesClient client = clusterManager.requireActiveClient();

        List<StatusDetails> deleted = client.apps()
                .statefulSets()
                .inNamespace(namespace)
                .withName(name)
                .delete();

        if (deleted == null || deleted.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "StatefulSet not found: " + name
            );
        }
    }
}
