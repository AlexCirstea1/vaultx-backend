package com.vaultx.user.context.service.authentication;

import com.vaultx.user.context.exception.CustomBadCredentialsException;
import com.vaultx.user.context.model.activity.ActivityType;
import com.vaultx.user.context.model.user.User;
import com.vaultx.user.context.repository.UserRepository;
import com.vaultx.user.context.service.user.ActivityService;
import com.vaultx.user.context.service.user.UserService;
import com.vaultx.user.context.utils.CipherUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PinService {
    private final UserService userService;
    private final UserRepository userRepository;
    private final ActivityService activityService;

    public void savePin(HttpServletRequest request, String pin) {
        if (pin.length() != 6) {
            throw new CustomBadCredentialsException("PIN must be exactly 6 digits");
        }

        User user = userService.getSessionUser(request);
        String hashedPin = CipherUtils.getHash(pin);

        user.setPin(hashedPin);
        userRepository.save(user);

        // Log PIN update activity
        activityService.logActivity(user, ActivityType.PIN, "PIN code changed", false, null);
    }

    public Boolean verifyPin(HttpServletRequest request, String pin) {
        if (pin.length() != 6) {
            throw new CustomBadCredentialsException("PIN must be exactly 6 digits");
        }

        User user = userService.getSessionUser(request);
        String hashedPin = CipherUtils.getHash(pin);

        if (Objects.equals(hashedPin, user.getPin())) {
            return Boolean.TRUE;
        } else {
            // Log failed PIN verification
            activityService.logActivity(user, ActivityType.PIN, "Failed PIN verification attempt", true, null);
            throw new CustomBadCredentialsException("Invalid PIN");
        }
    }
}
