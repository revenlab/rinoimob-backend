package com.rinoimob.service.rental;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
@Service
public class RentalSecretService {
    private final String configuredKey; private final SecureRandom random = new SecureRandom();
    public RentalSecretService(@Value("${rental.asaas.encryption-key:}") String configuredKey) { this.configuredKey = configuredKey; }
    public String encrypt(String value) { try { byte[] iv = new byte[12]; random.nextBytes(iv); Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv)); return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(c.doFinal(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException("Could not encrypt rental credential", e); } }
    public String decrypt(String value) { try { String[] p = value.split("\\.", 2); Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.getDecoder().decode(p[0]))); return new String(c.doFinal(Base64.getDecoder().decode(p[1])), StandardCharsets.UTF_8); } catch (Exception e) { throw new IllegalStateException("Could not decrypt rental credential", e); } }
    public String sha256(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    public void ensureConfigured() { try { key(); } catch (Exception e) { throw new IllegalStateException(e.getMessage(), e); } }
    public boolean matchesHash(String value, String expected) { return expected != null && MessageDigest.isEqual(sha256(value).getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8)); }
    private SecretKeySpec key() throws Exception { if (configuredKey == null || configuredKey.length() < 32) throw new IllegalStateException("RENTAL_ASAAS_ENCRYPTION_KEY must contain at least 32 characters"); return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(configuredKey.getBytes(StandardCharsets.UTF_8)), "AES"); }
}
