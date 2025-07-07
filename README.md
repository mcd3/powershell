Organizador de Archivos por Fecha de Modificación (PowerShell)
Este script de PowerShell organiza automáticamente los archivos del directorio actual en carpetas basadas en su fecha de última modificación.
¿Qué hace?
Detecta el directorio actual (Get-Location).
Busca todos los archivos en ese directorio, ignorando subcarpetas.
Para cada archivo:
Obtiene su fecha de última modificación.
Crea una carpeta con el nombre de la fecha en formato AAAA-MM-DD, si no existe.
Mueve el archivo a esa carpeta correspondiente
