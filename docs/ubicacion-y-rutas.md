# Ubicación de la barbería y Cómo llegar

## Uso

- Barbero habilitado: Mi cuenta → Ubicación de la barbería → tocar el mapa para colocar o mover el pin → Guardar ubicación.
- Cliente: Mis turnos → Cómo llegar. Abre una pantalla interna con la ubicación del barbero de ese turno, solicita ubicación y calcula un recorrido en auto.
- Con GPS apagado se ofrece abrir la configuración de ubicación del sistema. Android requiere que el usuario lo active; la app no lo enciende silenciosamente.
- El permiso es de primer plano, preciso o aproximado. Si se rechaza, se puede marcar el origen manualmente. La adquisición puntual se cancela al salir de la pantalla o elegir un punto manual.
- Las coordenadas del cliente se envían a OSRM al solicitar la ruta, pero no se guardan en Firestore.
- Solo se guardan coordenadas del local, sin dirección escrita ni referencias. Si el barbero cambia de ubicación, Cómo llegar usa la ubicación actual también para los turnos existentes.

## Estructura

- `core:domain`: Coordenadas, RutaBarberia, contratos de repositorios y casos de uso.
- `core:data`: FirestoreUbicacionBarberiaRepository y OsrmRutaRepository.
- `core:network`: RutasApi, DTO de GeoJSON y cliente Retrofit sin registro de coordenadas en logs.
- `core:ui`: MapaBarberia (MapLibre Native con OpenStreetMap), adquisición de ubicación y permisos Android.
- `features:cuenta/ubicacion`: Route, Screen, UiState y ViewModel del editor.
- `features:turnos/ubicacion`: Route, Screen, UiState y ViewModel de Cómo llegar.
- `app`: inyección manual y destinos de Navigation 3.

## Firestore: despliegue necesario

Se agregan `Ubicacion` (GeoPoint) y `UbicacionActualizadaEn` (timestamp del servidor) al documento existente `barberos/{uid}`. No se necesita migrar documentos: la ausencia de ubicación se muestra explícitamente.

Publicar las reglas de `firestore.rules` en el proyecto Firebase de la app antes de probar el guardado contra el servicio real. Solo el propietario habilitado puede modificar estos dos campos; clientes autenticados pueden leer la ubicación de barberos habilitados. El repositorio usa una transacción para confirmar el guardado con el servidor.

El desarrollo y las pruebas locales no despliegan reglas a Firebase automáticamente.

## Mapas y rutas

MapLibre Native 13.6.1 muestra mosaicos raster de `https://tile.openstreetmap.org`. La atribución a OpenStreetMap está visible, las peticiones identifican la aplicación y el SDK conserva su caché HTTP. Se desactiva la precarga de zoom y no se ofrecen descargas offline.

El servidor predeterminado es `https://router.project-osrm.org/`, servicio público de demostración de OSRM. No requiere clave, pero no tiene disponibilidad garantizada y no debe asumirse como infraestructura ilimitada para producción. Se limita a una solicitud por segundo por instancia del repositorio, se cancelan consultas obsoletas y no se consulta continuamente mientras el usuario se mueve.

El perfil usado es `driving`: devuelve un recorrido en auto optimizado según el motor. La UI no promete la menor distancia ni tráfico en vivo, y no ofrece indicaciones por voz o navegación continua.

Para usar otro servidor compatible con OSRM, configurar en `local.properties` y recompilar:

```properties
rutas.baseUrl=https://servidor-osrm.example/
```

La URL debe usar HTTPS y terminar en `/`. Para distribuir ampliamente la app, contratar/alojar un servicio de rutas y revisar el proveedor de mapas según el volumen esperado.

Referencias: [MapLibre](https://maplibre.org/maplibre-native/android/examples/getting-started/), [política de mosaicos OSM](https://operations.osmfoundation.org/policies/tiles/), [API OSRM](https://project-osrm.org/docs/v26.6.1/http).

## Verificación manual

1. Con un barbero habilitado, seleccionar y guardar un pin. Salir y volver: debe conservarlo.
2. Con un cliente que tenga un turno con ese barbero, abrir Cómo llegar: el pin debe coincidir.
3. Probar GPS apagado y aceptar/rechazar su activación; permisos precisos, aproximados, denegados y denegados permanentemente.
4. Cambiar manualmente el origen mientras se busca GPS o se calcula una ruta: prevalece la última selección.
5. Probar falta de conexión, barbería sin ubicación, giro de pantalla y volver atrás.
6. Probar dos turnos de distintos barberos con ubicaciones distintas.
