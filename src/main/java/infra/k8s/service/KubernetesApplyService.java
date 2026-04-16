package infra.k8s.service;
import org.springframework.web.multipart.MultipartFile;

public interface KubernetesApplyService {
    String applyYaml(MultipartFile file);
}