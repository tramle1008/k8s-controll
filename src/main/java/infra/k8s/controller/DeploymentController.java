package infra.k8s.controller;

import infra.k8s.dto.deployment.RolloutRestartRequest;
import infra.k8s.service.iml.Fabric8DeploymentService;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import infra.k8s.dto.DeploymentPodsDto;
import infra.k8s.dto.deployment.DeploymentCreateRequest;
import infra.k8s.dto.deployment.DeploymentDto;
import infra.k8s.dto.deployment.ScaleDeploymentRequest;
import infra.k8s.service.ClusterManager;
import infra.k8s.service.DeploymentService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/deployments")
public class DeploymentController {
   private final DeploymentService deploymentService;
    private final ClusterManager clusterManager;
    private final Fabric8DeploymentService deploymentFabric8Service;

    @PostMapping
    public ResponseEntity<String> createDeployment(
            @Valid @RequestBody DeploymentCreateRequest request) {
        try {
            String createdName = deploymentService.createDeployment(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body("Deployment tạo thành công: " + createdName);
        } catch (KubernetesClientException e) {
            return ResponseEntity
                    .status(e.getCode())
                    .body(e.getStatus().getMessage());
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());
        }
    }
    @GetMapping
    public ResponseEntity<List<DeploymentDto>> getAllDeployments() {
        List<DeploymentDto> deployments = deploymentService.getAllDeployments();
        return ResponseEntity.ok(deployments);
    }
    @PostMapping(value = "/yaml", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> createDeploymentFromYaml(
            @RequestPart("file") MultipartFile yamlFile) throws IOException {  // Optional override namespace
        String createdName = deploymentService.createDeploymentFromYaml(yamlFile);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Deployment from YAML: " + createdName);
    }

    @DeleteMapping("/{namespace}/{name}")
    public ResponseEntity<String> deleteDeployment(
            @PathVariable String name,
            @PathVariable String namespace) {
        KubernetesClient client = clusterManager.getActiveClient();
        List<StatusDetails> statusDetails = client.apps().deployments()
                .inNamespace(namespace)
                .withName(name)
                .delete();
        boolean deleted = statusDetails != null && !statusDetails.isEmpty();
        return ResponseEntity.ok(deleted ? "Deleted" : "Not found");
    }
    @GetMapping("/{namespace}/{name}/raw")
    public ResponseEntity<byte[]> downloadDeploymentYaml(
            @PathVariable String namespace,
            @PathVariable String name) {

        String yaml = deploymentService.getDeploymentRawYaml(namespace, name);

        String fileName = name + ".yaml";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(yaml.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/{namespace}/{name}/pods")
    public ResponseEntity<List<DeploymentPodsDto>> getDeploymentPods(
            @PathVariable String namespace,
            @PathVariable String name) {

        List<DeploymentPodsDto> pods = deploymentService.getDeploymentPods(namespace, name);

        return ResponseEntity.ok(pods);
    }

    @PutMapping("/{namespace}/{name}/scale")
    public ResponseEntity<Void> scaleDeployment(
            @PathVariable String namespace,
            @PathVariable String name,
            @RequestBody ScaleDeploymentRequest request
    ) {

        deploymentService.scaleDeployment(namespace, name, request.getReplicas());

        return ResponseEntity.ok().build();
    }


    @PostMapping("/apply")
    public ResponseEntity<String> applyDeployment(@RequestBody Deployment deployment) {
        if (deployment == null) {
            return ResponseEntity.badRequest().body("Deployment payload is null");
        }
        try {
            deploymentFabric8Service.applyDeployment(deployment);
            return ResponseEntity.ok("Deployment applied successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error applying deployment: " + e.getMessage());
        }
    }

    @PostMapping("/rollout-restart")
    public String restartDeployment(@RequestBody RolloutRestartRequest request) {

        deploymentService.restartDeployment(
                request.getNamespace(),
                request.getDeploymentName()
        );

        return "Rollout restart triggered!";
    }

}
