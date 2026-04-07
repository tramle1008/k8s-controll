package infra.k8s.service;


import io.fabric8.kubernetes.api.model.PersistentVolume;
import org.springframework.web.multipart.MultipartFile;
import infra.k8s.dto.PV.PVRequest;
import infra.k8s.dto.PV.PVResponse;


import java.io.IOException;
import java.util.List;

public interface PVService {
    List<PVResponse> getAll();

    PersistentVolume create(PVRequest request);

    boolean delete(String name);

    void createFromFile(MultipartFile file) throws IOException;

    String getRawYaml(String name);
}