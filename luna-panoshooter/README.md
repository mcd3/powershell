# Luna PanoShooter

Aplicación Android para la Lenovo TB-J616F que automatiza la captura de panoramas esféricos de alta resolución con la Insta360 Luna.

## Configuración de cámara validada

- Foto
- UHD 4:3
- Objetivo 1× con gran angular
- JPG + RAW
- Tablet en vertical, 1200 × 2000

## Secuencia de captura

La versión beta realiza 31 fotografías:

1. 9 fotografías en la fila central.
2. 9 fotografías en la fila superior (+3 pasos).
3. 4 fotografías en el límite superior, en posiciones horizontales −3, −1, +1 y +3.
4. 9 fotografías en la fila inferior.

## Uso

1. Instalar la APK.
2. Abrir `Luna PanoShooter`.
3. Activar `Control Luna PanoShooter` en Ajustes de accesibilidad.
4. Abrir Insta360 y conectar la Luna.
5. Configurar la cámara y colocarla manualmente mirando al centro.
6. Usar el control flotante: `INICIAR`, `PAUSA/SEGUIR` o `DETENER`.

La aplicación usa un servicio de accesibilidad exclusivamente para reproducir los gestos calibrados del joystick y del disparador. No lee textos, contraseñas ni contenido de otras aplicaciones.

## Estado

Beta inicial para pruebas sobre Lenovo TB-J616F. Las coordenadas se escalan proporcionalmente desde la resolución de referencia 1200 × 2000.
