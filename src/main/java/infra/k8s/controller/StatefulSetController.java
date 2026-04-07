package infra.k8s.controller;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import infra.k8s.dto.DeploymentPodsDto;
import infra.k8s.dto.mapper.StatefulSetDtoMapper;
import infra.k8s.dto.statefulset.StatefulSetCreateRequest;
import infra.k8s.dto.statefulset.StatefulSetDto;
import infra.k8s.service.StatefulSetService;
import infra.k8s.service.iml.StorageInstaller;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/statefulsets")
@RequiredArgsConstructor
public class StatefulSetController {

    private final StatefulSetService statefulSetService;
    private final StatefulSetDtoMapper mapper;
    private final StorageInstaller storageInstaller;



    @PostMapping
    public ResponseEntity<String> create(@RequestBody StatefulSetCreateRequest request) {
        String name = statefulSetService.create(request);
        return ResponseEntity.ok(name);
    }

    @GetMapping
    public ResponseEntity<List<StatefulSetDto>> getAll(
            @RequestParam(required = false) String namespace) {

        List<StatefulSet> statefulSets;

        if (namespace == null) {
            statefulSets = statefulSetService.getAllCluster();
        } else {
            statefulSets = statefulSetService.getAll(namespace);
        }

        List<StatefulSetDto> result = statefulSets.stream()
                .map(mapper::toDto)
                .toList();

        return ResponseEntity.ok(result);
    }

    @PostMapping("/install-storage")
    public ResponseEntity<String> install() throws Exception {
        storageInstaller.installLocalPath();
        return ResponseEntity.ok("local-path provisioner installed");
    }

    @GetMapping("/storage/local-path")
    public Map<String, Object> checkLocalPath() {

        boolean installed = storageInstaller.isLocalPathInstalled();

        return Map.of(
                "installed", installed,
                "name", "local-path"
        );
    }

    @GetMapping("/{namespace}/{name}/pods")
    public ResponseEntity<List<DeploymentPodsDto>> getStatefulSetPods(
            @PathVariable String namespace,
            @PathVariable String name) {

        return ResponseEntity.ok(
                statefulSetService.getStatefulSetPods(namespace, name)
        );
    }
    @DeleteMapping("/{namespace}/{name}")
    public ResponseEntity<String> deleteStatefulSet(
            @PathVariable String namespace,
            @PathVariable String name) {

        statefulSetService.delete(namespace, name);

        return ResponseEntity.ok("StatefulSet deleted: " + name);
    }
}