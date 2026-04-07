package infra.k8s.service;

import io.fabric8.kubernetes.api.model.Secret;
import org.jspecify.annotations.Nullable;
import org.springframework.web.multipart.MultipartFile;
import infra.k8s.dto.secret.SecretRequest;
import infra.k8s.dto.secret.SecretResponse;

import java.io.IOException;
import java.util.List;

public interface SecretService {
    @Nullable List<SecretResponse> getAll();

    Secret create(SecretRequest request);

    boolean delete(String namespace, String name);

    void createFromFile(MultipartFile file) throws IOException;

    String getRawYaml(String namespace, String name);
}
