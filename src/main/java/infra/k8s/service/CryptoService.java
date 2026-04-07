package infra.k8s.service;

import org.springframework.stereotype.Service;
import infra.k8s.config.EncryptionProperties;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

@Service
public class CryptoService {

    private final SecretKeySpec secretKey;

    public CryptoService(EncryptionProperties props) {
        byte[] key = Arrays.copyOf(props.getSecret().getBytes(), 16); // AES-128
        this.secretKey = new SecretKeySpec(key, "AES");
    }

    public String encrypt(String data) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public String decrypt(String encryptedData) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decoded = Base64.getDecoder().decode(encryptedData);
        return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
    }
}