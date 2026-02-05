package com.app.model;

public record TrainingResult(
        String time,
        String metric,
        boolean success
) {}

