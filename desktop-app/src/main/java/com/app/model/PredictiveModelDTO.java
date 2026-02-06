package com.app.model;

import java.util.List;

public record PredictiveModelDTO(
        String title,
        String description,
        List<String> tags,
        String detailMessage,
        String icon,
        String presicion,
        String tiempoTrain
) {}

