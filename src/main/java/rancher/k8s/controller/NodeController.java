//package rancher.k8s.controller;
//
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.PathVariable;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//@RestController
//@RequestMapping("/api/nodes")
//@RequiredArgsConstructor
//public class NodeController {
//    private final NodeService nodeLogService;
//
//    @GetMapping("/{name}/logs")
//    public ResponseEntity<?> getNodeLogs(@PathVariable String name) {
//        String logs = nodeLogService.getKubeletLog(name);
//        return ResponseEntity.ok(logs);
//    }
//}
