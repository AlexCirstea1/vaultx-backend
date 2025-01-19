package ro.cloud.security.user.context.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ro.cloud.security.user.context.kafka.KafkaProducer;
import ro.cloud.security.user.context.model.DocumentMetadata;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {
    private final KafkaProducer kafkaProducer;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public DocumentMetadata uploadDocument(HttpServletRequest request, MultipartFile file)
            throws IOException, NoSuchAlgorithmException {

        var fileHash = getFileHash(file.getBytes());
        var uploaderId = userService.getSessionUser(request).getId().toString();
        var metadata = new DocumentMetadata(file.getOriginalFilename(), fileHash, uploaderId);

        var metadataJson = objectMapper.writeValueAsString(metadata);
        kafkaProducer.sendMessage(metadataJson);

        return metadata;
    }

    private String getFileHash(byte[] fileBytes) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(fileBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
