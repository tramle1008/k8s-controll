package infra.k8s.controller;

import io.fabric8.kubernetes.api.model.PersistentVolume;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import infra.k8s.dto.PV.PVRequest;
import infra.k8s.dto.PV.PVResponse;
import infra.k8s.service.PVService;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pvs")
public class PVController {

    private final PVService pvService;

    public PVController(PVService pvService) {
        this.pvService = pvService;
    }

    @GetMapping
    public ResponseEntity<List<PVResponse>> getAllPVs() {
        return ResponseEntity.ok(pvService.getAll());
    }

    @PostMapping
    public ResponseEntity<?> createPV(@RequestBody PVRequest request) {
        PersistentVolume created = pvService.create(request);
        return ResponseEntity.ok(created);
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<?> deletePV(@PathVariable String name) {
        boolean deleted = pvService.delete(name);

        if (!deleted) {
            return ResponseEntity.badRequest().body("Xóa PV thất bại hoặc PV không tồn tại");
        }

        return ResponseEntity.ok(Map.of("message", "Deleted successfully"));
    }

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createPVFromFile(@RequestPart("file") MultipartFile file) throws IOException {
        pvService.createFromFile(file);
        return ResponseEntity.ok(Map.of("message", "Uploaded successfully"));
    }

    @GetMapping("/{name}/raw")
    public ResponseEntity<String> getRawYaml(@PathVariable String name) {
        String yaml = pvService.getRawYaml(name);

        if (yaml == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-yaml"))
                .header("Content-Disposition", "attachment; filename=\"" + name + ".yml\"")
                .body(yaml);
    }
}