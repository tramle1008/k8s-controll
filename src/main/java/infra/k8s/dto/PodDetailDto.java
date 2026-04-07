package infra.k8s.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PodDetailDto {
    private String namespace;
    private String name;
    private String ready;              // ví dụ: "1/1" hoặc "0/1"
    private String status;             // "Running", "Pending", "CrashLoopBackOff", "Error", ...
    private int restarts;
    private String age;                // "5m", "2h15m", "3d", ...
    private String nodeName;           // node Pod đang chạy
    private String podIp;              // IP nội bộ của Pod
    private Instant creationTimestamp; // thời gian tạo (dùng cho sort hoặc tooltip)
    private Map<String, String> labels;// để filter/group trên frontend nếu cần
}