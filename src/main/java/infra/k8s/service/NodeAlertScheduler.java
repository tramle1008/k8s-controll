package infra.k8s.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import infra.k8s.dto.TopicNodeSummary;
import infra.k8s.module.ClusterNode;
import infra.k8s.repository.ClusterNodeRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Scheduler kiểm tra định kỳ các node Not Ready.
 * Cảnh báo nếu downtime > 60 giây (log + WebSocket).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeAlertScheduler {

    private final ClusterNodeRepository clusterNodeRepository;
    private final SimpMessagingTemplate messagingTemplate;  // Inject để push WebSocket

    @Scheduled(fixedRate = 10000) // 10 giây/lần
    public void checkAlerts() {
        List<ClusterNode> downNodes = clusterNodeRepository.findNotReadyNodes();

        if (downNodes.isEmpty()) {
            log.debug("No down nodes found.");
            return;
        }

        Instant now = Instant.now();

        for (ClusterNode node : downNodes) {
            Instant transitionTime = node.getLastTransitionTime();
            if (transitionTime == null) {
              //  log.warn("Node {} has no lastTransitionTime, skipping.", node.getName());
                continue;
            }

            Duration downtime = Duration.between(transitionTime, now);
            long secondsDown = downtime.getSeconds();

            // Ngưỡng cảnh báo (có thể config sau)
            if (secondsDown > 60 && Boolean.FALSE.equals(node.getAlerted())) {
                node.setAlerted(true);
                clusterNodeRepository.save(node);

                // Log cảnh báo
                log.warn("ALERT: Node {} DOWN {} giây (từ {}), cluster {}",
                        node.getName(), secondsDown, transitionTime, node.getCluster().getName());
                messagingTemplate.convertAndSend("/topic/nodes", new TopicNodeSummary(node));
            }
        }
    }
}