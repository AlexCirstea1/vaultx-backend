package ro.cloud.security.user.context.model.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The message that gets sent to the sender as a read receipt event. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadReceiptNotification {
    // The user who read the messages
    private String readerId;
    // The list of message IDs that were just read
    private List<UUID> messageIds;
    // The timestamp when the read event occurred
    private LocalDateTime readTimestamp;
}
