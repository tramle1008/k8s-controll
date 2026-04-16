package infra.k8s.service;

import infra.k8s.dto.Registry.RepositoryDTO;
import org.jspecify.annotations.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface RegistryService {
    Object getTags(String repo);
    String handleUpload(MultipartFile file, String username, String appName, String tag) throws Exception;
    List<String> listImagesByUser(String username);
    void deleteRepository(String repo) throws Exception;

    List<RepositoryDTO> listAllRepositories() throws Exception;
}
