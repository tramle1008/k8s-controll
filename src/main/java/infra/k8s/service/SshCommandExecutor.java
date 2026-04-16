package infra.k8s.service;
import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Slf4j
@Service
public class SshCommandExecutor {

    public String run(String host, String user, String password, String command) {

        JSch jsch = new JSch();
        Session session = null;

        try {
            session = jsch.getSession(user, host, 22);
            session.setPassword(password);

            // Bỏ kiểm tra host key
            session.setConfig("StrictHostKeyChecking", "no");

            session.connect(10000); // 10s timeout

            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setErrStream(System.err);

            InputStream inputStream = channel.getInputStream();

            channel.connect();

            StringBuilder output = new StringBuilder();
            byte[] buffer = new byte[1024];
            int read;

            while ((read = inputStream.read(buffer)) != -1) {
                output.append(new String(buffer, 0, read));
            }

            channel.disconnect();
            session.disconnect();

            String result = output.toString().trim();
            log.info("SSH output from {}: {}", host, result);

            return result;

        } catch (Exception e) {
            log.error("SSH command failed on host {}: {}", host, e.getMessage());
            return null;
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }
}