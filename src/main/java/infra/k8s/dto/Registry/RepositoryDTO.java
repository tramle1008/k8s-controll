package infra.k8s.dto.Registry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RepositoryDTO {
    private String name;
    private List<ImageTagDTO> tags;
}