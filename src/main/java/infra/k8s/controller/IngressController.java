package infra.k8s.controller;

import infra.k8s.dto.ingress.IngressDTO;
import infra.k8s.service.ClusterManager;
import infra.k8s.service.IngressService;
import io.fabric8.kubernetes.api.model.networking.v1.IngressClass;
import io.fabric8.kubernetes.api.model.networking.v1.IngressClassBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ingress")
@RequiredArgsConstructor
public class IngressController {

    private final ClusterManager clusterManager;
    private final IngressService ingressService;

    /**
     * Tạo IngressClass từ JSON hoặc YAML gửi lên
     */
    @PostMapping("/create-class")
    public ResponseEntity<?> createIngressClass(@RequestBody Map<String, Object> payload) {
        try {
            // Ví dụ payload JSON:
            // {
            //   "name": "kong",
            //   "controller": "ingress-controllers.konghq.com/kong"
            // }

            KubernetesClient k8sClient = clusterManager.requireActiveClient();

            String name = (String) payload.get("name");
            String controller = (String) payload.get("controller");

            if (name == null || controller == null) {
                return ResponseEntity.badRequest().body("name và controller bắt buộc");
            }

            IngressClass ingressClass = new IngressClassBuilder()
                    .withNewMetadata()
                    .withName(name)
                    .endMetadata()
                    .withNewSpec()
                    .withController(controller)
                    .endSpec()
                    .build();

            k8sClient.network().v1().ingressClasses().create(ingressClass);

            return ResponseEntity.ok("IngressClass " + name + " đã được tạo");

        } catch (KubernetesClientException e) {
            return ResponseEntity.status(500)
                    .body("Lỗi khi tạo IngressClass: " + e.getMessage());
        }
    }

    @GetMapping()
    public ResponseEntity<List<IngressDTO>> listIngresses() {
        return ResponseEntity.ok(ingressService.listIngresses());
    }

    @DeleteMapping("/{namespace}/{name}")
    public ResponseEntity<?> deleteIngress(
            @PathVariable String namespace,
            @PathVariable String name
    ) {
        ingressService.deleteIngress(namespace, name);
        return ResponseEntity.ok("Deleted ingress " + name);
    }
}