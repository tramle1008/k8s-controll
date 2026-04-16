package infra.k8s.service;

import org.springframework.web.multipart.MultipartFile;
import infra.k8s.dto.deployment.DeploymentDto;
import infra.k8s.dto.DeploymentPodsDto;
import infra.k8s.dto.deployment.DeploymentCreateRequest;

import java.util.List;

public interface DeploymentService {
    String createDeployment(DeploymentCreateRequest request);
    List<DeploymentDto> getAllDeployments();
    String createDeploymentFromYaml(MultipartFile yamlFile);
    String getDeploymentRawYaml(String namespace, String name);

    List<DeploymentPodsDto> getDeploymentPods(String namespace, String name);

    void scaleDeployment(String namespace, String name, Integer replicas);

    void restartDeployment(String namespace, String deploymentName);
}