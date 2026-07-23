package com.slatevn.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.slatevn.config.GoogleProperties;
import com.slatevn.domain.AccountType;
import com.slatevn.domain.User;
import com.slatevn.dto.AuthResponse;
import com.slatevn.repository.UserRepository;
import com.slatevn.web.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Locale;
import java.util.Optional;

@Service
public class GoogleAuthService {

    private final GoogleProperties googleProperties;
    private final UserRepository userRepository;
    private final AuthService authService;

    public GoogleAuthService(
            GoogleProperties googleProperties,
            UserRepository userRepository,
            AuthService authService
    ) {
        this.googleProperties = googleProperties;
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @Transactional
    public AuthResponse authenticate(String idTokenString) {
        if (googleProperties.clientId() == null || googleProperties.clientId().isBlank()) {
            throw new BadRequestException("Google Sign-In is not configured");
        }

        GoogleIdToken.Payload payload = verifyToken(idTokenString);
        String googleSub = payload.getSubject();
        String email = payload.getEmail().toLowerCase(Locale.ROOT);
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new BadRequestException("Google email is not verified");
        }

        Optional<User> byGoogleSub = userRepository.findByGoogleSub(googleSub);
        if (byGoogleSub.isPresent()) {
            if (byGoogleSub.get().isDeleted()) {
                throw new BadRequestException("Account has been deleted");
            }
            applyGooglePictureIfMissing(byGoogleSub.get(), payload);
            userRepository.save(byGoogleSub.get());
            return authService.issueTokensForUser(byGoogleSub.get());
        }

        Optional<User> byEmail = userRepository.findByEmailIgnoreCase(email);
        if (byEmail.isPresent()) {
            User existing = byEmail.get();
            if (existing.isDeleted()) {
                throw new BadRequestException("Account has been deleted");
            }
            if (existing.getAccountType() == AccountType.INTERNAL) {
                throw new BadRequestException("Internal users must sign in with email and password");
            }
            if (existing.getGoogleSub() == null) {
                existing.setGoogleSub(googleSub);
                applyGooglePictureIfMissing(existing, payload);
                userRepository.save(existing);
            } else if (!googleSub.equals(existing.getGoogleSub())) {
                throw new BadRequestException("Google account does not match this email");
            }
            return authService.issueTokensForUser(existing);
        }

        User user = new User();
        user.setEmail(email);
        user.setDisplayName(resolveDisplayName(payload));
        user.setLocale("vi");
        user.setEnabled(true);
        user.setAccountType(AccountType.OWNER);
        user.setGoogleSub(googleSub);
        user.setAvatarUrl(resolvePicture(payload));
        userRepository.save(user);
        return authService.issueTokensForUser(user);
    }

    private GoogleIdToken.Payload verifyToken(String idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance()
            )
                    .setAudience(Collections.singletonList(googleProperties.clientId()))
                    .build();
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new BadRequestException("Invalid Google token");
            }
            return idToken.getPayload();
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("Invalid Google token");
        }
    }

    private static String resolveDisplayName(GoogleIdToken.Payload payload) {
        Object name = payload.get("name");
        if (name instanceof String value && !value.isBlank()) {
            return value.trim();
        }
        String email = payload.getEmail();
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf('@'));
        }
        return "User";
    }

    private void applyGooglePictureIfMissing(User user, GoogleIdToken.Payload payload) {
        if (user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) {
            String picture = resolvePicture(payload);
            if (picture != null) {
                user.setAvatarUrl(picture);
            }
        }
    }

    private static String resolvePicture(GoogleIdToken.Payload payload) {
        Object picture = payload.get("picture");
        if (picture instanceof String value && !value.isBlank()) {
            return value.trim();
        }
        return null;
    }
}
