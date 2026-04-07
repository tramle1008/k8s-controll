package infra.k8s.event;

import org.springframework.context.ApplicationEvent;

public class ClusterSwitchedEvent extends ApplicationEvent {

    private final Long clusterId;

    public ClusterSwitchedEvent(Object source, Long clusterId) {
        super(source);
        this.clusterId = clusterId;
    }

    public Long getClusterId() {
        return clusterId;
    }
}