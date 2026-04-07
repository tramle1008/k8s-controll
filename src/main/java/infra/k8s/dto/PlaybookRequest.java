package infra.k8s.dto;

import java.util.List;
import lombok.*;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaybookRequest {
    private int masters;           // 1 hoặc 3
    private List<String> ips;      // danh sách IP đầy đủ, thứ tự: master trước, worker sau
    // Không cần workers nữa → tự tính từ ips.size() - masters
}