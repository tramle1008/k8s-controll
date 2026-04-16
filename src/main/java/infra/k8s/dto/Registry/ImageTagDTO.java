package infra.k8s.dto.Registry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageTagDTO {
    private String tag;
    private long size;
    private String created;
}