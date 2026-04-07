package infra.k8s.controller;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import infra.k8s.dto.pvc.PVCRequest;
import infra.k8s.dto.pvc.PVCResponse;
import infra.k8s.service.PVCService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pvcs")
public class PVCController {

    private final PVCService pvcService;

    public PVCController(PVCService pvcService) {
        this.pvcService = pvcService;
    }

    @GetMapping
    public ResponseEntity<List<PVCResponse>> getAllPVCs() {
        return ResponseEntity.ok(pvcService.getAll());
    }

    @PostMapping
    public ResponseEntity<?> createPVC(@RequestBody PVCRequest request) {
        PersistentVolumeClaim created = pvcService.create(request);
        return ResponseEntity.ok(created);
    }

    @DeleteMapping("/{namespace}/{name}")
    public ResponseEntity<?> deletePVC(
            @PathVariable String namespace,
            @PathVariable String name
    ) {
        boolean deleted = pvcService.delete(namespace, name);

        if (!deleted) {
            return ResponseEntity.badRequest()
                    .body("Xóa PVC thất bại hoặc PVC không tồn tại");
        }

        return ResponseEntity.ok(Map.of("message", "Deleted successfully"));
    }

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createPVCFromFile(@RequestPart("file") MultipartFile file) {
        pvcService.createFromFile(file);
        return ResponseEntity.ok(Map.of("message", "Uploaded successfully"));
    }

    @GetMapping("/{namespace}/{name}/raw")
    public ResponseEntity<String> getRawYaml(
            @PathVariable String namespace,
            @PathVariable String name
    ) {
        String yaml = pvcService.getRawYaml(namespace, name);

        if (yaml == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-yaml"))
                .header("Content-Disposition", "attachment; filename=\"" + name + ".yml\"")
                .body(yaml);
    }
}