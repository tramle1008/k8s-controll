package rancher.k8s.controller;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.NodeMetrics;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.NodeMetricsList;
import io.fabric8.kubernetes.client.KubernetesClient;

import io.fabric8.kubernetes.client.KubernetesClientException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rancher.k8s.dto.CreateNamespaceRequest;
import rancher.k8s.dto.NamespaceDTO;
import rancher.k8s.dto.NodeSummary;
import rancher.k8s.dto.PodDTO;
import rancher.k8s.service.K8sNodeService;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/k8s")
@RequiredArgsConstructor
public class KubernetesController {
    private final KubernetesClient client;
    private final K8sNodeService nodeService;


    // list pods (giữ nguyên nếu bạn thích, hoặc chuyển sang Fabric8)
    @GetMapping("/pods")
    public ResponseEntity<List<io.fabric8.kubernetes.api.model.Pod>> listPods() {
        return ResponseEntity.ok(client.pods().inAnyNamespace().list().getItems());
    }

    //  list deployments
    @GetMapping("/deployments")
    public ResponseEntity<List<Deployment>> listDeployments() {
        return ResponseEntity.ok(client.apps().deployments().inAnyNamespace().list().getItems());
    }


    @GetMapping("/nodes")
    public ResponseEntity<List<NodeSummary>> listNodes() {
        return ResponseEntity.ok(nodeService.getAllNodes());
    }

    @GetMapping("/namespaces")
    public ResponseEntity<List<NamespaceDTO>> listNameSpace(){

        List<NamespaceDTO> namespaces = client.namespaces()
                .list()
                .getItems()
                .stream()
                .map(ns -> new NamespaceDTO(
                        ns.getMetadata().getName(),
                        ns.getStatus() != null ? ns.getStatus().getPhase() : "Unknown",
                        ns.getMetadata().getCreationTimestamp()
                ))
                .toList();

        return ResponseEntity.ok(namespaces);
    }
    @GetMapping("/namespaces/{namespace}/pods")
    public ResponseEntity<List<PodDTO>> listPodsByNamespace(
            @PathVariable String namespace) {

        List<PodDTO> pods = client.pods()
                .inNamespace(namespace)
                .list()
                .getItems()
                .stream()
                .map(pod -> PodDTO.builder()
                        .name(pod.getMetadata().getName())
                        .namespace(pod.getMetadata().getNamespace())
                        .status(pod.getStatus() != null ? pod.getStatus().getPhase() : "Unknown")
                        .podIp(pod.getStatus() != null ? pod.getStatus().getPodIP() : null)
                        .nodeName(pod.getSpec() != null ? pod.getSpec().getNodeName() : null)
                        .build())
                .toList();

        return ResponseEntity.ok(pods);
    }

    // Tạo namespace
    @PostMapping("/namespaces")
    public ResponseEntity<?> createNamespace(
            @Valid @RequestBody CreateNamespaceRequest request) {

        try {
            Namespace ns = new NamespaceBuilder()
                    .withNewMetadata()
                    .withName(request.getName())
                    .endMetadata()
                    .build();

            Namespace created = client.namespaces().create(ns);

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(created.getMetadata().getName());

        } catch (KubernetesClientException e) {
            if (e.getCode() == 409) {
                return ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body("Namespace đã tồn tại");
            }
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Thất bại : " + e.getMessage());
        }
    }

    //  Apply Deployment từ YAML/JSON vào namespace cụ thể
    @PostMapping("/deployments/apply")
    public ResponseEntity<Deployment> applyDeployment(
            @RequestParam String namespace,
            @RequestBody String yamlOrJson) {

        try {
            InputStream inputStream = new ByteArrayInputStream(yamlOrJson.getBytes(StandardCharsets.UTF_8));
            Deployment deployment = client.apps().deployments()
                    .inNamespace(namespace)
                    .load(inputStream)
                    .createOrReplace();

            return ResponseEntity.ok(deployment);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(null);
        }
    }

    // Xóa Deployment
    @DeleteMapping("/deployments/{name}")
    public ResponseEntity<String> deleteDeployment(
            @PathVariable String name,
            @RequestParam String namespace) {

        List<StatusDetails> statusDetails = client.apps().deployments()
                .inNamespace(namespace)
                .withName(name)
                .delete();
        boolean deleted = statusDetails != null && !statusDetails.isEmpty();
        return ResponseEntity.ok(deleted ? "Deleted" : "Not found");
    }
// metrics-server
    @GetMapping("/top-nodes")
    public ResponseEntity<List<NodeMetrics>> topNodes() {
        try {
            NodeMetricsList metricsList = client.top().nodes().metrics();
            return ResponseEntity.ok(metricsList.getItems());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(null);
        }
    }

}