package ro.cloud.security.user.context.service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DIDService {
    private static final String RSA_ALGORITHM = "RSA";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    /**
     * Generates a random challenge string for signature verification
     * @return A UUID string to be used as challenge
     */
    public String generateChallenge() {
        return UUID.randomUUID().toString();
    }

    /**
     * Verifies a signature using the provided DID public key
     *
     * @param publicDid Base64 encoded public key
     * @param message The original message that was signed
     * @param signatureBase64 Base64 encoded signature to verify
     * @return true if the signature is valid, false otherwise
     */
    public boolean verifyUserSignature(String publicDid, String message, String signatureBase64) {
        try {
            PublicKey publicKey = decodePublicKey(publicDid);
            byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
            return verifySignature(publicKey, message, signatureBytes);
        } catch (Exception e) {
            log.error("Signature verification failed", e);
            return false;
        }
    }

    private PublicKey decodePublicKey(String publicDid) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] publicKeyBytes = Base64.getDecoder().decode(publicDid);
        KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
        return keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
    }

    private boolean verifySignature(PublicKey publicKey, String message, byte[] signature) throws Exception {
        Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
        sig.initVerify(publicKey);
        sig.update(message.getBytes(StandardCharsets.UTF_8));
        return sig.verify(signature);
    }
}
