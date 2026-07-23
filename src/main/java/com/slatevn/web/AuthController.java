package com.slatevn.web;

import com.slatevn.dto.AuthResponse;
import com.slatevn.dto.GoogleAuthRequest;
import com.slatevn.dto.LoginRequest;
import com.slatevn.dto.ChangePasswordRequest;
import com.slatevn.dto.RefreshRequest;
import com.slatevn.dto.RegisterRequest;
import com.slatevn.dto.RegisterResponse;
import com.slatevn.dto.UpdateProfileRequest;
import com.slatevn.security.SecurityUtils;
import com.slatevn.service.AuthService;
import com.slatevn.service.GoogleAuthService;
import com.slatevn.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final GoogleAuthService googleAuthService;
    private final RegistrationService registrationService;

    public AuthController(
            AuthService authService,
            GoogleAuthService googleAuthService,
            RegistrationService registrationService
    ) {
        this.authService = authService;
        this.googleAuthService = googleAuthService;
        this.registrationService = registrationService;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleAuthRequest request) {
        return googleAuthService.authenticate(request.idToken());
    }

    @PostMapping("/register")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return registrationService.register(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @GetMapping("/me")
    public AuthResponse.UserResponse me() {
        return authService.me(SecurityUtils.currentUser().getId());
    }

    @PatchMapping("/me")
    public AuthResponse.UserResponse updateProfile(@RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(SecurityUtils.currentUser().getId(), request);
    }

    @PostMapping("/me/avatar")
    public AuthResponse.UserResponse uploadAvatar(@RequestPart("file") MultipartFile file) {
        return authService.uploadAvatar(SecurityUtils.currentUser().getId(), file);
    }

    @PostMapping("/change-password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(SecurityUtils.currentUser().getId(), request);
    }
}
