package org.example.backend.recipient;

public enum Grade {
    VIP, GOLD, GENERAL, UNKNOWN;
    public static Grade from(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        String normalized = raw.toUpperCase().replaceAll("[^A-Z0-9가-힣]", "");
        return switch (normalized) {
            case "VIP" -> VIP;
            case "GOLD", "골드" -> GOLD;
            case "GENERAL", "일반" -> GENERAL;
            default -> UNKNOWN;
        };
    }
}
