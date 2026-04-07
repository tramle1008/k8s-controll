package infra.k8s.controller;

import io.fabric8.kubernetes.api.model.Secret;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import infra.k8s.dto.secret.SecretRequest;
import infra.k8s.dto.secret.SecretResponse;
import infra.k8s.service.SecretService;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/secrets")
public class SecretController {

    private final SecretService secretService;

    public SecretController(SecretService secretService) {
        this.secretService = secretService;
    }

    @GetMapping
    public ResponseEntity<List<SecretResponse>> getAllSecrets() {
        return ResponseEntity.ok(secretService.getAll());
    }

    @PostMapping
    public ResponseEntity<?> createSecret(@RequestBody SecretRequest request) {
        Secret created = secretService.create(request);
        return ResponseEntity.ok(created);
    }

    @DeleteMapping("/{namespace}/{name}")
    public ResponseEntity<?> deleteSecret(
            @PathVariable String namespace,
            @PathVariable String name
    ) {
        boolean deleted = secretService.delete(namespace, name);

        if (!deleted) {
            return ResponseEntity.badRequest().body("Xóa Secret thất bại hoặc Secret không tồn tại");
        }

        return ResponseEntity.ok(Map.of("message", "Deleted successfully"));
    }

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createSecretFromFile(@RequestPart("file") MultipartFile file) throws IOException {
        secretService.createFromFile(file);
        return ResponseEntity.ok(Map.of("message", "Uploaded successfully"));
    }

    @GetMapping("/{namespace}/{name}/raw")
    public ResponseEntity<String> getRawYaml(
            @PathVariable String namespace,
            @PathVariable String name
    ) {
        String yaml = secretService.getRawYaml(namespace, name);

        if (yaml == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/yaml"))
                .body(yaml);
    }
}