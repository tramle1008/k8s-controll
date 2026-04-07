package infra.k8s.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class ClusterNotReadyException extends RuntimeException {
    public ClusterNotReadyException(String noActiveClusterSelected) {
        super("Cluster chưa sẵn sàng.");
    }
}