# Promesa

Aplicación Android en Kotlin y Jetpack Compose que actúa como filtro de contenido web personal contra apuestas deportivas mediante una VPN local (`VpnService`), bloqueo de búsquedas en Google, protección por PIN y DNS cifrado permanente (`53cf8e.dns.nextdns.io`).

## Características principales

- **VPN local DNS (`PromesaVpnService`)**: Intercepta consultas DNS IPv4/IPv6 salientes y bloquea dominios y subdominios (`*.dominio`) de casas de apuestas, casinos, tipsters y cripto-apuestas configurados, reenviando el resto cifrado por DoT/DoH exclusivamente a `53cf8e.dns.nextdns.io`.
- **Pantalla de bloqueo (`BlockedActivity`)**: Fondo oscuro `#1A1A2E`, acento `#E24B4A`, ícono de candado cerrado, mensaje principal *"Se lo prometiste a tu mamá"*, segundo mensaje rotatorio, botón **Modo Crisis** (WhatsApp directo a contacto de apoyo) y botón **Cerrar**.
- **Control por PIN**: Mínimo 4 dígitos (hash SHA-256 con salt). Pausa temporal fija de 15 minutos y bloqueo de seguridad de 30 minutos tras 3 intentos fallidos consecutivos.
- **Guardia de Accesibilidad (`PromesaGuardAccessibilityService`)**: Protege contra desinstalación pidiendo el PIN y bloquea búsquedas de apuestas en Google.
- **Persistencia**: Arranque automático (`BOOT_COMPLETED`), `startForeground` con prioridad máxima, exención de optimización de batería, `android:installLocation="internalOnly"`, log local con fecha/hora/dominio, alerta silenciosa al superar 3 intentos en una hora y widget de días sin intentos bloqueados.

## Compilar APK en Android Studio o terminal

1. Clona el repositorio y abre la carpeta raíz en **Android Studio** (requiere JDK 17 y Android SDK 36, `minSdk = 26`).
2. Antes de compilar localmente por primera vez, genera el `debug.keystore` desde el archivo incluido:
   ```bash
   base64 -d debug.keystore.base64 > debug.keystore
   ```
3. Ejecuta **Build > Build Bundle(s) / APK(s) > Build APK(s)** en Android Studio o desde la terminal:
   ```bash
   gradle :app:assembleDebug
   ```

## Compilación automática en GitHub Actions

El flujo incluido en `.github/workflows/build-apk.yml` compila automáticamente el APK en cada push a `main` / `master` (o ejecución manual desde la pestaña **Actions**) y lo publica como artefacto descargable llamado **`Promesa-APK`**.
