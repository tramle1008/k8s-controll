package infra.k8s.dto;
//thong tin de tao node

import lombok.Data;

@Data
public class NodeDto {
    private String name;       // master-1, worker-1...
    private String role;       // "master" or "worker"
    private String ip;
    private String user;       // user
    private SshDto ssh;
}