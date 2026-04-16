package infra.k8s.controller;

import infra.k8s.dto.ClusterMetricsDto;
import infra.k8s.dto.CreateNamespaceRequest;
import infra.k8s.dto.NamespaceDTO;
import infra.k8s.dto.NodeSummary;
import infra.k8s.dto.cluster.AddonStatusDTO;
import infra.k8s.service.AddonService;
import infra.k8s.service.KubernetesApplyService;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;

import io.fabric8.kubernetes.client.KubernetesClientException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import infra.k8s.dto.node.NodeMetricsDto;
import infra.k8s.service.ClusterManager;
import infra.k8s.service.Informer.NodeMetricsService;
import infra.k8s.service.K8sNodeService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/k8s")
@RequiredArgsConstructor
public class KubernetesController {
    private final ClusterManager clusterManager;
    private final KubernetesApplyService kubernetesApplyService;
    private final K8sNodeService nodeService;
    private final NodeMetricsService nodeMetricsService;
    private final AddonService addonService;

//    apply bat ki yaml file
    @PostMapping("/apply")
    public ResponseEntity<?> applyYaml(@RequestParam("file") MultipartFile file) {
        String result = kubernetesApplyService.applyYaml(file);

        if (result.startsWith("Error:")) {
            return ResponseEntity.status(400).body(result);
        }

        return ResponseEntity.ok(result);
    }
    @GetMapping("/nodes")
    public ResponseEntity<List<NodeSummary>> listNodes() {
        return ResponseEntity.ok(nodeService.getAllNodes());
    }

    @GetMapping("/namespaces")
    public ResponseEntity<List<NamespaceDTO>> listNameSpace(){
        KubernetesClient client = clusterManager.getActiveClient();
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


    // Tạo namespace
    @PostMapping("/namespaces")
    public ResponseEntity<?> createNamespace(
            @Valid @RequestBody CreateNamespaceRequest request) {

        try {
            KubernetesClient client = clusterManager.getActiveClient();

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

    @DeleteMapping("/namespaces/{name}")
    public ResponseEntity<String> deleteNamespace(@PathVariable String name) {
        KubernetesClient client = clusterManager.getActiveClient();

        Namespace existing = client.namespaces().withName(name).get();
        if (existing == null) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Namespace không tồn tại: " + name);
        }

        client.namespaces().withName(name).delete();
        return ResponseEntity.ok("Đã xóa namespace: " + name);
    }



    @GetMapping("/metrics")
    public ClusterMetricsDto getClusterMetrics() {
        return nodeService.getClusterMetrics();
    }

    @GetMapping("/nodes/metrics")
    public List<NodeMetricsDto> getNodeMetrics() {
        return nodeMetricsService.collectCurrentMetrics();
    }

    @PostMapping("/{nodeName}/drain")
    public ResponseEntity<String> drainNode(@PathVariable String nodeName) {
        return ResponseEntity.ok(nodeService.drainNode(nodeName));
    }
    @PostMapping("/{nodeName}/uncordon")
    public ResponseEntity<String> uncordonNode(@PathVariable String nodeName) {
        return ResponseEntity.ok(nodeService.uncordonNode(nodeName));
    }

    @GetMapping("/addons")
    public ResponseEntity<List<AddonStatusDTO>> getAddons() {
        return ResponseEntity.ok(addonService.checkAddons());
    }

    @PostMapping("/addons/install/{name}")
    public ResponseEntity<?> installAddon(@PathVariable String name) {

        try {
            addonService.installAddon(name);
            return ResponseEntity.ok("Install " + name + " started");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}