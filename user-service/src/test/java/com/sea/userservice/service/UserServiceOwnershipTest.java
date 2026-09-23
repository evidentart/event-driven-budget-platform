package com.sea.userservice.service;

import com.sea.userservice.dto.UserResponse;
import com.sea.userservice.mapper.UserMapper;
import com.sea.userservice.model.User;
import com.sea.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceOwnershipTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Test
    void readsProfileUsingJwtSubjectRatherThanClientSuppliedIdentity() {
        User user = new User();
        UserResponse response = new UserResponse(null, "alice@example.com", null, null, null, true);
        when(userRepository.findByKeycloakId("alice-sub")).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(response);

        UserResponse actual = new UserService(userRepository, userMapper)
                .getOrCreateMyProfile(authentication("alice-sub", "alice@example.com"));

        assertEquals(response, actual);
        verify(userRepository).findByKeycloakId("alice-sub");
        verify(userRepository, never()).findById(any());
    }

    @Test
    void provisionsProfileFromValidatedJwtClaims() {
        when(userRepository.findByKeycloakId("alice-sub")).thenReturn(Optional.empty());
        User saved = new User();
        UserResponse response = new UserResponse(null, "alice@example.com", null, null, null, true);
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(userMapper.toResponse(saved)).thenReturn(response);

        UserResponse actual = new UserService(userRepository, userMapper)
                .getOrCreateMyProfile(authentication("alice-sub", "alice@example.com"));

        assertEquals(response, actual);
        verify(userRepository).save(argThat(user ->
                "alice-sub".equals(user.getKeycloakId())
                        && "alice@example.com".equals(user.getEmail())
                        && "Alice".equals(user.getFirstName())
                        && "User".equals(user.getLastName())));
    }

    private JwtAuthenticationToken authentication(String subject, String email) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .claim("email", email)
                .claim("given_name", "Alice")
                .claim("family_name", "User")
                .build();
        return new JwtAuthenticationToken(jwt);
    }
}
