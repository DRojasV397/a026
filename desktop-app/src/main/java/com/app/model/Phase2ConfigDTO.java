package com.app.model;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO para almacenar la configuración de parámetros de la Fase 2
 * ACTUALIZADO: Detección de errores de fechas
 */
public class Phase2ConfigDTO {
    private LocalDate startDate;
    private LocalDate endDate;
    private List<String> selectedVariables;
    private int predictionHorizon; // en meses

    // Validaciones
    private boolean hasEnoughData;
    private boolean hasValidVariables;
    private boolean hasNoErrors;

    private String errorMessage;

    public Phase2ConfigDTO() {
        this.hasEnoughData = false;
        this.hasValidVariables = false;
        this.hasNoErrors = true;
        this.errorMessage = "";
    }

    // Getters y Setters
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
        updateValidations();
    }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
        updateValidations();
    }

    public List<String> getSelectedVariables() { return selectedVariables; }
    public void setSelectedVariables(List<String> selectedVariables) {
        this.selectedVariables = selectedVariables;
        updateValidations();
    }

    public int getPredictionHorizon() { return predictionHorizon; }
    public void setPredictionHorizon(int predictionHorizon) {
        this.predictionHorizon = predictionHorizon;
        updateValidations();
    }

    public int getTrainPercentage() {
        return 70; }
    public int getValidationPercentage() {
        return 30; }

    public boolean isHasEnoughData() { return hasEnoughData; }
    public boolean isHasValidVariables() { return hasValidVariables; }
    public boolean isHasNoErrors() { return hasNoErrors; }

    public String getErrorMessage() { return errorMessage; }

    /**
     * Actualiza las validaciones basadas en los datos actuales
     */
    private void updateValidations() {
        // Resetear mensaje de error
        errorMessage = "";

        // Validar que fecha inicio < fecha fin
        if (startDate != null && endDate != null) {
            if (endDate.isBefore(startDate)) {
                hasNoErrors = false;
                errorMessage = "La fecha de inicio no puede ser posterior a la fecha de fin";
            } else if (startDate.equals(endDate)) {
                hasNoErrors = false;
                errorMessage = "Las fechas de inicio y fin no pueden ser iguales";
            } else {
                hasNoErrors = true;
                errorMessage = "";
            }
        } else {
            hasNoErrors = true; // No hay error si falta alguna fecha
        }

        // Validar que haya suficientes datos (eal menos 12 meses de registros)
        if (startDate != null && endDate != null && hasNoErrors) {
            long months = java.time.temporal.ChronoUnit.MONTHS.between(startDate, endDate);
            hasEnoughData = months >= 12;

            if (!hasEnoughData && errorMessage.isEmpty()) {
                errorMessage = "Se requieren al menos 12 meses de datos históricos";
            }
        } else {
            hasEnoughData = false;
        }

        // Validar que haya al menos 1 variable seleccionada
        hasValidVariables = selectedVariables != null && !selectedVariables.isEmpty();
    }

    /**
     * Verifica si la configuración es válida para continuar
     */
    public boolean isValid() {
        return hasEnoughData && hasValidVariables && hasNoErrors &&
                predictionHorizon >= 1 && predictionHorizon <= 6;
    }
}