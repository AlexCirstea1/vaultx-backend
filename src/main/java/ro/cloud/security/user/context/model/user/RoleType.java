package ro.cloud.security.user.context.model.user;

import lombok.Getter;

@Getter
public enum RoleType {
    ADMIN("ADMIN"),
    USER("USER"),
    VERIFIED("VERIFIED"),
    ANONYMOUS("ANONYMOUS");

    private final String value;

    RoleType(String value) {
        this.value = value;
    }
}
