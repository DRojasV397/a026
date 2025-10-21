# Sistema BI - Login (JavaFX + BootstrapFX + ReactFX)

JavaFX login application (JDK 17+, Maven) using standard JavaFX controls, BootstrapFX for modern base styling, and ReactFX for reactive behaviors. The UI replica un diseño moderno de dos paneles con validaciones, estado de carga e interacciones suaves.

## Features
- Two-panel responsive layout: branding (left) and login form (right). The branding panel hides on small screens (< 1024px).
- Standard JavaFX controls themed with BootstrapFX.
- ReactFX-driven behaviors: clear errors on typing, dynamic enable/disable, hover animations.
- Validations: email required + regex, password required + min 6 chars.
- Loading state: button + spinner with an 800ms simulated delay.
- Inline SVG icons (no extra icon dependency).

## Project Structure
```
src
 └─ main
    ├─ java
    │   └─ com/example/javafxapp
    │       ├─ Main.java
    │       └─ LoginController.java
    └─ resources
        └─ com/example/javafxapp
            ├─ login-view.fxml
            └─ styles.css
```

## Requirements
- JDK 17 or higher
- Maven 3.8+

## Run with Maven
Activa tu perfil de SO para establecer correctamente el classifier de JavaFX:

- Windows:
```
mvn -Pwindows clean javafx:run
```
- Linux:
```
mvn -Plinux clean javafx:run
```
- macOS (Intel):
```
mvn -Pmac clean javafx:run
```
- macOS (Apple Silicon):
```
mvn -Pmac-aarch64 clean javafx:run
```

Build only:
```
mvn -P<tu-os> clean package
```

## Run in IntelliJ IDEA
1. Open the project and ensure JDK 17+ is selected.
2. Let Maven import dependencies.
3. Activate the appropriate Maven profile (`windows`, `linux`, `mac`, or `mac-aarch64`).
4. In Maven tool window, run `javafx:run`.
   - or create an Application run config with main class `com.example.javafxapp.Main`.

## Notes
- Iconography is implemented via inline SVGPath nodes. You can swap to FontAwesomeFX/Ikonli if preferred.
- Styles are in `styles.css` and applied by Main.java.

## Troubleshooting
- If dependencies don’t resolve, check your internet connection and try `mvn -U -P<your-os> clean javafx:run`.
- On macOS Apple Silicon, ensure the `mac-aarch64` profile is used.
- If you see InaccessibleObjectException or JFoenix skin errors on JDK 17+, make sure JVM options are applied. The Maven run already passes them. For IDE run configurations, add these VM options:
  
  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
  --add-opens=javafx.controls/javafx.scene.control=ALL-UNNAMED \
  --add-opens=javafx.controls/javafx.scene.control.skin=ALL-UNNAMED \
  --add-exports=javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED \
  --add-exports=javafx.controls/com.sun.javafx.scene.control.behavior=ALL-UNNAMED \
  --add-exports=javafx.controls/com.sun.javafx.scene.control.inputmap=ALL-UNNAMED \
  --add-exports=javafx.graphics/com.sun.javafx.stage=ALL-UNNAMED
