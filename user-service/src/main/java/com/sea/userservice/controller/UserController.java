package com.sea.userservice.controller;

import com.sea.userservice.dto.UpdateProfileRequest;
import com.sea.userservice.dto.UserResponse;
import com.sea.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Option A: Get-or-create user profile row on first login.
     * Identity is taken from the validated JWT (Keycloak sub).
     */
    @GetMapping("/profile")
    public ResponseEntity<UserResponse> getMyProfile(JwtAuthenticationToken auth) {
        return ResponseEntity.ok(userService.getOrCreateMyProfile(auth));
    }

    @PatchMapping("/profile")
    public ResponseEntity<UserResponse> updateMyProfile(
            JwtAuthenticationToken auth,
            @Valid @RequestBody UpdateProfileRequest request) {

        return ResponseEntity.ok(userService.updateMyProfile(auth, request));
    }

    @DeleteMapping("/profile")
    public ResponseEntity<Void> deleteMyProfile(JwtAuthenticationToken auth) {
        userService.deleteMyProfile(auth);
        return ResponseEntity.noContent().build();
    }

    // Admin/internal endpoints (by DB UUID)

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserProfile(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getUserProfile(userId));
    }

    @GetMapping("/{userId}/exists")
    public ResponseEntity<Boolean> userExists(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.userExistsByDbId(userId));
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(userService.listUsers(page, size));
    }
}
