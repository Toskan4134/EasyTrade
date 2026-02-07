# EasyTrade

Un plugin para servidores de Hytale que permite intercambios seguros de objetos entre jugadores con una interfaz interactiva y transacciones atómicas.

## Características

- **Interfaz de Intercambio Interactiva** - Selecciona objetos para intercambiar con controles de cantidad
- **Transacciones Atómicas Seguras** - Los objetos se intercambian simultáneamente o no se intercambian en absoluto
- **Validación Inteligente de Inventario** - Verifica inteligentemente si los objetos pueden apilarse con el inventario existente
- **Sistema de Cuenta Regresiva** - Cuenta regresiva de 3 segundos antes de ejecutar el intercambio para prevenir accidentes
- **Mensajes Personalizables** - Sistema de mensajes completamente traducible con soporte para códigos de color
- **Modo de Prueba** - Pruebas en solitario para desarrollo y depuración
- **Verificador de Actualizaciones** - Verifica automáticamente en GitHub y CurseForge nuevas versiones
- **Modo de Depuración** - Registro detallado para solución de problemas

## Cómo Funciona

1. El Jugador A envía una solicitud de intercambio al Jugador B: `/trade request JugadorB`
2. El Jugador B acepta la solicitud: `/trade accept`
3. Ambos jugadores abren la interfaz de intercambio y agregan objetos a sus ofertas
4. Ambos jugadores hacen clic en **ACEPTAR** cuando estén listos
5. Comienza la cuenta regresiva de 3 segundos - ambos jugadores aún pueden cancelar
6. Después de la cuenta regresiva, ambos jugadores hacen clic en **CONFIRMAR** para completar el intercambio
7. Los objetos se intercambian atómicamente (todo o nada)

## Comandos

| Comando | Descripción | Permiso |
|---------|-------------|---------|
| `/trade request <jugador>` | Enviar una solicitud de intercambio a otro jugador | `easytrade.trade.request` |
| `/trade accept` | Aceptar una solicitud de intercambio pendiente | `easytrade.trade.accept` |
| `/trade decline` | Rechazar una solicitud de intercambio pendiente | `easytrade.trade.decline` |
| `/trade cancel` | Cancelar el intercambio actual | `easytrade.trade.cancel` |
| `/trade confirm` | Confirmar intercambio después de la cuenta regresiva | `easytrade.trade.confirm` |
| `/trade open` | Abrir la interfaz de intercambio | `easytrade.trade.open` |
| `/trade reload` | Recargar configuración y mensajes | `easytrade.admin.reload` |
| `/trade test` | Iniciar intercambio de prueba en solitario (solo en modo depuración) | `easytrade.admin.test` |
| `/trade help` | Mostrar mensaje de ayuda | `easytrade.trade.help` |

**Nota:** Todos los permisos están configurados como verdaderos por defecto para todos los jugadores excepto los comandos de administrador.

## Configuración

La configuración se guarda automáticamente en `mods/Toskan4134_EasyTrade/EasyTrade.json`

| Opción | Por Defecto | Descripción |
|--------|-------------|-------------|
| `Debug` | `false` | Habilitar registro de depuración detallado |
| `RequestTimeoutSeconds` | `30000` | Milisegundos antes de que expire la solicitud de intercambio |
| `CountdownDurationSeconds` | `3000` | Duración de la cuenta regresiva en milisegundos antes de ejecutar el intercambio |
| `CheckForUpdates` | `true` | Verificar actualizaciones del plugin al iniciar |

### Ejemplo de Configuración

```json
{
  "CountdownDuration": 3000,
  "RequestTimeout": 30000,
  "CheckForUpdates": true,
  "Debug": true
}
```

## Mensajes

Los mensajes se almacenan en `plugins/EasyTrade/messages.json` y soportan códigos de color y marcadores de posición.

### Códigos de Color

- `&0-9, &a-f` - Colores estándar de Minecraft
- `&#RRGGBB` - Colores hexadecimales (ej., `&#FF0000` para rojo)
- `&l` - Texto en negrita
- `&r` - Restablecer formato

### Marcadores de Posición

- `{player}` - Nombre del jugador
- `{target}` - Nombre del jugador objetivo
- `{seconds}` - Segundos de cuenta regresiva/tiempo de espera
- `{version}` - Versión del plugin

### Ejemplo de Mensajes

```properties
trade.request.sent=&aSolicitud de intercambio enviada a &f{target}
trade.request.received=&eSolicitud de intercambio recibida de &f{initiator}&e. Usa &6/trade accept &7para aceptar.
trade.status.countdown=&a¡Ambos aceptaron! Completando en &f{seconds}s&a...
ui.status.notEnoughSpace=No tienes suficiente espacio en el inventario
```
> Puedes encontrar algunos ejemplos de messages.json [AQUÍ](https://github.com/Toskan4134/EasyTrade/tree/main/examples/messages)

## Interfaz de Intercambio

La interfaz de intercambio está dividida en secciones:

- **Tu Inventario** - Objetos disponibles para ofrecer. Busca en inventario, barra de acceso rápido y mochila (abajo)
- **Tu Oferta** - Objetos que estás ofreciendo (izquierda)
- **Oferta del Compañero** - Objetos que tu compañero está ofreciendo (derecha)
- **Mensaje de Estado** - Muestra el estado del intercambio e instrucciones
- **Botones de Acción** - ACEPTAR, CONFIRMAR, CANCELAR

### Agregar Objetos a la Oferta

- **Clic en objeto** - Agregar 1 pila completa
- **Clic en botón +1** - Agregar 1 objeto a la oferta existente
- **Clic en botón +10** - Agregar 10 objetos a la oferta existente

### Eliminar Objetos de la Oferta

- **Clic en objeto ofrecido** - Eliminar 1 pila completa
- **Clic en botón -1** - Eliminar 1 objeto de la oferta
- **Clic en botón -10** - Eliminar 10 objetos de la oferta

### Estados del Intercambio (Depuración)

1. **NEGOTIATING** - Ambos jugadores pueden modificar ofertas
2. **ONE_ACCEPTED** - Un jugador ha aceptado, esperando al otro
3. **BOTH_ACCEPTED_COUNTDOWN** - Cuenta regresiva en progreso (3 segundos)
4. **EXECUTING** - El intercambio se está ejecutando (transacción atómica)
5. **COMPLETED** - Intercambio exitoso
6. **CANCELLED** - El intercambio fue cancelado
7. **FAILED** - El intercambio falló (error de validación, problemas de inventario)

### Características de Seguridad

- **Auto-desaceptar** - Si el inventario cambia, la aceptación se revoca automáticamente
- **Validación de inventario** - Verifica si los objetos aún están disponibles antes de ejecutar
- **Validación inteligente de espacio** - Verifica inteligentemente si los objetos pueden apilarse con el inventario existente
- **Transacciones atómicas** - Todos los objetos se intercambian o ninguno (reversión en caso de fallo)
- **Manejo de desconexión del compañero** - El intercambio se cancela y la interfaz se cierra si el compañero se desconecta

## Permisos

Todos los permisos siguen el patrón `easytrade.<categoría>.<comando>`.
Puedes modificar los permisos usando `/perm group/user list/add/remove <grupo/usuario>`

### Permisos por Defecto (Todos los Jugadores)

Estos permisos deberían otorgarse a todos:

```
easytrade.trade.request   - Enviar solicitudes de intercambio
easytrade.trade.accept    - Aceptar solicitudes de intercambio
easytrade.trade.decline   - Rechazar solicitudes de intercambio
easytrade.trade.cancel    - Cancelar intercambios activos
easytrade.trade.confirm   - Confirmar intercambios después de la cuenta regresiva
easytrade.trade.open      - Abrir interfaz de intercambio
easytrade.trade.help      - Ver mensaje de ayuda
```

### Permisos de Administrador

Estos permisos deberían estar restringidos a administradores/operadores:

```
easytrade.admin.reload    - Recargar configuración y mensajes
easytrade.admin.test      - Iniciar intercambios de prueba en solitario (requiere modo depuración)
```

### Comportamiento por Defecto

**Por defecto, NO se requieren permisos:**
- ✅ Todos los jugadores pueden usar todos los comandos de intercambio (no se necesitan permisos)
- ❌ Solo `/trade reload` requiere permisos (`easytrade.admin.reload`)
- 🐞 El comando `/trade test` requiere `debug=true` en la configuración y permisos (`easytrade.admin.test`)

**Solo necesitas configurar permisos si quieres RESTRINGIR el acceso a los comandos de intercambio.**

## Instalación

1. Compila el archivo JAR del plugin o descárgalo desde las versiones
2. Coloca el JAR en la carpeta `mods` de tu servidor
3. Inicia/reinicia el servidor
4. Los archivos de configuración y mensajes se generarán automáticamente
5. Personaliza los mensajes en `plugins/EasyTrade/messages.json` y la configuración en `mods/Toskan4134_EasyTrade/EasyTrade.json` si lo deseas

## Compilación

```bash
./gradlew build
```

El JAR compilado estará ubicado en `build/libs/`

### Configuración de Desarrollo

1. Clona el repositorio
2. Importa en IntelliJ IDEA como un proyecto Gradle
3. Configura la configuración de ejecución para apuntar al servidor de Hytale
4. Habilita el modo de depuración en la configuración para registro detallado

## Verificador de Actualizaciones

El plugin verifica automáticamente actualizaciones desde GitHub y CurseForge:
- Verifica al iniciar el servidor
- Verifica cada 12 horas mientras el servidor está en ejecución
- Registra en consola cuando una nueva versión está disponible
- Notifica a los operadores (jugadores con permiso `*`) cuando se conectan

Establece `CheckForUpdates` en `false` en la configuración para deshabilitarlo.

## Licencia

Licencia MIT

## Autor

Toskan4134

## Enlaces

- **GitHub**: https://github.com/Toskan4134/EasyTrade
- **CurseForge**: https://www.curseforge.com/hytale/mods/easytrade
