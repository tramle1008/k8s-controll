package infra.k8s.service.Informer;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClusterLogService {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendLog(Long clusterId, String message) {
        messagingTemplate.convertAndSend("/topic/cluster/" + clusterId, message);
    }

}