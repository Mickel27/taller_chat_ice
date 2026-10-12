# Plataforma Distribuida de Chat Multimedia y Streaming de Voz

**Computación en Internet I (09810 - TIC)**  
**Universidad Icesi** — Facultad de Ingeniería, Diseño y Ciencias Aplicadas  
Departamento de TIC y Sistemas Inteligentes  
**Semestre:** 2026-2 | **NRC:** 12378 / 12377 | **Ponderación:** 8.0% (Taller 2 – Unidad 2)

---

## 1. Información del Equipo

* **Estudiante 1:** Luis Miguel Delgado — **Código:** A00410889
* **Estudiante 2:** Miguel Angel Blanco — **Código:** A00410773
* **Tipo de Actividad:** Taller Evaluativo / Proyecto Distribuido
* **Política IAG:** Nivel 3: Colaboración Asistida

---

## 2. Arquitectura del Sistema

El sistema implementa una **arquitectura híbrida distribuida cliente-servidor** desacoplada estratégicamente en dos canales de transporte según sus requerimientos de fiabilidad y latencia:

```
                            +-----------------------------------------------+
                            |                 SERVIDOR ICE                  |
                            |                                               |
                            |  +------------------+  +-------------------+  |
                            |  |  ChatServiceI    |  |  SessionManager   |  |
                            |  |  (RPC Handler)   |  |  (Presence/Users) |  |
                            |  +------------------+  +-------------------+  |
                            |  +------------------+  +-------------------+  |
                            |  |  RoomManager     |  | AudioCallManager  |  |
                            |  |  (Groups/Rooms)  |  | (Call Signaling)  |  |
                            |  +------------------+  +-------------------+  |
                            |                         ^                     |
                            +-------------------------|---------------------+
                                                      |
                 [Canal TCP: ZeroC Ice 3.7.10]        |  [Canal UDP: Datagramas]
                 - Login / Presencia                  |  - Audio Streaming (16kHz PCM)
                 - Mensajes 1-a-1 & Grupos            |  - SFU Audio Relay (Port 10001)
                 - Archivos por Chunks                |  - Registro y reenvío de voz
                                                      |
                            +-------------------------v---------------------+
                            |             UdpAudioRelayServer (SFU)         |
                            +-----------------------------------------------+
                                      |                           |
                     Paquetes UDP     |                           | Paquetes UDP
                     (Voz en vivo)    |                           | (Voz en vivo)
                                      v                           v
                        +---------------------------+   +---------------------------+
                        |         CLIENTE A         |   |         CLIENTE B         |
                        | - ChatCallbackI (Ice TCP) |   | - ChatCallbackI (Ice TCP) |
                        | - UdpAudioClient (UDP)    |   | - UdpAudioClient (UDP)    |
                        | - AudioRecorder/Player    |   | - AudioRecorder/Player    |
                        +---------------------------+   +---------------------------+
```

### Canales de Comunicación
1. **Canal de Control, Señalización y Transferencia (ZeroC Ice / TCP):**
   * **Protocolo:** TCP gestionado a través de middleware ZeroC Ice (v3.7.10).
   * **Garantías:** Tipado estricto mediante contratos Slice (`ChatSystem.ice`), entrega garantizada y en orden, despacho asíncrono mediante proxies de callback (`ChatCallbackPrx`).
   * **Funciones:** Autenticación, monitor de presencia, mensajería privada 1 a 1, mensajería grupal y transferencia binaria fragmentada (*chunking* de 32 KB).

2. **Canal de Transmisión de Audio en Tiempo Real (UDP):**
   * **Protocolo:** Datagramas UDP directos (`DatagramSocket` / `DatagramPacket`).
   * **Garantías:** Mínima latencia, sin sobrecargas de retransmisión por pérdida de paquetes ni retrasos acumulativos por control de congestión de TCP.
   * **Arquitectura de Audio Grupal:** **Relay Centralizado (SFU Ligero)**. El servidor UDP (`UdpAudioRelayServer` en puerto 10001) recibe tramas de audio PCM a 16 kHz (16-bit mono) empaquetadas en datagramas de hasta 1200 bytes y las retransmite selectivamente a los demás miembros registrados en la llamada.

---

## 3. Estructura del Proyecto y Despliegue

El proyecto está organizado como una solución multimodular de **Gradle** con el plugin oficial de ZeroC Ice:

```
taller_chat_ice/
├── build.gradle              # Configuración raíz y tareas runServer / runClient
├── settings.gradle           # Declaración de módulos (:slice, :server, :client)
├── slice/                    # Contratos de interfaz Slice (.ice) y stubs compilados
│   └── src/main/slice/ChatSystem.ice
├── server/                   # Lógica del servidor Ice y servidor UDP SFU
│   └── src/main/java/com/chat/server/
│       ├── ServerMain.java
│       ├── SessionManager.java
│       ├── RoomManager.java
│       ├── AudioCallManager.java
│       ├── audio/UdpAudioRelayServer.java
│       └── servants/ChatServiceI.java
└── client/                   # Cliente interactivo CLI, callbacks y motor de audio
    └── src/main/java/com/chat/client/
        ├── ClientMain.java
        ├── callbacks/ChatCallbackI.java
        ├── audio/
        │   ├── AudioRecorder.java
        │   ├── AudioPlayer.java
        │   └── UdpAudioClient.java
        └── util/FileTransferUtil.java
```

### Requisitos Previos
* **Java Development Kit (JDK):** Versión 17 o superior.
* Conexión a red local o `localhost` para pruebas.

### Instrucciones de Compilación y Ejecución

1. **Compilar todos los módulos:**
   ```bash
   ./gradlew compileJava
   ```
   *(En Windows usar `gradlew.bat compileJava`)*

2. **Ejecutar el Servidor:**
   ```bash
   ./gradlew runServer
   ```
   *El servidor inicializará el endpoint Ice en `tcp -h 127.0.0.1 -p 10000` y el relay UDP de voz en el puerto `10001`.*

3. **Ejecutar uno o más Clientes (en terminales separadas):**
   ```bash
   ./gradlew runClient
   ```

---

## 4. Manual de Usuario (CLI)

Al iniciar el cliente, el sistema solicitará el nombre de usuario (*nickname*). Si el nickname no está en uso, el cliente registrará su callback y presentará el menú interactivo:

| Comando | Sintaxis | Descripción |
| :--- | :--- | :--- |
| **Ayuda** | `/help` | Muestra el catálogo de comandos disponibles. |
| **Mensaje Privado** | `/msg <usuario> <mensaje>` | Envía un mensaje de texto 1 a 1 a otro usuario conectado. |
| **Crear Grupo** | `/create <grupo>` | Crea una nueva sala de chat con nombre único. |
| **Unirse a Grupo** | `/join <grupo>` | Se suscribe a los eventos y mensajes de una sala existente. |
| **Salir de Grupo** | `/leave <grupo>` | Abandona una sala de chat. |
| **Mensaje Grupal** | `/group <grupo> <mensaje>` | Envía un mensaje a todos los miembros suscritos a la sala. |
| **Enviar Archivo** | `/sendfile <usuario> <ruta>` | Envía un archivo a un usuario privado vía Ice (fragmentado en 32 KB). |
| **Enviar Archivo a Grupo** | `/sendfile -g <grupo> <ruta>` | Difunde un archivo a todos los miembros de una sala. |
| **Llamada 1 a 1: Llamar** | `/call <usuario>` | Solicita y timbra una llamada de voz 1 a 1 vía Ice al usuario destino. |
| **Llamada 1 a 1: Aceptar** | `/call-accept [usuario]` | Acepta la llamada entrante y activa el streaming de audio UDP bidireccional. |
| **Llamada 1 a 1: Rechazar**| `/call-reject [usuario]` | Rechaza la llamada entrante notificando al emisor vía Ice. |
| **Colgar Llamada** | `/hangup` | Cuelga la llamada 1 a 1 activa, cancela el timbrado o sale de la llamada grupal. |
| **Iniciar Llamada Grupal** | `/call-start <grupo>` | Inicia una llamada de voz en una sala e ingresa automáticamente. |
| **Unirse a Llamada Grupal** | `/call-join <grupo>` | Se une a una llamada de voz ya activa en la sala indicada. |
| **Salir de Llamada Grupal** | `/call-leave <grupo>` | Sale de la llamada de voz del grupo sin cerrarla para los demás. |
| **Silenciar Micrófono** | `/mute` | Pausa la captura y envío de audio local durante cualquier llamada (1 a 1 o grupal). |
| **Reactivar Micrófono** | `/unmute` | Reanuda la captura y transmisión de audio local en la llamada activa. |
| **Cerrar Sesión** | `/logout` *(o presionar Enter)* | Cierra sesión ordenadamente, liberando llamadas y callbacks. |

> **Flujo de Señalización de Llamadas 1 a 1 (RF-05):**  
> 1. El usuario $A$ escribe `/call B`. El servidor ZeroC Ice valida que ninguno esté en otra llamada y envía un evento asíncrono `notifyIncomingCall(A)` al callback de $B$.  
> 2. En la terminal de $B$ aparece una alerta interactiva con las opciones de contestar o rechazar.  
> 3. Si $B$ ejecuta `/call-accept`, el servidor registra la sesión de audio activa y notifica a $A$ mediante `notifyCallAccepted(B)`. Ambos clientes inician sus grabadoras PCM y transmiten datagramas UDP a través del relay.  
> 4. Si $B$ ejecuta `/call-reject`, el servidor notifica a $A$ con `notifyCallRejected` y libera la solicitud.  
> 5. Cualquiera de los dos participantes puede finalizar la llamada en cualquier instante con `/hangup`, lo cual notifica al interlocutor mediante `notifyCallEnded` y detiene de forma segura el hardware y sockets de audio.

> **Nota sobre archivos recibidos:** Los archivos transferidos mediante `/sendfile` se ensamblan automáticamente a partir de sus fragmentos y se guardan en la carpeta local `downloads/`.

---

## 5. Cuestionario de Análisis Conceptual y Decisiones de Diseño

### 5.1. Arquitectura Híbrida TCP/UDP y Consecuencias Acústicas
* **Razones técnicas fundamentales:**  
  La arquitectura híbrida responde a las diferencias críticas en los requerimientos de calidad de servicio (QoS) de cada componente:
  * El canal de señalización, gestión de usuarios, mensajería de texto y transferencia de archivos exige **entrega fiable, libre de errores y estrictamente ordenada**. Perder un mensaje de chat, corromper un archivo binario o desfasar una notificación de presencia desestabiliza el estado global de la aplicación. ZeroC Ice sobre TCP garantiza estas propiedades mediante confirmaciones (ACKs), sumas de verificación y retransmisiones transparentes.
  * Por el contrario, la voz en tiempo real es una señal continua altamente sensible a la **latencia y al jitter (variación del retardo)**, pero tolerante a una tasa marginal de pérdida de tramas (~1% al 3%). Utilizar datagramas **UDP** elimina los costos de establecimiento de conexión, la sobrecarga de encabezados y, crucialmente, los mecanismos de retransmisión y control de congestión de TCP.
* **Consecuencias de transmitir voz sobre Ice / TCP:**  
  Si la voz se transmitiera sobre Ice/TCP, cualquier pérdida de paquetes dispararía el algoritmo de *retransmisión automática y control de congestión* (bloqueo Head-of-Line). Esto impediría que los paquetes posteriores sean entregados al receptor hasta que el paquete perdido sea retransmitido con éxito. Acústicamente, esto produce **retrasos crecientes acumulativos, cortes intermitentes en la conversación (dropouts) y desincronización severa**, haciendo inviable una conversación bidireccional fluida.

---

### 5.2. Manejo de Buffer y Chunking en ZeroC Ice
* **Comportamiento por defecto ante archivos grandes:**  
  ZeroC Ice protege la memoria del servidor y los clientes mediante la propiedad de configuración `Ice.MessageSizeMax`, cuyo valor por defecto suele ser **1024 KB (1 MB)**. Si un cliente intenta enviar un archivo grande (por ejemplo, 80 MB) en una sola llamada RPC pasando la totalidad de bytes en una secuencia Slice (`sequence<byte>`), el runtime de Ice intercepta el tamaño del mensaje serializado y arroja inmediatamente una excepción `com.zeroc.Ice.MemoryLimitException`, abortando la operación.
* **Solución mediante Fragmentación (*Chunking*):**  
  La técnica de fragmentación divide el archivo original en bloques secuenciales de tamaño controlado (en nuestra implementación, bloques de **32 KB**). Cada bloque es transmitido en una invocación RPC independiente mediante la estructura `FileChunk`:
  ```slice
  struct FileChunk {
      string fileId;
      string filename;
      long totalSize;
      int chunkIndex;
      int totalChunks;
      ByteSeq data;
      string target;
      bool isGroup;
  };
  ```
  Esto garantiza que ningún paquete RPC supere el umbral de `Ice.MessageSizeMax`. El receptor almacena los bloques indexados en memoria concurrente (`pendingFiles`) y, una vez recibidos todos los fragmentos (`chunksMap.size() == totalChunks`), recompone el flujo binario original de manera ordenada y libre de corrupción en el sistema de archivos.

---

### 5.3. Escalabilidad en Llamadas Grupales (Relay SFU vs. Malla P2P / Mesh)
En una sala con $N$ participantes activos hablando simultáneamente:

| Métrica | Relay Centralizado (SFU Ligero) | Malla Peer-to-Peer (Mesh) |
| :--- | :--- | :--- |
| **Paquetes UDP emitidos por cada cliente** | **1 paquete** hacia el servidor | **$N - 1$ paquetes** (una copia por cada par) |
| **Paquetes recibidos por cada cliente** | $N - 1$ paquetes | $N - 1$ paquetes |
| **Carga total de red en el Servidor (entrada)** | $N$ paquetes | **0 paquetes** (el servidor no procesa audio) |
| **Carga total de red en el Servidor (salida)** | **$N \times (N - 1)$ paquetes** | **0 paquetes** |
| **Carga total de tráfico en el Servidor** | **$N^2$ paquetes aproximadamente** | $0$ |
| **Consumo de ancho de banda del cliente** | Mínimo y constante ($O(1)$ subida) | Crece linealmente con cada participante ($O(N)$ subida) |

* **Justificación de la decisión del equipo (SFU Ligero):**  
  Se seleccionó la arquitectura de **Relay Centralizado (SFU)** debido a que en entornos residenciales o redes universitarias, el ancho de banda de subida (*upload*) de los clientes suele ser asimétrico y restringido, y las conexiones suelen estar detrás de mecanismos NAT restrictivos. Con una topología Mesh, a medida que la sala supera 4 o 5 participantes, el enlace de subida del cliente colapsa. El Relay centralizado alivia a los clientes delegando la duplicación al servidor y permite aplicar validaciones estrictas de autorización de pertenencia a salas.

---

### 5.4. Transparencia y Callbacks en Ice: Ciclo de Vida y Adaptadores
* **Ciclo de vida y configuración de adaptadores en el cliente:**  
  1. Para que el servidor pueda invocar métodos de retorno de forma asíncrona sobre el cliente, este último debe actuar simultáneamente como servidor Ice local.
  2. El cliente inicializa su `Communicator` y crea un `ObjectAdapter` local (por ejemplo `CallbackAdapter`) mediante `communicator.createObjectAdapter("CallbackAdapter")` o `createObjectAdapterWithEndpoints("", "tcp")`.
  3. El cliente instancia su *servant* local (`ChatCallbackI`) y lo registra en el adaptador utilizando `adapter.addWithUUID(callbackServant)`.
  4. El adaptador es activado (`adapter.activate()`), permitiendo que el hilo despachador de Ice escuche invocaciones entrantes.
  5. El cliente convierte la identidad del servant en un proxy (`ChatCallbackPrx`) y lo envía como parámetro en la operación `login(nickname, callbackProxy)` del servidor.
* **Papel de Proxies Directos vs. Indirectos:**  
  * **Proxies Directos:** Contienen explícitamente el protocolo, dirección IP y puerto del endpoint de destino (ej. `ChatCallback:tcp -h 192.168.1.50 -p 54321`). En el flujo de callbacks entre cliente y servidor, el callback registrado suele ser un proxy directo para que el servidor pueda contactar al cliente sin intermediación de un servicio de localización.
  * **Proxies Indirectos:** Omiten los endpoints físicos y contienen una identidad lógica o nombre de réplica (ej. `ChatService@ChatAdapter` o `ChatService`), requiriendo que un servicio de localización (`IceGrid`) resuelva dinámicamente el host y puerto en tiempo de ejecución. Permiten alta disponibilidad y balanceo de carga en arquitecturas empresariales.

---

## 6. Bitácora de Uso Ético de IAG (Nivel 3 - Colaboración Asistida)

Conforme a las directrices institucionales de la Universidad Icesi para actividades de Nivel 3:

| Componente | Prompts Clave Empleados | Código Generado y Adaptación Crítica | Lecciones Aprendidas y Depuración |
| :--- | :--- | :--- | :--- |
| **Contrato Slice (.ice)** | *"Estructura un contrato Slice para un chat distribuido que contemple mensajería privada, salas y metadatos de chunking para archivos binarios."* | El modelo propuso inicialmente clases para los metadatos; se adaptó a un `struct FileChunk` y tipos primitivos de Slice para evitar sobrecarga de serialización de objetos y garantizar interoperabilidad con stubs de Java. | Comprender la diferencia entre `struct` y `class` en Slice, y cómo `sequence<byte>` se mapea limpiamente a arreglos `byte[]` en Java. |
| **Transferencia de Archivos (Chunking)** | *"¿Cómo implementar lectura de bloques de 32 KB en Java y reconstrucción concurrente sin corrupción?"* | Se tomó la lógica base de lectura con `FileInputStream` y se adaptó un gestor de ensamble con `ConcurrentHashMap<String, Map<Integer, byte[]>>` que garantiza orden en la escritura a disco y elimina archivos temporales incompletos. | El orden de llegada de los chunks sobre TCP está garantizado a nivel de flujo, pero estructurar un mapa indexado previene condiciones de carrera ante envíos paralelos. |
| **Audio UDP y Mute** | *"Crea un relay UDP en Java que reciba paquetes de audio PCM y los redistribuya a una lista de participantes concurrentes."* | Se adaptó la serialización binaria con `DataOutputStream` / `DataInputStream` para añadir cabeceras de tipificación de paquetes (`GROUP_REGISTER`, `GROUP_AUDIO`, etc.) y validación de membresía en salas antes de retransmitir. | En UDP no hay control de flujo automático; mantener datagramas por debajo del MTU (máximo 1200 bytes de carga útil) evita fragmentación a nivel IP. |
| **Thread Safety** | *"Revisa la concurrencia de este SessionManager en Ice."* | Se reemplazaron colecciones estándar por `ConcurrentHashMap` y `ConcurrentHashMap.newKeySet()`, implementando captura de `LocalException` para depurar clientes caídos abruptamente. | ZeroC Ice utiliza un pool de hilos para despachar peticiones concurrentes, por lo que cualquier acceso al estado compartido sin colecciones concurrentes produce `ConcurrentModificationException`. |
