# Glyph Matrix SDK (obligatorio, no incluido en este repo)

Esta carpeta debe contener el archivo `glyph-matrix-sdk-2.0.aar` oficial de Nothing.
No se incluye en el repositorio porque su licencia (ver `LICENSE.md` en el repo del
SDK) prohíbe la redistribución.

## Cómo obtenerlo

1. Ve a https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit
2. Descarga el archivo `glyph-matrix-sdk-2.0.aar` que está en la raíz del repositorio
   (botón "Download raw file" o `git clone` del repo).
3. Copia ese archivo aquí, en `app/libs/glyph-matrix-sdk-2.0.aar`, respetando
   exactamente ese nombre (así lo referencia `app/build.gradle.kts`).

Sin este archivo, la compilación fallará con un error de "no se encuentra el archivo"
al intentar resolver la dependencia `files("libs/glyph-matrix-sdk-2.0.aar")`.
