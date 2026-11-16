#!/usr/bin/env bash
# One-time helper: creates the Scrolless release signing key.
# It never uploads anything and never calls `gh`; it only prints the next steps.
set -euo pipefail

KEYSTORE_DIR="$HOME/.scrolless"
KEYSTORE="$KEYSTORE_DIR/release.jks"
ALIAS="scrolless"

if ! command -v keytool >/dev/null 2>&1; then
  echo "Error: keytool no encontrado. Instala un JDK (p. ej. Temurin 21) y vuelve a intentarlo." >&2
  exit 1
fi

if [ -e "$KEYSTORE" ]; then
  echo "Error: ya existe $KEYSTORE. No se sobrescribe." >&2
  echo "Si de verdad quieres una clave nueva, mueve la actual a mano (los usuarios tendrán que desinstalar para actualizar)." >&2
  exit 1
fi

mkdir -p "$KEYSTORE_DIR"
chmod 700 "$KEYSTORE_DIR"

echo "Creando $KEYSTORE (alias: $ALIAS)."
echo "keytool te pedirá la contraseña de forma interactiva; no se guarda en ningún sitio más."
echo

keytool -genkeypair -v \
  -storetype PKCS12 \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -alias "$ALIAS" \
  -keystore "$KEYSTORE"

chmod 600 "$KEYSTORE"

cat <<EOF

Listo: $KEYSTORE

Siguientes pasos (una sola vez):

1. Copia de seguridad AHORA: guarda el archivo $KEYSTORE y la contraseña
   en tu gestor de contraseñas. Si se pierde, los usuarios tendrán que
   desinstalar la app para poder actualizar.

2. Sube los 4 secretos al repositorio (desde la carpeta del repo):

   gh secret set SCROLLESS_KEYSTORE_BASE64 < <(base64 -w0 "$KEYSTORE")
   gh secret set SCROLLESS_KEYSTORE_PASSWORD   # te pedirá el valor
   gh secret set SCROLLESS_KEY_ALIAS           # escribe: $ALIAS
   gh secret set SCROLLESS_KEY_PASSWORD        # con PKCS12 es la misma contraseña

3. Saca una versión: git tag v1.0.0 && git push origin v1.0.0  (ver RELEASING.md)
EOF
