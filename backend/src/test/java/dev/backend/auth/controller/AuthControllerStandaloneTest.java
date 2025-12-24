package dev.backend.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.backend.auth.controller.dto.IssuedTokens;
import dev.backend.auth.controller.dto.SignupRequest;
import dev.backend.auth.service.AuthService;
import dev.backend.auth.service.RefreshTokenService;
import dev.backend.auth.service.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerStandaloneTest {

    private MockMvc mvc;
    private final ObjectMapper om = new ObjectMapper();

    @Mock
    AuthService authService;
    @Mock UserRepository userRepository;
    @Mock RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(authService, userRepository, refreshTokenService);

        // ✅ @Valid 동작시키려면 Validator를 붙여줘야 함
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setValidator(validator)
                .build();
    }

    @Test
    void signup_passwordTooShort_should400() throws Exception {
        // password 6자 -> @Size(min=8) 위반
        SignupRequest req = new SignupRequest("test@test.com", "123456");

        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signup_ok_should201_andSetCookie() throws Exception {
        SignupRequest req = new SignupRequest("test@test.com", "12345678");

        // IssuedTokens가 record면 보통 이런 형태일 가능성이 큼:
        // record IssuedTokens(String accessToken, String refreshTokenRaw, long accessExpiresInSeconds)
        IssuedTokens tokens = new IssuedTokens("access.jwt.here",900 , "refresh.jwt.here");

        when(authService.signup(any(SignupRequest.class))).thenReturn(tokens);

        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isCreated())
                // ✅ 쿠키 헤더 존재 확인 (refresh_token)
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("refresh_token=")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")));
    }
}