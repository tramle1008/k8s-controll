package infra.k8s.controller;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import infra.k8s.service.ClusterManager;
import infra.k8s.service.iml.MetalLbService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/metallb")
@RequiredArgsConstructor
public class MetalLbController {

    private final MetalLbService metalLbService;
    private final ClusterManager clusterManager;

    // ======================
    //  APPLY / CHANGE POOL
    // ======================
    @PostMapping("/{clusterId}/apply")
    public ResponseEntity<?> applyPool(
            @PathVariable Long clusterId,
            @RequestBody Map<String, Object> body) {

        Object rangeObj = body.get("range");
        if (rangeObj == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "range is required"
            ));
        }

        String range = rangeObj.toString();

        metalLbService.applyPool(clusterId, range);

        return ResponseEntity.ok(Map.of("appliedRange", range));
    }

    // ======================
    //  GET CURRENT POOL
    // ======================
    @GetMapping("/{clusterId}/pool")
    public ResponseEntity<?> getPool(@PathVariable Long clusterId) {

        var client = clusterManager.getClient(clusterId);

        GenericKubernetesResource pool = client
                .genericKubernetesResources("metallb.io/v1beta1", "IPAddressPool")
                .inNamespace("metallb-system")
                .withName("default")
                .get();

        if (pool == null) {
            return ResponseEntity.ok(Map.of("status", "not_found"));
        }

        // Extract spec
        Object specObj = pool.getAdditionalProperties().get("spec");
        if (!(specObj instanceof Map<?, ?> spec)) {
            return ResponseEntity.ok(Map.of("status", "invalid_spec"));
        }

        var addresses = spec.get("addresses");

        return ResponseEntity.ok(
                Map.of("addresses", addresses)
        );
    }

    @PostMapping("/{clusterId}/fixauto")
    public ResponseEntity<?> autopool(@PathVariable Long clusterId) {
        try {
            metalLbService.autoGeneratePool(clusterId);

            return ResponseEntity.ok("Auto generate IPAddressPool thành công");

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Tạo pool thất bại: " + e.getMessage());
        }
    }
}