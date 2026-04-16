package infra.k8s.service;

import infra.k8s.dto.statefulset.DatabaseInfo;
import org.jspecify.annotations.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PostgresExecService {
    @Nullable List<DatabaseInfo> listDatabases();

    String importSql(String databaseName, MultipartFile file, boolean onErrorStop);

    String executeQuery(String databaseName, String sql);
}
