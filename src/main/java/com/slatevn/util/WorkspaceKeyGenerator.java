package com.slatevn.util;

import com.slatevn.repository.WorkspaceRepository;

import java.text.Normalizer;
import java.util.UUID;
import java.util.function.Predicate;

public final class WorkspaceKeyGenerator {

    private WorkspaceKeyGenerator() {
    }

    public static String generateUniqueKey(String name, Predicate<String> keyExists) {
        String base = slugFromName(name);
        String candidate = base;
        int suffix = 1;
        while (keyExists.test(candidate)) {
            String suffixStr = String.valueOf(suffix++);
            int maxBaseLen = Math.max(2, 32 - suffixStr.length());
            candidate = base.substring(0, Math.min(base.length(), maxBaseLen)) + suffixStr;
            if (suffix > 9999) {
                candidate = "WS" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
                break;
            }
        }
        return candidate;
    }

    public static String generateUniqueKey(String name, WorkspaceRepository workspaceRepository) {
        return generateUniqueKey(name, workspaceRepository::existsByKeyIgnoreCase);
    }

    public static String slugFromName(String name) {
        String normalized = name.trim()
                .replace('đ', 'd')
                .replace('Đ', 'D');
        String stripped = Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase()
                .replaceAll("[^A-Z0-9]", "");
        if (stripped.length() < 2) {
            stripped = "WS" + UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
        }
        return stripped.substring(0, Math.min(32, stripped.length()));
    }

    public static boolean isValidKey(String key) {
        return key != null && key.matches("^[A-Za-z0-9_-]{2,32}$");
    }
}
