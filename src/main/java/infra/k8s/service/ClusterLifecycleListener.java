package infra.k8s.service;

import io.fabric8.kubernetes.client.KubernetesClient;

public interface ClusterLifecycleListener {
    void onClusterReady(KubernetesClient client);
}