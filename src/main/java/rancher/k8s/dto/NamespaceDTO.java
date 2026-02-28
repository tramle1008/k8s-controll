package rancher.k8s.dto;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NamespaceDTO {
    private String name;
    private String status;
    private String creationTimestamp;
}