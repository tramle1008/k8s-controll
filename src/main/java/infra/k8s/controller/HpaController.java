package infra.k8s.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import infra.k8s.dto.hpa.HpaCreateRequest;
import infra.k8s.dto.hpa.HpaDescribeDto;
import infra.k8s.service.HpaService;

import java.io.IOException;

@RestController
@RequestMapping("/api/hpa")
@RequiredArgsConstructor
public class HpaController {

    private final HpaService hpaService;

    @GetMapping
    public Object list() {
        return hpaService.list();
    }

    @GetMapping("/{namespace}/{name}")
    public Object get(
            @PathVariable String namespace,
            @PathVariable String name
    ) {
        return hpaService.get(namespace, name);
    }

    @PostMapping
    public Object create(@RequestBody HpaCreateRequest request) {
        return hpaService.create(request);
    }

    @DeleteMapping("/{namespace}/{name}")
    public void delete(
            @PathVariable String namespace,
            @PathVariable String name
    ) {
        hpaService.delete(namespace, name);
    }
    @PostMapping(value = "/yaml", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> createHpaFromYaml(
            @RequestPart("file") MultipartFile yamlFile) throws IOException {

        String createdName = hpaService.createHpaFromYaml(yamlFile);

        return ResponseEntity.ok("Created HPA: " + createdName);
    }

    @GetMapping("/{namespace}/{name}/describe")
    public ResponseEntity<HpaDescribeDto> describeHpa(
            @PathVariable String namespace,
            @PathVariable String name) {

        return ResponseEntity.ok(
                hpaService.describeHpa(namespace, name)
        );
    }
}