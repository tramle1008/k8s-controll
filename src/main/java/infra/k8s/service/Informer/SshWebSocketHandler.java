package infra.k8s.service.Informer;

import com.jcraft.jsch.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class SshWebSocketHandler extends TextWebSocketHandler {

    private Session sshSession;
    private ChannelShell sshChannel;
    private OutputStream sshInput;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("WS connected → Opening SSH...");

        // 🔥 Lấy query param từ ws://.../ws/ssh?user=xxx&host=yyy
        String query = session.getUri().getQuery();
        Map<String, String> params = parseQuery(query);

        String username = params.get("user");
        String host = params.get("host");

        System.out.println("Connecting SSH user=" + username + " host=" + host);

        String privateKey = "C:/Users/HP/.ssh/id_ed25519";

        JSch jsch = new JSch();
        jsch.addIdentity(privateKey);

        // 🔥 Dùng user + host gửi từ frontend
        sshSession = jsch.getSession(username, host, 22);

        sshSession.setConfig("StrictHostKeyChecking", "no");
        sshSession.connect(5000);

        sshChannel = (ChannelShell) sshSession.openChannel("shell");
        sshChannel.setPtyType("xterm");

        InputStream stdout = sshChannel.getInputStream();
        sshInput = sshChannel.getOutputStream();

        sshChannel.connect(3000);

        // Thread: SSH -> WebSocket
        new Thread(() -> {
            byte[] buffer = new byte[2048];
            int len;
            try {
                while ((len = stdout.read(buffer)) != -1) {
                    session.sendMessage(new TextMessage(new String(buffer, 0, len)));
                }
            } catch (Exception ignored) {}
        }).start();

        session.sendMessage(new TextMessage("Connected to SSH " + host + "...\r\n"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (sshInput != null) {
            sshInput.write(message.getPayload().getBytes());
            sshInput.flush();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        System.out.println("WS closed → Closing SSH...");
        cleanup();
    }

    private void cleanup() {
        try { if (sshChannel != null) sshChannel.disconnect(); } catch (Exception ignored) {}
        try { if (sshSession != null) sshSession.disconnect(); } catch (Exception ignored) {}
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null) return map;

        for (String param : query.split("&")) {
            String[] parts = param.split("=");
            if (parts.length == 2) {
                map.put(parts[0], parts[1]);
            }
        }
        return map;
    }
}