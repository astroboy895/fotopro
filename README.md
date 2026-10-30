# FOTOPRO — versión inicial 0.1

Proyecto Android nativo para probar una cámara propia en el Motorola Moto G05.

## Incluido
- Cámara propia con CameraX.
- Captura de fotos priorizando calidad.
- Conserva el original y crea una copia con optimización básica de contraste/color.
- Grabación de video Full HD/HD/SD según capacidades del dispositivo.
- Interfaz oscura y guardado en las carpetas Pictures/FOTOPRO y Movies/FOTOPRO.

## Importante
Esta primera versión NO aplica todavía IA generativa ni mejora avanzada a video. El ajuste fotográfico inicial es conservador y no puede recuperar detalle que el sensor no capturó. La mejora avanzada por IA, estabilización y procesamiento de video se desarrollarán en siguientes etapas.

La app no puede interceptar en tiempo real el obturador de la cámara Motorola. Para optimización durante la captura se utiliza la cámara propia FOTOPRO. La integración con fotos tomadas por la cámara original se agregará después mediante acceso autorizado a medios/MediaStore.

## Compilar
Abrir el proyecto en Android Studio con JDK 17 y compilar `:app:assembleDebug`.
También puede compilarse desde GitHub Actions con el workflow incluido.
