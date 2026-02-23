package com.app.model.data.api;

/**
 * DTO para un producto del catálogo del usuario.
 * Mapea la respuesta del endpoint GET /productos/
 */
public class ProductoCatalogDTO {

    private int     idProducto;
    private String  sku;
    private String  nombre;
    private Integer idCategoria;
    private String  categoriaNombre;
    private Double  precioUnitario;
    private Double  costoUnitario;
    private Integer activo;
    private Integer stock;
    private Integer stockMinimo;
    private Integer stockMaximo;
    private String  ubicacion;

    public int     getIdProducto()       { return idProducto; }
    public String  getSku()              { return sku != null ? sku : ""; }
    public String  getNombre()           { return nombre != null ? nombre : ""; }
    public Integer getIdCategoria()      { return idCategoria; }
    public String  getCategoriaNombre()  { return categoriaNombre != null ? categoriaNombre : "Sin categoría"; }
    public Double  getPrecioUnitario()   { return precioUnitario; }
    public Double  getCostoUnitario()    { return costoUnitario; }
    public Integer getActivo()           { return activo; }
    public boolean isActivo()            { return activo != null && activo == 1; }
    public Integer getStock()            { return stock; }
    public Integer getStockMinimo()      { return stockMinimo; }
    public Integer getStockMaximo()      { return stockMaximo; }
    public String  getUbicacion()        { return ubicacion != null ? ubicacion : ""; }

    /** Margen bruto en porcentaje, formateado. */
    public String getMargen() {
        if (precioUnitario != null && costoUnitario != null && precioUnitario > 0) {
            double margen = ((precioUnitario - costoUnitario) / precioUnitario) * 100.0;
            return String.format("%.1f%%", margen);
        }
        return "N/D";
    }

    /** Precio formateado como "$1,234.56" o "—" si nulo. */
    public String getPrecioFormateado() {
        return precioUnitario != null ? String.format("$%,.2f", precioUnitario) : "—";
    }

    /** Costo formateado como "$1,234.56" o "—" si nulo. */
    public String getCostoFormateado() {
        return costoUnitario != null ? String.format("$%,.2f", costoUnitario) : "—";
    }
}
