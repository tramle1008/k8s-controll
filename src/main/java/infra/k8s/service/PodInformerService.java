package infra.k8s.service;


import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import infra.k8s.cache.PodStateCache;
import infra.k8s.dto.PodSummaryDto;
import infra.k8s.event.ClusterSwitchedEvent;
import infra.k8s.module.PodState;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PodInformerService{

    private final PodStateCache cache;
    private final SimpMessagingTemplate messagingTemplate;
    private final ClusterManager clusterManager;
    private SharedIndexInformer<Pod> informer;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();



    private void startInformer() {

        KubernetesClient client = clusterManager.getActiveClient();

        if (client == null) {
            log.warn("Không có cluster active, không start Pod informer.");
            return;
        }

        log.info("Khởi tạo Pod informer cho cluster {}",
                clusterManager.getActiveClusterId());

        informer = client.pods()
                .inAnyNamespace()
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

                }, 10 * 60 * 1000L);

        executor.submit(informer::run);

        CompletableFuture.runAsync(this::reconcileExistingPods, executor);
    }

    @PreDestroy
    public void stop() {
        if (informer != null) {
            informer.stop();
        }
        executor.shutdownNow();
    }

    private void handlePodUpdate(Pod pod) {
        String name = pod.getMetadata().getName();
        String ns = pod.getMetadata().getNamespace();
        String key = name + "-" + ns;


       // log.info("handlePodUpdate triggered for {}/{} (phase: {})", ns, name, pod.getStatus() != null ? pod.getStatus().getPhase() : "Unknown");
        String phase = pod.getStatus() != null ? pod.getStatus().getPhase() : "Unknown";
        boolean ready = false;
        if (pod.getStatus() != null && pod.getStatus().getConditions() != null) {
            ready = pod.getStatus().getConditions().stream()
                    .filter(c -> "Ready".equals(c.getType()))
                    .findFirst()
                    .map(c -> "True".equals(c.getStatus()))
                    .orElse(false);
        }
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

        if (changed) {
            log.info("Pod changed: {}/{} - phase: {}, ready: {} → PUSHING detail & summary", ns, name, phase, ready);
            messagingTemplate.convertAndSend("/topic/pods-all", state);  // ← Đổi sang /topic/pods-all để khớp HTML test
            pushPodSummary();
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

            log.info("Pod deleted: {}/{} → PUSHING detail & summary", ns, name);
            messagingTemplate.convertAndSend("/topic/pods-all", state);  // ← Đổi sang /topic/pods-all
            pushPodSummary();
        }
    }

    private void reconcileExistingPods() {

        try {
            Thread.sleep(3000);
        } catch (InterruptedException ignored) {}

        KubernetesClient client = clusterManager.getActiveClient();

        if (client == null) {
            log.warn("Không có cluster active, skip reconcile Pods.");
            return;
        }

        log.info("Starting full reconcile Pods for active cluster {}",
                clusterManager.getActiveClient());

        // Xóa cache cũ
        cache.clearAll();

        List<Pod> pods = client.pods().inAnyNamespace().list().getItems();

        log.info("Reconciled {} pods", pods.size());

        for (Pod pod : pods) {
            handlePodUpdate(pod);
        }

        pushPodSummary();
    }

    private void stopInformer() {
        if (informer != null) {
            log.info("Stopping old Pod informer...");
            informer.stop();
            informer = null;
        }
    }

    @EventListener
    public void onClusterSwitched(ClusterSwitchedEvent event) {

        log.info("### Nhận ClusterSwitchedEvent → clusterId = {}", event.getClusterId());

        stopInformer();
        cache.clearAll();
        startInformer();
    }

    private void pushPodSummary() {
        PodSummaryDto summary = new PodSummaryDto();

        Collection<PodState> allStates = cache.getAll();

        summary.setTotalPods(cache.getTotalCount());
        summary.setRunningPods(cache.getRunningCount());
        summary.setPendingPods(allStates.stream().filter(s -> "Pending".equals(s.getPhase())).count());
        summary.setFailedPods(allStates.stream().filter(s -> "Failed".equals(s.getPhase())).count());
        summary.setSucceededPods(allStates.stream().filter(s -> "Succeeded".equals(s.getPhase())).count());
        summary.setUnknownPods(allStates.stream().filter(s -> "Unknown".equals(s.getPhase())).count());
        summary.setAlertedPods(cache.getAlertedCount());

        log.info("Push Pod summary: total={}, running={}, pending={}, failed={}",
                summary.getTotalPods(), summary.getRunningPods(), summary.getPendingPods(), summary.getFailedPods());

        messagingTemplate.convertAndSend("/topic/pods-summary", summary);
    }
}