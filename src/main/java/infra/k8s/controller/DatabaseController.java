package infra.k8s.controller;



import infra.k8s.service.DatabaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/database")
@RequiredArgsConstructor
public class DatabaseController {

    private final DatabaseService dbService;

    /**
     * Import file SQL vào PostgreSQL pod
     */
    @PostMapping("/import")
    public ResponseEntity<?> importSql(
            @RequestParam String namespace,
            @RequestParam String pod,
            @RequestParam String database,
            @RequestParam MultipartFile file
    ) {
        try {
            String result = dbService.importSqlToPostgresPod(
                    namespace,
                    pod,
                    database,
                    file
            );
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity
                    .badRequest()
                    .body(" Import failed: " + e.getMessage());
        }
    }
}