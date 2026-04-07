package infra.k8s.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import infra.k8s.service.RegistryService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/images")
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

    @GetMapping("/tags")
    public ResponseEntity<?> getTags(@RequestParam String repo) {
        return ResponseEntity.ok(registryService.getTags(repo));
    }

    @DeleteMapping("/image")
    public ResponseEntity<?> deleteImage(
            @RequestParam String repo,
            @RequestParam String tag
    ) {
        try {
            registryService.deleteImage(repo, tag);

            return ResponseEntity.ok(Map.of(
                    "message", "Xóa image thành công"
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage()
            ));
        }
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

}
