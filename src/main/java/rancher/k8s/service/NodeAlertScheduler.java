package rancher.k8s.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rancher.k8s.cache.NodeStateCache;
import rancher.k8s.module.NodeState;
import rancher.k8s.repository.NodeEventRepository;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class NodeAlertScheduler {

    private final NodeStateCache cache;
    private final NodeEventRepository eventRepo;

    @Scheduled(fixedRate = 10000)
    public void checkNodes() {

        for (NodeState state : cache.getAll()) {

            if (!state.isReady() && !state.isAlerted()) {

                long downTime = Duration.between(
                        state.getLastTransitionTime(),
                        Instant.now()
                ).getSeconds();

                if (downTime > 60) {
                    // gửi mail / webhook
                    state.setAlerted(true);
                }
            }
        }
    }
}