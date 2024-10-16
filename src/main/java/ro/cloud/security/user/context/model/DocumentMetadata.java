package ro.cloud.security.user.context.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DocumentMetadata {
    private String documentName;
    private String documentHash;
    private String uploaderId;
}
