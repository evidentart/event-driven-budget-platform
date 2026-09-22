package com.sea.userservice.controller;

import com.sea.userservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.mockito.Mockito.when;

@WebMvcTest(UserController.class)
@Import(com.sea.userservice.config.ResourceServerSecurityConfig.class)
class UserAdminSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void rejectsAuthenticatedNonAdminFromArbitraryUserEndpoint() throws Exception {
        mockMvc.perform(get("/api/users/{userId}", UUID.randomUUID())
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("alice"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void permitsAdminRoleForArbitraryUserEndpoint() throws Exception {
                mockMvc.perform(get("/api/users/{userId}", UUID.randomUUID())
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsMalformedUserIdsWithConsistentErrorShape() throws Exception {
        mockMvc.perform(get("/api/users/not-a-uuid")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/api/users/not-a-uuid"));
    }

    @Test
    void doesNotExposeInternalIdentifiersForMissingUsers() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.getUserProfile(userId))
                .thenThrow(new com.sea.userservice.exception.UserNotFoundException(
                        "User not found for keycloakId=internal-subject"));

        mockMvc.perform(get("/api/users/{userId}", userId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User was not found."));
    }
}
