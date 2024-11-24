package ro.cloud.security.user.context.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import ro.cloud.security.user.context.model.User;
import ro.cloud.security.user.context.model.dto.UserResponseDTO;
import ro.cloud.security.user.context.repository.UserRepository;

import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final ModelMapper mapper;
    private final JwtDecoder jwtDecoder;
    private final RedisTemplate<String, Object> redisTemplate;

    public UserResponseDTO getUserById(UUID id) {
        var user = userRepository.findById(id).orElseThrow();
        return UserResponseDTO.builder()
                .email(user.getEmail())
                .username(user.getUsername())
                .id(id)
                .build();
    }

    public User getSessionUser(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }

        String tokenValue = authHeader.substring(7);
        Jwt access_token = jwtDecoder.decode(tokenValue);
        return userRepository.findById(UUID.fromString(access_token.getSubject())).orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findUserByUsername(username).orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    public UserResponseDTO getUser(HttpServletRequest request) {
        var user = getSessionUser(request);
        var userDto = mapper.map(user, UserResponseDTO.class);
        userDto.setHasPin(user.getPin() != null);
        return userDto;
    }

    public String deleteUser(HttpServletRequest request) {
        var user = getSessionUser(request);
        userRepository.delete(user);
        redisTemplate.delete(user.getId().toString());
        return "User and associated data deleted successfully";
    }
}