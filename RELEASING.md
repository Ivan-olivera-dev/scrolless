# Publicar Scrolless

Todo lo hace GitHub Actions. Tú solo creas una etiqueta.

## Preparación (una sola vez)

1. Crea la clave de firma:
   ```bash
   ./scripts/create-release-key.sh
   ```
   Genera `~/.scrolless/release.jks` y te pide una contraseña.
2. Guarda **el archivo `.jks` y la contraseña** en tu gestor de contraseñas.
3. Sube los 4 secretos al repo (el script te recuerda los comandos):
   ```bash
   gh secret set SCROLLESS_KEYSTORE_BASE64 < <(base64 -w0 "$HOME/.scrolless/release.jks")
   gh secret set SCROLLESS_KEYSTORE_PASSWORD
   gh secret set SCROLLESS_KEY_ALIAS        # scrolless
   gh secret set SCROLLESS_KEY_PASSWORD     # misma contraseña
   ```

> **Importante:** si pierdes la clave, no podrás firmar actualizaciones con la misma
> firma. Android las rechazará y cada usuario tendrá que **desinstalar** la app
> (y perder su racha) para instalar la nueva. Haz la copia de seguridad.

## Sacar una versión

```bash
git tag v1.0.0 && git push origin v1.0.0
```

En unos minutos aparece en **GitHub → Releases** con:

- `scrolless-1.0.0.apk` (firmado)
- `scrolless-1.0.0.apk.sha256`
- notas generadas a partir de los commits y la atestación SLSA en **Actions → Attestations**.

Si falta algún secreto, el workflow falla con un mensaje claro y **no** publica nada sin firmar.

## Para los usuarios

Pueden descargar el APK desde Releases o seguir el repo con
[Obtainium](https://github.com/ImranR98/Obtainium), que avisa e instala cada versión nueva.
