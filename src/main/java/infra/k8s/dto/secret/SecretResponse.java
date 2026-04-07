package infra.k8s.dto.secret;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class SecretResponse {
    private String name;
    private String namespace;
    private String type;
    private int dataCount;
    private String age;

}
