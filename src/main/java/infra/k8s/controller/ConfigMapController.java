package infra.k8s.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import infra.k8s.dto.configmap.ConfigMapCreateRequest;
import infra.k8s.dto.configmap.ConfigMapDto;
import infra.k8s.service.ConfigMapService;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/configmaps")
@RequiredArgsConstructor
public class ConfigMapController {

    private final ConfigMapService configMapService;



    @GetMapping()
    public ResponseEntity<List<ConfigMapDto>> getConfigMaps(
    ) {
        return ResponseEntity.ok(configMapService.getAll());
    }
    @PostMapping
    public ResponseEntity<Void> createConfigMap(
            @RequestBody ConfigMapCreateRequest dto
    ) {

        configMapService.create(dto);

        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> createFromFile(
            @RequestParam String namespace,
            @RequestPart MultipartFile file
    ) throws IOException {
        configMapService.createFromFile(namespace, file);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{namespace}/{name}")
    public ResponseEntity<Void> deleteConfigMap(
            @PathVariable String namespace,
            @PathVariable String name
    ) {
        configMapService.delete(namespace, name);
        return ResponseEntity.ok().build();
    }
}