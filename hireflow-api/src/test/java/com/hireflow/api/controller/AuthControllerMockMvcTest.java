package com.hireflow.api.controller;

import com.hireflow.api.dto.LoginRequestDto;
import com.hireflow.api.security.ApiAuthenticationAdapter;
import com.hireflow.web.security.AuthenticationResult;
import com.hireflow.web.security.UserPrincipal;
import com.hireflow.web.security.UserRole;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiAuthenticationAdapter authenticationAdapter;

    @Test
    @DisplayName("GET /api/v1/auth/csrf returns token, headerName, and anti-caching headers")
    void getCsrfToken_returnsValidTokenAndHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.parameterName").value("_csrf"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login without CSRF token returns 403 Forbidden")
    void login_withoutCsrf_returns403Forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student_john\",\"password\":\"SecretPass123!\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value(containsString("CSRF")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login with blank credentials returns 400 Bad Request")
    void login_withBlankCredentials_returns400BadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login with wrong password returns 401 Unauthorized with generic message")
    void login_withInvalidPassword_returns401Unauthorized() throws Exception {
        when(authenticationAdapter.authenticate(eq("student_john"), eq("WrongPass123!"), anyString()))
                .thenReturn(AuthenticationResult.failure(AuthenticationResult.Status.INVALID_CREDENTIALS, "Invalid username or password."));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student_john\",\"password\":\"WrongPass123!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid username or password."))
                .andExpect(jsonPath("$.message", not(containsString("hash"))));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login with unknown username returns 401 Unauthorized without enumeration")
    void login_withUnknownUser_returns401UnauthorizedWithoutEnumeration() throws Exception {
        when(authenticationAdapter.authenticate(eq("unknown_user_999"), anyString(), anyString()))
                .thenReturn(AuthenticationResult.failure(AuthenticationResult.Status.INVALID_CREDENTIALS, "Invalid username or password."));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"unknown_user_999\",\"password\":\"AnyPassword123!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login with valid credentials returns 200 OK and sanitized UserProfileDto")
    void login_withValidCredentials_returns200AndUserProfile() throws Exception {
        UserPrincipal principal = new UserPrincipal("student_john", "John Doe", UserRole.STUDENT, "STU-2024-001");
        when(authenticationAdapter.authenticate(anyString(), anyString(), anyString()))
                .thenReturn(AuthenticationResult.success(principal));
        when(authenticationAdapter.findAccount(anyString()))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student_john\",\"password\":\"ValidPass123!\"}"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.username").value("student_john"))
                .andExpect(jsonPath("$.displayName").value("John Doe"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.studentId").value("STU-2024-001"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/auth/me when unauthenticated returns 401 Unauthorized")
    void getCurrentUser_whenUnauthenticated_returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout without CSRF token returns 403 Forbidden")
    void logout_withoutCsrf_returns403Forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/auth/logout with CSRF token terminates session and clears cookie")
    void logout_whenAuthenticated_withCsrf_clearsSessionAndReturns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(jsonPath("$.message").value("Logged out successfully."));
    }

    @Test
    @DisplayName("Public read-only endpoints remain accessible without authentication")
    void publicEndpointsRemainAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/companies"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/drives"))
                .andExpect(status().isOk());
    }
}
