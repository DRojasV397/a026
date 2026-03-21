package com.app.model.dashboard;

import java.util.Collections;
import java.util.List;

public class PreferencesResponseDTO {

    private boolean success;
    private int id_usuario;
    private List<UserPreferenceItemDTO> preferencias;

    public boolean isSuccess()     { return success; }
    public int getIdUsuario()      { return id_usuario; }
    public List<UserPreferenceItemDTO> getPreferencias() {
        return preferencias != null ? preferencias : Collections.emptyList();
    }
}
