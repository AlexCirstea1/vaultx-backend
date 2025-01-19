package ro.cloud.security.user.context.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ro.cloud.security.user.context.model.DocumentMetadata;
import ro.cloud.security.user.context.service.DocumentService;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    public ResponseEntity<DocumentMetadata> uploadDocument(
            HttpServletRequest request, @RequestParam("file") MultipartFile file)
            throws IOException, NoSuchAlgorithmException {
        return ResponseEntity.ok(documentService.uploadDocument(request, file));
    }
}
