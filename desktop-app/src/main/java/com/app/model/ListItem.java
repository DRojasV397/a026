package com.app.model;

/**
 * Modelo de datos para items de lista
 */
public class ListItem {
    private int id;
    private String title;
    private String subtitle;
    private String value;
    private String iconPath;
    private String badge;  // Opcional: badge o etiqueta
    private ItemType type;

    public enum ItemType {
        DEFAULT,
        SUCCESS,
        WARNING,
        INFO
    }

    public ListItem() {
        this.type = ItemType.DEFAULT;
    }

    public ListItem(int id, String title, String subtitle, String value) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.value = value;
        this.type = ItemType.DEFAULT;
    }

    public ListItem(int id, String title, String subtitle, String value, String iconPath, ItemType type) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.value = value;
        this.iconPath = iconPath;
        this.type = type;
    }

    // Getters y Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getIconPath() {
        return iconPath;
    }

    public void setIconPath(String iconPath) {
        this.iconPath = iconPath;
    }

    public String getBadge() {
        return badge;
    }

    public void setBadge(String badge) {
        this.badge = badge;
    }

    public ItemType getType() {
        return type;
    }

    public void setType(ItemType type) {
        this.type = type;
    }
}