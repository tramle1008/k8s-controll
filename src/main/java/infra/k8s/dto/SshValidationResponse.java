package infra.k8s.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SshValidationResponse {
    private boolean success;
    private String message;
    private String clusterName;
    private int nodeCount;
    private List<String> successfulNodes = new ArrayList<>();
    private List<String> failedNodes = new ArrayList<>();

    public SshValidationResponse(boolean b, String clusterNameIsRequired, Object o, int i) {
    }
}