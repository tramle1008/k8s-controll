package infra.k8s.service;

import org.jspecify.annotations.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface RegistryService {
    Object getTags(String repo);
    void deleteImage(String repo, String tag) throws Exception;
    String handleUpload(MultipartFile file, String username, String appName, String tag) throws Exception;

    List<String> listImagesByUser(String username);
}
