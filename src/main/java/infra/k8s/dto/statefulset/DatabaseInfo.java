package infra.k8s.dto.statefulset;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DatabaseInfo {
    private String name;
    private String owner;
    private String encoding;
    private String size;     // ví dụ: "123 MB"
}