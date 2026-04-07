package infra.k8s.service;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import infra.k8s.dto.DeploymentPodsDto;
import infra.k8s.dto.statefulset.StatefulSetCreateRequest;

import java.util.List;

public interface StatefulSetService {
    String create(StatefulSetCreateRequest request);

    List<StatefulSet> getAllCluster();

    List<StatefulSet> getAll(String namespace);

    List<DeploymentPodsDto> getStatefulSetPods(String namespace, String name);

    void delete(String namespace, String name);
}
