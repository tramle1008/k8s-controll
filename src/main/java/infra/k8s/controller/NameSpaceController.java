package infra.k8s.controller;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import infra.k8s.dto.CreateNamespaceRequest;
import infra.k8s.dto.NamespaceDTO;
import infra.k8s.service.ClusterManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/namespaces")
@RequiredArgsConstructor
public class NameSpaceController {
    private final ClusterManager clusterManager;

    @GetMapping()
    public ResponseEntity<List<NamespaceDTO>> listNameSpace(){
        KubernetesClient client = clusterManager.requireActiveClient();
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

    @GetMapping("/project-namespaces")
    public ResponseEntity<List<String>> listProjectNamespaces() {
        KubernetesClient client = clusterManager.requireActiveClient();

        // Danh sách namespace system cần loại bỏ
        Set<String> excluded = Set.of(
                "kube-system",
                "kube-public",
                "kube-node-lease",
                "longhorn-system",
                "metallb-system"
        );

        List<String> namespaces = client.namespaces()
                .list()
                .getItems()
                .stream()
                .map(ns -> ns.getMetadata().getName())      // lấy name
                .filter(name -> !excluded.contains(name))   // loại namespace hệ thống
                .toList();

        return ResponseEntity.ok(namespaces);
    }

    // Tạo namespace
    @PostMapping()
    public ResponseEntity<?> createNamespace(
            @Valid @RequestBody CreateNamespaceRequest request) {

        try {
            KubernetesClient client = clusterManager.requireActiveClient();

            Namespace ns = new NamespaceBuilder()
                    .withNewMetadata()
                    .withName(request.getName())
                    .endMetadata()
                    .build();

            Namespace created = client.namespaces().create(ns);

            Map<String, Object> response = new HashMap<>();
            response.put("name", created.getMetadata().getName());
            response.put("status", created.getStatus().getPhase());
            response.put("creationTimestamp", created.getMetadata().getCreationTimestamp());

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(response);

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

    @DeleteMapping("/{name}")
    public ResponseEntity<String> deleteNamespace(@PathVariable String name) {
        KubernetesClient client = clusterManager.requireActiveClient();
        Namespace existing = client.namespaces().withName(name).get();
        if (existing == null) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Namespace không tồn tại: " + name);
        }
        client.namespaces().withName(name).delete();
        return ResponseEntity.ok("Đã xóa namespace: " + name);
    }

    @PostMapping(value = "/yaml", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> importNamespaceFromYaml(
            @RequestPart("file") @NotNull MultipartFile yamlFile) {

        if (yamlFile.isEmpty()) {
            return ResponseEntity.badRequest().body("File YAML không được để trống");
        }

        try (InputStream inputStream = yamlFile.getInputStream()) {
            KubernetesClient client = clusterManager.getActiveClient();

            // Cách 1: Load generic và cast (ổn định nhất, không phụ thuộc version)
            HasMetadata resource = (HasMetadata) client.load(inputStream).items();  // hoặc .item() nếu version mới

            if (resource == null || !(resource instanceof Namespace)) {
                return ResponseEntity.badRequest()
                        .body("File YAML không phải là định nghĩa Namespace hợp lệ (hoặc parse thất bại)");
            }

            Namespace namespace = (Namespace) resource;

            // Debug: In ra tên để kiểm tra
            String nsName = namespace.getMetadata().getName();
            System.out.println("Parsed namespace name: " + nsName);  // ← thêm dòng này để log

            if (nsName == null || nsName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Namespace name trong YAML bị thiếu hoặc rỗng");
            }

            // Kiểm tra tồn tại
            if (client.namespaces().withName(nsName).get() != null) {
                client.namespaces()
                        .resource(namespace)
                        .createOrReplace();  // apply (tương đương kubectl apply)

                return ResponseEntity.ok("Đã apply (cập nhật) namespace: " + nsName);
            }

            // Tạo mới
            client.namespaces().create(namespace);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body("Đã tạo namespace từ YAML: " + nsName);

        } catch (KubernetesClientException e) {
            if (e.getCode() == 409) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Namespace đã tồn tại");
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Lỗi Kubernetes: " + e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Không đọc được file YAML: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();  // ← in stack trace ra log để debug
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Lỗi server khi xử lý YAML: " + e.getMessage());
        }
    }
}
