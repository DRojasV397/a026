package com.app.model;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record UserDTO(
        String        fullName,
        String        username,
        String        role,
        String        roleDisplay,
        String        department,
        String        email,
        boolean       isVerified,
        String        phone,
        String        avatarPath,
        LocalDate     memberSince,   // ← NUEVO
        LocalDateTime lastAccess,    // ← NUEVO
        UserStats     stats
) {
    public record UserStats(
            int daysActive,
            int predictionsGenerated,
            int reportsGenerated
    ) {}
}