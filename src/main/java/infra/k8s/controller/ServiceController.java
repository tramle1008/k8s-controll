package infra.k8s.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import infra.k8s.dto.service.ServiceCreateRequest;
import infra.k8s.dto.service.ServiceService;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ServiceController {

    private final ServiceService serviceService;

    @PostMapping
    public ResponseEntity<String> createService(
            @RequestBody ServiceCreateRequest request
    ) {
        String result = serviceService.createService(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listServices() {

        return ResponseEntity.ok(serviceService.listServices());
    }

    @GetMapping("/{namespace}/{name}")
    public ResponseEntity<Map<String, Object>> getServiceDetail(
            @PathVariable String namespace,
            @PathVariable String name) {

        return ResponseEntity.ok(serviceService.getServiceDetail(namespace, name));
    }

    @DeleteMapping("/{namespace}/{name}")
    public ResponseEntity<String> deleteService(
            @PathVariable String namespace,
            @PathVariable String name) {

        return ResponseEntity.ok(serviceService.deleteService(namespace, name));
    }

    @PutMapping("/{namespace}/{name}")
    public ResponseEntity<String> updateService(
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestBody ServiceCreateRequest request) {

        return ResponseEntity.ok(serviceService.updateService(namespace, name, request));
    }

    @PostMapping("/yaml")
    public ResponseEntity<String> createServiceYaml(
            @RequestParam("file") MultipartFile file) throws IOException {

        return ResponseEntity.ok(serviceService.createServiceFromYaml(file));
    }
}