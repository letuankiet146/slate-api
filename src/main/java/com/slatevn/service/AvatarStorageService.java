package com.slatevn.service;

import com.slatevn.config.UploadProperties;
import com.slatevn.web.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AvatarStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );
    private static final long MAX_BYTES = 2 * 1024 * 1024;

    private final Path avatarDir;

    public AvatarStorageService(UploadProperties uploadProperties) {
        this.avatarDir = Path.of(uploadProperties.directory(), "avatars").toAbsolutePath().normalize();
    }

    public String store(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Avatar file is required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("Avatar must be 2 MB or smaller");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BadRequestException("Avatar must be JPEG, PNG, or WebP");
        }

        deleteExisting(userId);

        String extension = extensionFor(contentType);
        Path target = avatarDir.resolve(userId + extension);
        try {
            Files.createDirectories(avatarDir);
            file.transferTo(target);
        } catch (IOException ex) {
            throw new BadRequestException("Failed to store avatar");
        }
        return "/uploads/avatars/" + userId + extension;
    }

    public void delete(UUID userId) {
        deleteExisting(userId);
    }

    private void deleteExisting(UUID userId) {
        try {
            if (!Files.isDirectory(avatarDir)) {
                return;
            }
            try (var stream = Files.list(avatarDir)) {
                stream.filter(path -> path.getFileName().toString().startsWith(userId.toString()))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                            }
                        });
            }
        } catch (IOException ignored) {
        }
    }

    private static String extensionFor(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
