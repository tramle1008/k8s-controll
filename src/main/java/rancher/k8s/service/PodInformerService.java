package rancher.k8s.service;


import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import rancher.k8s.cache.PodStateCache;
import rancher.k8s.dto.PodSummaryDto;
import rancher.k8s.module.PodState;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PodInformerService {

    private final PodStateCache cache;
    private final SimpMessagingTemplate messagingTemplate;

    // Nên inject KubernetesClient từ @Bean (tạo config riêng), tạm dùng builder
    private final KubernetesClient client = new KubernetesClientBuilder().build();

    private SharedIndexInformer<Pod> informer;

    @PostConstruct
    public void start() {
        informer = client.pods()
                .inAnyNamespace()  // hoặc .inNamespace("your-namespace")
                .inform(new ResourceEventHandler<Pod>() {
                    @Override
                    public void onAdd(Pod pod) {
                        handlePodUpdate(pod);
                    }

                    @Override
                    public void onUpdate(Pod oldPod, Pod newPod) {
                        handlePodUpdate(newPod);
                    }

                    @Override
                    public void onDelete(Pod pod, boolean deletedFinalStateUnknown) {
                        handlePodDelete(pod);
                    }
                }, 10 * 60 * 1000L);  // resync 10 phút – quan trọng!

        // Chạy informer async graceful
        Executors.newSingleThreadExecutor().execute(informer::run);

        // Reconcile sau khi start
        reconcileExistingPods();
    }

    @PreDestroy
    public void stop() {
        if (informer != null) {
            informer.stop();
        }
    }

    private void handlePodUpdate(Pod pod) {
        String name = pod.getMetadata().getName();
        String ns = pod.getMetadata().getNamespace();
        String key = name + "-" + ns;

        String phase = pod.getStatus() != null ? pod.getStatus().getPhase() : "Unknown";
        boolean ready = pod.getStatus().getConditions().stream()
                .filter(c -> "Ready".equals(c.getType()))
                .findFirst()
                .map(c -> "True".equals(c.getStatus()))
                .orElse(false);

        Instant now = Instant.now();

        PodState state = cache.get(key);

        boolean changed = false;

        if (state == null) {
            state = new PodState(name, ns, phase, ready, now);
            cache.put(key, state);
            changed = true;
        } else {
            state.setLastSeenTime(now);

            if (!state.getPhase().equals(phase) || state.isReady() != ready) {
                state.setPhase(phase);
                state.setReady(ready);
                state.setLastTransitionTime(now);
                state.setAlerted(false);
                changed = true;
            }
        }

        // Chỉ push khi có thay đổi quan trọng (phase/ready thay đổi hoặc mới)
        if (changed) {
            messagingTemplate.convertAndSend("/topic/pods", state);           // push chi tiết pod thay đổi
            pushPodSummary();  // push summary tổng hợp
        }
    }

    private void handlePodDelete(Pod pod) {
        String name = pod.getMetadata().getName();
        String ns = pod.getMetadata().getNamespace();
        String key = name + "-" + ns;

        PodState state = cache.remove(key);
        if (state != null) {
            state.setPhase("Terminated");
            state.setReady(false);
            state.setLastTransitionTime(Instant.now());

            messagingTemplate.convertAndSend("/topic/pods", state);
            pushPodSummary();  // cập nhật summary sau delete
        }
    }

    private void reconcileExistingPods() {
        try {
            Thread.sleep(3000);  // đợi informer sync ban đầu
        } catch (InterruptedException ignored) {}

        List<Pod> pods = client.pods().inAnyNamespace().list().getItems();
        for (Pod pod : pods) {
            handlePodUpdate(pod);
        }
        log.info("Reconciled {} pods", pods.size());
        pushPodSummary();  // push summary sau reconcile
    }

    // Method push summary realtime
    private void pushPodSummary() {
        PodSummaryDto summary = new PodSummaryDto();

        // Lấy collection từ getAll()
        Collection<PodState> allStates = cache.getAll();

        summary.setTotalPods(cache.getTotalCount());
        summary.setRunningPods(cache.getRunningCount());
        summary.setPendingPods(allStates.stream().filter(s -> "Pending".equals(s.getPhase())).count());
        summary.setFailedPods(allStates.stream().filter(s -> "Failed".equals(s.getPhase())).count());
        summary.setSucceededPods(allStates.stream().filter(s -> "Succeeded".equals(s.getPhase())).count());
        summary.setUnknownPods(allStates.stream().filter(s -> "Unknown".equals(s.getPhase())).count());
        summary.setAlertedPods(cache.getAlertedCount());

        messagingTemplate.convertAndSend("/topic/pods-summary", summary);
    }
}