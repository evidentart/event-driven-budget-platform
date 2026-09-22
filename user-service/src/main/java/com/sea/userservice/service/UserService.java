package com.sea.userservice.service;

import com.sea.userservice.dto.UpdateProfileRequest;
import com.sea.userservice.dto.UserResponse;
import com.sea.userservice.exception.UserNotFoundException;
import com.sea.userservice.mapper.UserMapper;
import com.sea.userservice.model.User;
import com.sea.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    /**
     * Option A: get-or-create local profile row on first authenticated request.
     * Uses verified JWT claims from Keycloak.
     */
    @Transactional
    public UserResponse getOrCreateMyProfile(JwtAuthenticationToken auth) {
        String keycloakId = auth.getToken().getSubject();

        return userRepository.findByKeycloakId(keycloakId)
                .map(userMapper::toResponse)
                .orElseGet(() -> provisionFromToken(auth));
    }

    @Transactional
    public UserResponse updateMyProfile(JwtAuthenticationToken auth, UpdateProfileRequest request) {
        String keycloakId = auth.getToken().getSubject();

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new UserNotFoundException("User not found for keycloakId=" + keycloakId));

        userMapper.updateEntity(request, user);
        User updated = userRepository.save(user);

        log.info("User profile updated for keycloakId={}", keycloakId);
        return userMapper.toResponse(updated);
    }

    @Transactional
    public void deleteMyProfile(JwtAuthenticationToken auth) {
        String keycloakId = auth.getToken().getSubject();

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new UserNotFoundException("User not found for keycloakId=" + keycloakId));

        userRepository.delete(user);
        log.info("User deleted for keycloakId={}", keycloakId);
    }

    // Admin/internal endpoints below (by DB UUID)

    @Transactional(readOnly = true)
    public UserResponse getUserProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(int page, int size) {
        return userRepository.findAll(PageRequest.of(page, size))
                .stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean userExistsByDbId(UUID userId) {
        return userRepository.existsById(userId);
    }

    private UserResponse provisionFromToken(JwtAuthenticationToken auth) {
        String keycloakId = auth.getToken().getSubject();
        String email = auth.getToken().getClaimAsString("email");
        String firstName = auth.getToken().getClaimAsString("given_name");
        String lastName = auth.getToken().getClaimAsString("family_name");

        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Token missing email claim. Ensure Keycloak adds 'email' to access token.");
        }

        // If names are missing, you can either default or throw. Defaulting is fine for demos.
        if (firstName == null) firstName = "";
        if (lastName == null) lastName = "";

        User user = new User();
        user.setKeycloakId(keycloakId);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setActive(true);

        try {
            User saved = userRepository.save(user);
            log.info("Provisioned new user: id={}, keycloakId={}", saved.getId(), saved.getKeycloakId());
            return userMapper.toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            // Race condition: 2 requests at the same time. Unique index wins.
            return userRepository.findByKeycloakId(keycloakId)
                    .map(userMapper::toResponse)
                    .orElseThrow(() -> e);
        }
    }
}
