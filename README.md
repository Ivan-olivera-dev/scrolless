# Scrolless

> **Bloqueador quirúrgico de scroll infinito (Reels, Shorts y TikTok) para Android.**  
> 100% Open Source · 0 Permisos de Red · Builds Reproducibles · Estética Minimalista Editorial.

---

## Por qué existe Scrolless

Las aplicaciones modernas mezclan canales de comunicación útiles (mensajes directos de Instagram, tutoriales largos de YouTube) con trampas de dopamina diseñadas para retener tu atención (Reels, Shorts, TikTok).

**Scrolless** no te obliga a borrar Instagram ni YouTube:
1. **Bloqueo Quirúrgico de Secciones Internas:** Detecta en tiempo real cuando entrás específicamente a la pestaña de **Instagram Reels**, **YouTube Shorts** o **TikTok**, dejando libres tus DMs, historias y videos horizontales.
2. **Presupuesto de Tiempo Solo en Pestaña:** Si elegís un límite diario (ej. `15 min/día` o `5 min/día`), el cronómetro corre **únicamente mientras estás dentro de la pestaña de Reels/Shorts** y se pausa en el instante en que salís.
3. **Pantalla de Interrupción Consciente:** Al abrir un Reel/Short o agotar tu margen diario, despliega una pantalla de interrupción editorial que te invita a respirar 10 segundos o volver atrás (`GLOBAL_ACTION_BACK`) con un toque.
4. **Rachas con Escudos (Sin Frustración):** Suma días de racha cada vez que respetás tu límite diario. Cada 7 días limpios ganás un **escudo protector** para que un mal día puntual no destruya semanas de constancia.

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
    │   ├── DetectionEngine.kt           # Detector quirúrgico de nodos (Reels, Shorts, TikTok)
    │   └── SessionAndStreakManager.kt   # Máquina de estados: timer solo en pestaña + rachas
    ├── data/
    │   └── LocalStore.kt                # Persistencia 100% local en el dispositivo
    ├── service/
    │   └── ScrollessAccessibilityService.kt # Servicio de accesibilidad + Inspector de View IDs
    └── ui/
        ├── PlayaTheme.kt                # Paleta y tipografía editorial
        ├── DashboardScreen.kt           # Panel principal de métricas y modos
        ├── InterruptionActivity.kt      # Pantalla de interrupción consciente
        └── MainActivity.kt              # Punto de entrada
```

---

## Contribuir nuevos View IDs (Modo Inspector)

Cuando Instagram o YouTube actualizan su interfaz, pueden renombrar los identificadores internos de su reproductor vertical:
1. Abrí **Scrolless** y tocá **"Abrir inspector de View IDs para colaboradores →"** al pie de la pantalla.
2. Entrá a la pestaña de Reels o Shorts y volvé a Scrolless para ver los `resource-id` capturados en vivo.
3. Agregá el nuevo identificador en `DetectionEngine.kt` y enviá un Pull Request.

---

## Licencia

Distribuido bajo la licencia [MIT](LICENSE).
