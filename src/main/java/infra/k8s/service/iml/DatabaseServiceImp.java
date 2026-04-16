package infra.k8s.service.iml;

import infra.k8s.service.ClusterManager;
import infra.k8s.service.DatabaseService;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseServiceImp implements DatabaseService {
    private final ClusterManager clusterManager;

    @Override
    public String importSqlToPostgresPod(
            String namespace,
            String podName,
            String databaseName,
            MultipartFile sqlFile
    ) throws Exception {

        KubernetesClient client = clusterManager.requireActiveClient();
        String remotePath = "/tmp/" + sqlFile.getOriginalFilename();

        // Upload file SQL
        try (var is = sqlFile.getInputStream()) {
            client.pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .file(remotePath)
                    .upload(is);
        }

        // Exec psql import
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        client.pods()
                .inNamespace(namespace)
                .withName(podName)
                .writingOutput(out)
                .writingError(err)
                .exec(
                        "psql",
                        "-U", "postgres",
                        "-d", databaseName,
                        "-f", remotePath
                );

        // Đợi psql chạy xong
        Thread.sleep(3000);

        if (!err.toString().isBlank()) {
            return " ERROR:\n" + err;
        }
        return "SUCCESS:\n" + out;
    }

}
