# Scrolless

> **Bloqueador quirúrgico de scroll infinito (Reels, Shorts y TikTok) para Android.**  
> 100% Open Source · 0 Permisos de Red · Builds Reproducibles · Estética Minimalista Editorial.

---

## Por qué existe Scrolless

Las aplicaciones modernas mezclan canales de comunicación útiles (mensajes directos de Instagram, tutoriales largos de YouTube) con trampas de dopamina diseñadas para retener tu atención (Reels, Shorts, TikTok).

**Scrolless** no te obliga a borrar Instagram ni YouTube:
1. **Bloqueo Quirúrgico de Secciones Internas:** Detecta cuando el visor de **Instagram Reels** o el reproductor de **YouTube Shorts** ocupa la pantalla, dejando libres tus DMs, historias, el feed y los videos largos. **TikTok** se trata siempre como app completa.
2. **Interruptor "App completa":** Si preferís, Instagram y YouTube se pueden bloquear enteros con un toque ("Solo Reels / App completa", "Solo Shorts / App completa").
3. **Presupuesto de Tiempo Solo en Pestaña:** Con un límite diario (`15 min`, `5 min` o los minutos que escribas), el cronómetro corre **únicamente mientras Reels/Shorts está en pantalla** y se pausa al salir.
4. **Pantalla de Interrupción Consciente:** Al entrar te propone respirar 10 segundos antes de seguir; "Volver al inicio" te lleva al escritorio de Android. Sin tocar la música, las llamadas ni las ventanas flotantes de otras apps.
5. **Rachas con Escudos (Sin Frustración):** Suma días de racha cada vez que respetás tu límite diario. Cada 7 días limpios ganás un **escudo protector** para que un mal día puntual no destruya semanas de constancia. La tira semanal muestra tus 7 días reales.

---

## Cómo verificar que Scrolless es 100% seguro y fiel a este código

Un bloqueador quirúrgico en Android requiere usar `AccessibilityService` para identificar en qué pestaña estás. Como ese permiso es sensible, **Scrolless está diseñado bajo el principio de "No confíes, verificá" (*Don't trust, verify*)**:

### 1. Garantía Cero-Red a nivel Kernel (`Zero-Network Guarantee`)
Scrolless **no declara `android.permission.INTERNET`** en su [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml).  
En Android, sin ese permiso en el manifiesto, el kernel de Linux prohíbe al proceso abrir cualquier socket TCP/UDP. Es **físicamente imposible** que la app envíe datos fuera de tu dispositivo.

Podés auditar cualquier APK descargado ejecutando:
```bash
aapt dump permissions scrolless-release-unsigned.apk
# Salida esperada: únicamente package: com.scrolless.app (0 permisos de red)
```

### 2. Compilación Reproducible (`Reproducible Builds`)
En [`app/build.gradle.kts`](app/build.gradle.kts), los archivos generados tienen `isPreserveFileTimestamps = false` e `isReproducibleFileOrder = true`. Si clonás este repositorio y compilás con JDK 17, obtendrás el **mismo hash SHA-256 bit por bit** que el APK publicado en GitHub Releases:

```bash
git clone https://github.com/<tu-usuario>/scrolless.git
cd scrolless
gradle assembleRelease
shasum -a 256 app/build/outputs/apk/release/*.apk
```

### 3. Prueba Criptográfica de Origen (GitHub SLSA Attestation)
Los binarios de cada release los compila públicamente GitHub Actions ([`android-ci-release.yml`](.github/workflows/android-ci-release.yml)) firmando una atestación criptográfica SLSA:

```bash
gh attestation verify scrolless-release.apk --owner <tu-usuario>
```

---

## Sistema de Diseño (Inspirado en *Playa del Perelló*)

La interfaz evita el ruido visual típico de las apps de productividad y adopta una estética editorial cálida y silenciosa:

| Token | Modo Claro | Modo Oscuro | Uso |
| :--- | :--- | :--- | :--- |
| `bgColor` | `#FAF9F6` | `#151515` | Fondo continuo sin tarjetas pesadas ni sombras |
| `textPrimary` | `#2C2C2C` | `#E6E6E6` | Tipografía principal (`FontWeight.Light` / `Normal`) |
| `textSecondary` | `#8A8A8A` | `#707070` | Subtítulos con *letter-spacing* amplio |
| `borderColor` | `#EAEAEA` | `#2A2A2A` | Divisores horizontales de `1dp` |
| `success` | `#82A884` | `#5D7A5E` | Modo Pausa + 15m / Uso saludable |
| `warning` | `#D6C07E` | `#9E8E5E` | Modo Estricto 5m / Precaución |
| `danger` | `#C47B7B` | `#8A5757` | Modo Bloqueo Quirúrgico Total (0m) |

---

## Arquitectura del Proyecto

```text
app/src/main/
├── AndroidManifest.xml                  # Manifiesto sin permiso INTERNET
├── res/xml/accessibility_service_config.xml
└── java/com/scrolless/app/
    ├── core/
    │   ├── DetectionEngine.kt           # Reglas puras: nodos visibles y altos -> Reels / Shorts / app completa
    │   └── SessionAndStreakManager.kt   # SessionController puro: timer, pases, rachas e historial de 7 días
    ├── data/
    │   └── LocalStore.kt                # Persistencia local (config / runtime separados, migración v3)
    ├── service/
    │   └── ScrollessAccessibilityService.kt # Lee solo la ventana activa de las apps vigiladas
    └── ui/
        ├── PlayaTheme.kt                # Paleta y tipografía editorial
        ├── ScrollessLogo.kt             # Marca "sol en el horizonte"
        ├── DashboardScreen.kt           # Panel principal de métricas y modos
        ├── InterruptionActivity.kt      # Pantalla de interrupción consciente
        └── MainActivity.kt              # Punto de entrada
```

### Cómo detecta (sin leer ningún texto)

El servicio solo recorre nodos **visibles para el usuario** y guarda su `resource-id`, si están seleccionados y qué fracción de la pantalla ocupan. Nunca captura textos ni descripciones.

| App | Se considera formato corto cuando… | Nunca se bloquea |
| :--- | :--- | :--- |
| Instagram | `clips_viewer_view_pager` ocupa >= 60% de la altura **y** (`clips_single_media_component` también, **o** la pestaña `clips_tab` está seleccionada) | Historias (`reel_viewer_*`), bandeja e hilos de DMs |
| YouTube | `reel_watch_player`, `reel_player_page_container` o `reel_recycler` ocupa >= 60% de la altura | Inicio (estante pequeño de Shorts), videos largos |
| TikTok | Siempre (app completa) | — |

---

## Contribuir nuevos View IDs

Cuando Instagram o YouTube actualizan su interfaz pueden renombrar sus identificadores. Las builds de depuración registran cada evaluación en logcat, solo con identificadores y tamaños:

```bash
./gradlew installDebug
adb logcat -s ScrollessDiag
# pkg=com.instagram.android signal=ig-reels:media decision=interrupt ... nodes=[clips_viewer_view_pager(1.00),clips_single_media_component(1.00),clips_tab(0.06,sel)]
```

1. Activá Scrolless en Accesibilidad y navegá por la pantalla que falla.
2. Copiá la línea `ScrollessDiag` correspondiente (no contiene textos ni datos personales).
3. Ajustá la regla en `DetectionEngine.kt`, agregá el caso a `DetectionEngineTest.kt` y enviá un Pull Request.

Si una app cambia demasiado, el interruptor **"App completa"** sigue funcionando sin depender de sus IDs.

---

## Licencia

Distribuido bajo la licencia [MIT](LICENSE).
