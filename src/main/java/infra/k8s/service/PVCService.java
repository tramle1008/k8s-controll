package infra.k8s.service;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import org.springframework.web.multipart.MultipartFile;
import infra.k8s.dto.pvc.PVCRequest;
import infra.k8s.dto.pvc.PVCResponse;

import java.util.List;

public interface PVCService {
    List<PVCResponse> getAll();

    PersistentVolumeClaim create(PVCRequest request);

    boolean delete(String namespace, String name);

    void createFromFile(MultipartFile file);

    String getRawYaml(String namespace, String name);
}