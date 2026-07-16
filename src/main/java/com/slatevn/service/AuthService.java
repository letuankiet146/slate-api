package com.slatevn.service;

import com.slatevn.config.JwtProperties;
import com.slatevn.domain.RefreshToken;
import com.slatevn.domain.ScopeType;
import com.slatevn.domain.User;
import com.slatevn.dto.AuthResponse;
import com.slatevn.dto.LoginRequest;
import com.slatevn.repository.MembershipRepository;
import com.slatevn.repository.RefreshTokenRepository;
import com.slatevn.repository.UserRepository;
import com.slatevn.security.JwtService;
import com.slatevn.web.BadRequestException;
import com.slatevn.web.NotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            MembershipRepository membershipRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            JwtProperties jwtProperties
    ) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().toLowerCase(), request.password()));
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new NotFoundException("User not found"));
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshTokenRaw) {
        String hash = sha256(refreshTokenRaw);
        RefreshToken stored = refreshTokenRepository.findByTokenHashAndRevokedFalse(hash)
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            stored.setRevoked(true);
            refreshTokenRepository.save(stored);
            throw new BadRequestException("Refresh token expired");
        }
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse.UserResponse me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return toUserResponse(user);
    }

    private AuthResponse issueTokens(User user) {
        String access = jwtService.createAccessToken(user.getId(), user.getEmail());
        String refreshRaw = UUID.randomUUID() + "." + UUID.randomUUID();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(sha256(refreshRaw));
        refreshToken.setExpiresAt(Instant.now().plusSeconds(jwtProperties.refreshTokenDays() * 24 * 3600));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.of(access, refreshRaw, toUserResponse(user));
    }

    private AuthResponse.UserResponse toUserResponse(User user) {
        List<String> perms = membershipRepository.findByUserIdAndScopeType(user.getId(), ScopeType.SYSTEM).stream()
                .flatMap(m -> m.getRole().getPermissions().stream())
                .map(p -> p.getCode())
                .distinct()
                .sorted()
                .toList();
        return new AuthResponse.UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getLocale(),
                user.isEnabled(),
                perms
        );
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
