package com.app.model.admin;

import java.util.List;

/**
 * DTO para usuarios gestionados desde el panel de administracion.
 * Clase mutable (no record) para compatibilidad con Gson.
 */
public class AdminUserDTO {

    private int idUsuario;
    private String nombreCompleto;
    private String nombreUsuario;
    private String email;
    private String estado;
    private String creadoEn;
    private String tipo;
    private List<String> roles;
    private List<String> modulos;

    // Getters con defaults null-safe

    public int getIdUsuario() { return idUsuario; }

    public String getNombreCompleto() { return nombreCompleto != null ? nombreCompleto : ""; }

    public String getNombreUsuario() { return nombreUsuario != null ? nombreUsuario : ""; }

    public String getEmail() { return email != null ? email : ""; }

    public String getEstado() { return estado != null ? estado : "Activo"; }

    public String getCreadoEn() { return creadoEn != null ? creadoEn : ""; }

    public String getTipo() { return tipo != null ? tipo : "Secundario"; }

    public List<String> getRoles() { return roles != null ? roles : List.of(); }

    public List<String> getModulos() { return modulos != null ? modulos : List.of(); }

    public boolean isPrincipal() { return "Principal".equals(tipo); }

    public boolean isActivo() { return !"Inactivo".equalsIgnoreCase(estado); }
}
