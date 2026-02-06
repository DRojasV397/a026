package com.app.model;

public record StatsDTO(
        String emoji,
        String value,
        String label,
        String change,
        String colorClass,
        boolean positive
) {}

