package com.vaultx.user.context.service.user;

import com.vaultx.user.context.model.authentication.response.UserResponseDTO;
import com.vaultx.user.context.model.user.User;
import com.vaultx.user.context.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final UserRepository userRepository;
    private final JwtDecoder jwtDecoder;
    private final ModelMapper modelMapper;

    public User getSessionUser(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }

        String tokenValue = authHeader.substring(7);
        Jwt accessToken = jwtDecoder.decode(tokenValue);
        return userRepository
                .findById(UUID.fromString(accessToken.getSubject()))
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    public UserResponseDTO getUser(HttpServletRequest request) {
        var user = getSessionUser(request);
        var userDto = modelMapper.map(user, UserResponseDTO.class);
        userDto.setHasPin(user.getPin() != null);
        return userDto;
    }

    public String deleteUser(HttpServletRequest request) {
        var user = getSessionUser(request);
        userRepository.delete(user);
        return "User and associated data deleted successfully";
    }
}
