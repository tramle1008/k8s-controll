package infra.k8s.service;

import org.springframework.web.multipart.MultipartFile;

public interface DatabaseService {
    String importSqlToPostgresPod(String namespace, String pod, String database, MultipartFile file) throws Exception;
}
