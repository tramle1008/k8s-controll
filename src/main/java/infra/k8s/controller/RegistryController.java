package infra.k8s.controller;

import infra.k8s.dto.Registry.RepositoryDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import infra.k8s.service.RegistryService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/registry")
@RequiredArgsConstructor
public class RegistryController {
    private final RegistryService registryService;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("username") String username,
            @RequestParam("appName") String appName,
            @RequestParam(value = "tag", required = false) String tag
    ) {
        try {
            String image = registryService.handleUpload(
                    file,
                    username,
                    appName,
                    tag
            );

            return ResponseEntity.ok(Map.of("image", image));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/repo")
    public ResponseEntity<?> deleteRepo(
            @RequestParam String repo
    ) {
        try {
            registryService.deleteRepository(repo);
            return ResponseEntity.ok(Map.of("message", "Xóa repo thành công"));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/tags")
    public ResponseEntity<?> getTags(@RequestParam String repo) {
        return ResponseEntity.ok(registryService.getTags(repo));
    }

    @GetMapping("/list/{username}")
    public ResponseEntity<?> listImagesByUser(@PathVariable String username) {
        try {
            List<String> images = registryService.listImagesByUser(username);
            return ResponseEntity.ok(Map.of("repositories", images));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    @GetMapping("/repos")
    public ResponseEntity<List<RepositoryDTO>> getAllRepositories() throws Exception {
        List<RepositoryDTO> repos = registryService.listAllRepositories();
        return ResponseEntity.ok(repos);
    }

}
