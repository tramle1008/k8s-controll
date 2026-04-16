package infra.k8s.service.iml;

import infra.k8s.dto.statefulset.DatabaseInfo;
import infra.k8s.service.ClusterManager;
import infra.k8s.service.PostgresExecService;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostgresExecServiceIml implements PostgresExecService {

    private final ClusterManager clusterManager;

    private static final String POD_NAME = "postgres-0";
    private static final String NAMESPACE = "microservice";
    private static final String POSTGRES_USER = "postgres";

    // ======================================================
    // 1. LIST DATABASES
    // ======================================================
    public List<DatabaseInfo> listDatabases() {

        // SQL query thay cho \l
        String query =
                "SELECT datname, pg_get_userbyid(datdba), encoding, datcollate, datctype " +
                        "FROM pg_database WHERE datistemplate = false;";

        String[] cmd = {
                "psql",
                "-U", POSTGRES_USER,
                "-t", "-A",                 // không format bảng
                "-F", "|",                  // phân tách bằng |
                "-c", query
        };

        String output = execCommand(cmd);
        return parseListDatabases(output);
    }

    private List<DatabaseInfo> parseListDatabases(String raw) {
        List<DatabaseInfo> list = new ArrayList<>();
        String[] lines = raw.split("\n");

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            String[] p = line.split("\\|");
            if (p.length < 4) continue;

            list.add(new DatabaseInfo(
                    p[0].trim(),  // name
                    p[1].trim(),  // owner
                    p[2].trim(),  // encoding
                    p[3].trim()   // size (pretty)
            ));
        }
        return list;
    }


    // ======================================================
    // 2. IMPORT SQL FILE
    // ======================================================
    public String importSql(String databaseName, MultipartFile file, boolean onErrorStop) {

        if (file.isEmpty()) {
            return "❌ File SQL trống!";
        }

        KubernetesClient client = clusterManager.requireActiveClient();

        String[] cmd = {
                "psql",
                "-U", POSTGRES_USER,
                "-d", databaseName,
                "--set", "ON_ERROR_STOP=" + (onErrorStop ? "on" : "off")
        };

        try {
            byte[] sqlContent = file.getBytes();

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();

            try (ExecWatch watch = client.pods()
                    .inNamespace(NAMESPACE)
                    .withName(POD_NAME)
                    .writingOutput(stdout)
                    .writingError(stderr)
                    .exec(cmd)) {

                // Gửi file SQL qua stdin
                try (var stdin = watch.getInput()) {
                    stdin.write(sqlContent);
                    stdin.flush();
                }

                // Chờ psql xử lý xong
                Thread.sleep(1500);

                String err = stderr.toString(StandardCharsets.UTF_8);
                if (!err.isBlank()) {
                    return " Import thất bại:\n" + err;
                }

                return "Import thành công vào DB **" + databaseName + "**\n";

            }

        } catch (Exception e) {
            log.error("Import SQL error", e);
            return "Lỗi import SQL: " + e.getMessage();
        }
    }


    // ======================================================
    // 3. EXECUTE CUSTOM QUERY
    // ======================================================
    public String executeQuery(String databaseName, String sql) {

        String[] cmd = {
                "psql",
                "-U", POSTGRES_USER,
                "-d", databaseName,
                "-t", "-A",
                "-F", "|",
                "-c", sql
        };

        return execCommand(cmd);
    }


    // ======================================================
    // 4. Lệnh exec chung
    // ======================================================
    private String execCommand(String[] cmd) {

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        try (ExecWatch watch = clusterManager.requireActiveClient()
                .pods()
                .inNamespace(NAMESPACE)
                .withName(POD_NAME)
                .writingOutput(stdout)
                .writingError(stderr)
                .exec(cmd)) {

            // đợi command chạy xong một chút
            Thread.sleep(1000);

            String error = stderr.toString(StandardCharsets.UTF_8).trim();
            if (!error.isBlank()) {
                throw new RuntimeException(error);
            }

            return stdout.toString(StandardCharsets.UTF_8).trim();

        } catch (Exception e) {
            throw new RuntimeException("Exec failed: " + e.getMessage(), e);
        }
    }
}