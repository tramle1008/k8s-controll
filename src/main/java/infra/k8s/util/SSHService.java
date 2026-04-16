package infra.k8s.util;

import com.jcraft.jsch.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class SSHService {

    private final SshConfig config;

    public String exec(String command) throws Exception {

        JSch jsch = new JSch();

        // Nếu bạn dùng private key
        if (config.getPrivateKeyPath() != null) {
            jsch.addIdentity(config.getPrivateKeyPath());
        }

        Session session = jsch.getSession(config.getUser(), config.getHost(), 22);


        // Không kiểm tra host key
        session.setConfig("StrictHostKeyChecking", "no");

        session.connect();

        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);

        InputStream in = channel.getInputStream();
        InputStream err = channel.getErrStream();

        channel.connect();

        StringBuilder output = new StringBuilder();
        byte[] buffer = new byte[1024];

        // Đọc stdout
        while (!channel.isClosed() || in.available() > 0) {
            while (in.available() > 0) {
                int len = in.read(buffer, 0, buffer.length);
                if (len < 0) break;
                output.append(new String(buffer, 0, len));
            }
            Thread.sleep(50);
        }

        // Đọc stderr nếu có lỗi
        while (err.available() > 0) {
            int len = err.read(buffer, 0, buffer.length);
            if (len < 0) break;
            output.append(new String(buffer, 0, len));
        }

        channel.disconnect();
        session.disconnect();

        return output.toString();
    }
}