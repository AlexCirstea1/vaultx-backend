package ro.cloud.security.user.context.service.authentication;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ro.cloud.security.user.context.exception.CustomBadCredentialsException;
import ro.cloud.security.user.context.model.user.User;
import ro.cloud.security.user.context.repository.UserRepository;
import ro.cloud.security.user.context.utils.CipherUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class PinService {
    private final UserService userService;
    private final UserRepository userRepository;

    public void savePin(HttpServletRequest request, String pin) {
        if (pin.length() != 6) {
            throw new CustomBadCredentialsException("PIN must be exactly 6 digits");
        }

        User user = userService.getSessionUser(request);
        String hashedPin = CipherUtils.getHash(pin);

        user.setPin(hashedPin);
        userRepository.save(user);
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
            throw new CustomBadCredentialsException("Invalid PIN");
        }
    }
}
