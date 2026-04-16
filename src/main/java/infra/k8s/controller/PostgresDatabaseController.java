package infra.k8s.controller;

import infra.k8s.dto.statefulset.DatabaseInfo;
import infra.k8s.service.PostgresExecService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/postgres")
@RequiredArgsConstructor
public class PostgresDatabaseController {

    private final PostgresExecService postgresService;

    // 1. Liệt kê databases (gọi khi click Refresh hoặc mở trang)
    @GetMapping("/databases")
    public ResponseEntity<List<DatabaseInfo>> listDatabases() {
        return ResponseEntity.ok(postgresService.listDatabases());
    }

    // 2. Import SQL (dùng cho Import Dialog)
    @PostMapping("/import")
    public ResponseEntity<String> importSql(
            @RequestParam("databaseName") String databaseName,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "onErrorStop", defaultValue = "true") boolean onErrorStop) {

        String result = postgresService.importSql(databaseName, file, onErrorStop);
        return ResponseEntity.ok(result);
    }

    // 3. Chạy query tùy ý (dùng cho Query Editor)
    @PostMapping("/execute")
    public ResponseEntity<String> executeQuery(
            @RequestParam("databaseName") String databaseName,
            @RequestBody String sql) {

        String result = postgresService.executeQuery(databaseName, sql);
        return ResponseEntity.ok(result);
    }
}