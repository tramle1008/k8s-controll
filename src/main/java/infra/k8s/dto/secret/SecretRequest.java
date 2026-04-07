package infra.k8s.dto.secret;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class SecretRequest {
    private String name;
    private String namespace;
    private String type;
    private Map<String, String> data;
}
