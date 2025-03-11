package ro.cloud.security.user.context.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ro.cloud.security.user.context.service.authentication.DIDService;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

class DIDServiceTest {

    private final DIDService didService = new DIDService();

    @Test
    void testSignAndVerifyWithDIDService() throws Exception {
        // 1) Generate an RSA key pair (2048 bits) for testing
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        // 2) Convert public key to Base64 (simulating what you'd store in publicDid)
        String publicKeyBase64 = Base64.getEncoder()
                .encodeToString(keyPair.getPublic().getEncoded());

        // 3) Prepare a test message
        String message = "Hello from DIDServiceTest";

        // 4) Sign the message with the private key
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(messageBytes);
        byte[] signatureBytes = signer.sign();

        // Convert the raw signature to Base64
        String signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes);

        // 5) Use DIDService to verify the signature
        boolean isValid = didService.verifyUserSignature(
                publicKeyBase64,  // The stored public DID
                message,          // The message we signed
                signatureBase64   // The signature from the private key
        );

        // 6) Assert that verification succeeds
        Assertions.assertTrue(
                isValid,
                "Signature should be valid when verified with matching public key and message."
        );
    }

    @Test
    void testSignAndVerifyShouldFailWithWrongMessage() throws Exception {
        // 1) Generate an RSA key pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        // 2) Convert public key to Base64
        String publicKeyBase64 = Base64.getEncoder()
                .encodeToString(keyPair.getPublic().getEncoded());

        // 3) Sign one message
        String originalMessage = "Original message";
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(originalMessage.getBytes(StandardCharsets.UTF_8));
        byte[] signatureBytes = signer.sign();
        String signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes);

        // 4) Attempt to verify with a different message
        String differentMessage = "Tampered message";
        boolean isValid = didService.verifyUserSignature(
                publicKeyBase64,  // same public key
                differentMessage, // different from the original
                signatureBase64
        );

        // 5) This should fail
        Assertions.assertFalse(isValid, "Verification should fail for a tampered message.");
    }
}
