**TALLER EVALUATIVO UNIDAD 2: SISTEMAS DISTRIBUIDOS, MIDDLEWARE Y COMUNICACIÓN MULTIMEDIA´**

# **Computación en Internet I**

**FACULTAD DE INGENIERIA, DISENO Y CIENCIAS APLICADAS˜	DEPARTAMENTO DE COMPUTACIÓN Y SISTEMAS´**

## **INTELIGENTES**

| 09810 \- TIC CODIGO:´ | 202620 (Semestre 2026-2) PERIODO: |
| ----- | ----- |
| 12378 / 12377 (Grupos 001 y 003\) **NRC / GRUPO:** | 8.0 % (Taller 2 – Unidad 2\) **PONDERACION:´** |
| Grupos de 3 o 4 Estudiantes **MODALIDAD:** |  / 5.0 **CALIFICACION:´** |
| Nivel 3: Colaboracion Asistida´ **POL´ITICA IAG:** | Taller Evaluativo / Proyecto Distribuido **TIPO ACTIVIDAD:** |
| Luis Miguel Delgado **ESTUDIANTE 1:** | A00410889 **CODIGO:´** |
| Miguel Angel Blanco **ESTUDIANTE 2:** | A00410773 **CODIGO:´** |
| **1** |  |

## **Contexto del Problema y Desaf´ıo de Ingenier´ıa**

En las sesiones previas del curso exploramos la comunicación entre procesos mediante sockets crudos (TCP y UDP). A través de dicha experiencia identificamos las dificultades de construir sistemas a gran escala bajo ese paradigma: la serialización artesanal de bytes, el acoplamiento rígido de mensajes y la complejidad de coordinar múltiples clientes concurrentes. Posteriormente,´ introdujimos el concepto de **Middleware orientado a objetos y Llamadas a Procedimiento Remoto (RPC)** a traves de´ **ZeroC Ice (Internet Communications Engine)**, comprendiendo como los contratos en lenguaje´ **Slice** permite definir interfaces fuertemente tipadas y transparentes a la ubicación f´ ´ısica de los nodos.

En este taller evaluativo, cada equipo de ingenier´ıa asumirá el desafío´ ´ıo de **diseñar y construir una plataforma distribuida de˜ comunicación en tiempo real y colaboraci´ on multimedia´** (en una arquitectura similar a Discord, Slack o Telegram). El sistema combina estratégicamente dos mundos de comunicaci´	on en red:´

1. **Canal de Señalización, Mensajería´ıa y Transferencia de Archivos:´** Gestionado a través del middleware´ **ZeroC Ice**, garantizando tipado estricto, despacho as´ıncrono y fiabilidad sobre TCP.

2. **Canal de Transmisión de Voz en Tiempo Real:´** Gestionado directamente sobre datagramas **UDP**, priorizando la baja latencia y la fluidez acustica sin incurrir en la sobrecarga de retransmisión y confirmación de TCP.´

**Nota Pedagógica Crucial:´** A diferencia de los talleres introductorios guiados, este documento **no proporciona código de solución ni guía paso a paso´** . Cada equipo es autónomo en la formulación de sus contratos Slice (´ .ice), la arquitectura interna de sus servidores y clientes, el manejo de hilos y la elección de la interfaz de usuario (´ **CLI interactiva o JavaFX**).

| 2 |
| :---- |

## **Requerimientos Funcionales del Sistema**

La solución desarrollada por el equipo debe cumplir estrictamente con los siguientes requerimientos funcionales:´

## **2.1 RF-01: Gestión de Sesión, Presencia y Registro de Clientes´**

 **Autenticación e Identificación:Cada** cliente debe conectarse al servidor ZeroC Ice indicando un identificador de usuario (*nickname*) único. Si el identificador ya se encuentra en uso por otra sesi´	on activa, el servidor debe rechazar la conexión´ arrojando una excepción personalizada de Slice.´

 **Registro de Retorno (Callbacks):** Para permitir que el servidor notifica eventos al cliente de forma más´ıncrona (mensajes entrantes, llamadas, archivos), el cliente debe registrar un proxy de retorno (*callback object*) en el servidor al momento de iniciar sesión.´

**Monitor de Presencia en Tiempo Real:** Todos los clientes conectados deben recibir notificaciones automáticas inmediatas cuando un usuario se conecta o se desconecta de la plataforma.

**Desconexión Limpia y Tolerancia a Fallos:´** Debe implementarse un mecanismo de cierre de sesión ordenado (´ *logout*) que limpie los recursos y proxies en el servidor, as´ı como detección y remoción de clientes cuya conexión se pierda abruptamente.´

## **2.2 RF-02: Mensajería Privada Directa (1 a 1\)**

**Comunicación Punto a Punto:´**	Un usuario conectado debe poder enviar un mensaje de texto privado a cualquier otro usuario disponible en la red indicando su *nickname*.

**Despacho As´ıncrono:** El mensaje debe enviarse al servidor Ice, el cual localizara el proxy de callback del destinatario y´ despacha el mensaje en tiempo real.´

**Confirmación y Control de Errores:´**	El remitente debe recibir confirmación de que el mensaje fue entregado al servidor, o un mensaje de error descriptivo en caso de que el destinatario no exista o se haya desconectado.

## **2.3 RF-03: Creación y Gestión de Grupos / Salas de Chat (Canales)´**

**Creación Dinámica de Salas:´** Cualquier usuario debe tener la capacidad de crear una nueva sala o grupo de chat asignandole´ un nombre o identificador único.´

**Afiliacion y Listado:´** Los usuarios pueden consultar el catálogo de salas disponibles, unirse a una o'múltiples salas simult´ aneamente y abandonarlas en cualquier instante.

**Difusión Grupal (Broadcast):Los** mensajes emitidos dentro de una sala deben propagarse a todos los participantes inscritos activamente en ella, identificando con claridad el remitente y la sala de origen, sin enviarse de vuelta como duplicado al emisor.

**Aislamiento de Tráfico:´**	Los mensajes pertenecientes a un grupo jamás deben filtrarse ni entregarse a usuarios que no pertenezcan a dicho grupo.

## **2.4 RF-04: Transferencia de Contenido Multimedia sobre ZeroC Ice**

**Tipos de Contenido Soportados:** Transferencia de archivos binarios tales como imágenes (´ .png, .jpg), notas de voz (.wav, .mp3) o documentos (.pdf, .txt).

**Transferencia V´ıa Contrato Slice:** El envío´ıo y recepción de archivos multimedia debe realizarse´ **estrictamente a través de´ interfaces y operaciones de ZeroC Ice**, empleando secuencias binarias de Slice (sequence\<byte\>).

**Estrategia de Fragmentación (´ *Chunking*):** Dado que Ice posee un l´ımite de tamaño por mensaje configurado (Ice.MessageSizeMax), el sistema debe fragmentar los archivos en bloques o *chunks* sucesivos (ej. paquetes de 32 KB a 64 KB) con metadatos asociados (nombre de archivo, tamaño total, índice de bloque y total de fragmentos).

**Destino Directo y Grupal:** Los archivos deben poder enviarse tanto a un usuario en chat privado como a una sala grupal completa.

**Reconstrucción e Integridad:´**	El cliente receptor debe reconstruir el archivo en disco o carpeta temporal garantizando que no sufra corrupción binaria ni truncamiento.´

## **2.5 RF-05: Llamadas de Voz Directas (1 a 1\) sobre Datagramas UDP**

###  **Señalización y Negociación via Ice:´**

* El usuario iniciador invoca una operación RPC en el servidor Ice solicitando iniciar una llamada de voz con el usuario destino,´ publicando su dirección IP y puerto UDP de recepción de audio.´

* El usuario destino recibe una notificación de llamada entrante y puede´	**Aceptar** o **Rechazar** la llamada vía Ice.

* Al aceptar, el destino reporta su IP y puerto UDP asignado, quedando formalmente establecida la sesión de voz.

 **Flujo de Audio UDP:**

* Captura de audio en tiempo real desde el microfono del usuario emisor (empleando la API estándar javax.sound.sampled de Java).

* Empaquetamiento de muestras de audio en datagramas UDP (DatagramPacket) y transmision continua al endpoint UDP del´ receptor sin intermediacion de retransmisiones TCP.´

* Recepcion de datagramas UDP en un hilo dedicado en el cliente y reproducci´	on inmediata en los altavoces.´

 **Finalizacion de Llamada:´** Cualquiera de los dos usuarios puede colgar la llamada mediante una senal en Ice, lo cual cerrar˜ a´ de forma segura los sockets UDP y liberara las l´ ´ıneas de audio.

## **2.6 RF-06: Llamadas de Voz Grupales (Conferencias de Audio) sobre UDP**

**Inicio y Vinculación a Conferencia:** En cualquier sala de chat grupal activa, los usuarios deben poder iniciar o sumarse a una llamada grupal de voz.

**Arquitectura de Distribución de Audio Grupal:** El equipo debe diseñar e implementar una de las siguientes dos estrategias

(documentando la decisión en el informe):´

1) **Relay Centralizado de Audio (SFU Ligero):** El servidor dispone de un socket UDP que recibe los paquetes de voz de cada emisor y los reenvía a todos los demás participantes de la sala de audio.

2) **Malla Peer-to-Peer (Mesh):** A través de la señalización de Ice, cada participante obtiene la lista de IPs y puertos UDP de los demás integrantes de la llamada y transmite réplicas de sus datagramas directamente a cada uno.

 **Control de Audio:** El cliente debe permitir silenciar el micrófono (´	*Mute / Unmute*) en cualquier momento y abandonar la conferencia sin interrumpir la llamada de los demás participantes.´

| 3 |
| :---- |

### **Requerimientos No Funcionales y Restricciones de Diseno˜**

1. **Middleware Obligatorio:** Toda la lógica de control, catálogos, se´ nalización, mensajería y transferencia de archivos debe residir sobre **ZeroC Ice (versión 3.7 o superior)´** en lenguaje Java.

2. **Canal de Audio Estrictamente UDP:** El streaming de voz debe utilizar exclusivamente sockets de datagramas UDP (java.net.DatagramSoc Queda prohibido transmitir el flujo continuo de audio sobre llamadas s´ıncronas de Ice o conexiones TCP.

#### 3\. **Interfaz de Usuario (A Elección del Equipo):´**	

	 **Opción A (CLI Avanzada):**Interfaz por consola con comandos interactivos limpios (ej. /login, /msg \<user\>\<txt\>, /create

\<room\>, /join \<room\>, /sendfile \<target\>\<path\>, /call \<target\>, /hangup, /mute). Requiere un hilo de lectura de entrada de usuario desacoplado de la impresión de mensajes as´ ´ıncronos.

**Opción B (JavaFX):** Interfaz gráfica de escritorio con paneles para lista de usuarios, salas, ventana de chat, selector de archivos y controles de llamada (botones de llamar, contestar, colgar y mute).

**Nota de Evaluación:Ambas** opciones se evalúan bajo el mismo estándar de calidad. Una CLI robusta, bien formateada y´ sin bloqueos de concurrencia tiene exactamente la misma ponderación que una interfaz JavaFX.´

4. **Concurrencia Segura en el Servidor (Thread-Safety):** El servidor Ice es intr´ınsecamente multihilo; las colecciones que almacenan el estado global (mapa de usuarios conectados, proxies de clientes, salas activas, participantes en llamadas) deben estar protegidas rigurosamente contra condiciones de carrera mediante sincronización explícita o colecciones concurrentes (ConcurrentHashMap, CopyOnWriteArrayList).

5. **Gestión del Proyecto con Gradle:** La solución debe organizarse como un proyecto multimodular en **Gradle** estructurado con el plugin oficial de Ice (com.zeroc.gradle.ice-builder.slice) y scripts de ejecución definidos en el archivo´ build.gradle para servidor y cliente.

| 4 |
| :---- |

### 	**Cuestionario de Análisis Conceptual y Decisiones de Diseño**

Como parte del informe técnico de entrega, el equipo debe responder con profundidad técnica y justificación de ingenier´ ´ıa a las siguientes interrogantes:

1. **Arquitectura H´ıbrida TCP/UDP:** ¿Cuales son las razones técnicas fundamentales para diseñar una arquitectura híbrida donde el control, el chat de texto y la transferencia de archivos corren sobre ZeroC Ice (TCP), mientras que el flujo de voz corre directamente sobre UDP? ¿Qué consecuencias acústicas y de rendimiento ocurrirán si la voz se transmite sobre Ice/TCP bajo condiciones de fluctuación de red (*jitter*) o pérdida de paquetes?´

1. Razones técnicas de arquitectura híbrida: El canal de control, mensajería y transferencia de archivos requiere fiabilidad estricta y orden en la entrega, lo cual se garantiza mediante TCP a través de ZeroC Ice,pero por otro lado la transmisión de voz en tiempo real prioriza la baja latencia y la fluidez, y hay más tolerancia frente a la pérdida de datos en las tramas de audio, así que conviene evitar la sobrecarga de retransmisión y confirmación propia de TCP, por lo que se gestiona directamente con datagramas UDP.  
2. Consecuencias de voz sobre TCP: Bajo condiciones que se pueden dar como fluctuación de red (jitter) o pérdida de paquetes, TCP bloqueará la entrega hasta retransmitir el segmento perdido, generando retrasos acumulados, interrupciones y pérdida de sincronía en el flujo de audio en tiempo real.  
2. **Manejo de Buffer y Chunking en Ice:** Si un usuario intenta transferir un video de 80 MB mediante una única llamada RPC pasando toda la secuencia de bytes en un parámetro sequence\<byte\>, ¿que excepción o comportamiento manifestar a ZeroC Ice por defecto? Explique cómo la técnica de fragmentación en bloques resuelve este problema y qué parámetros de configuración de Ice intervienen.

1. Excepción por defecto: Superar el límite de tamaño por mensaje configurado en Ice (Ice.MessageSizeMax) al enviar un archivo grande en una única llamada RPC genera una excepción de desborde de buffer (MemoryLimitException).  
2. Chunking: Consiste en dividir el archivo en bloques sucesivos (ej. de 32 KB a 64 KB) acompañados de metadatos (nombre de archivo, tamaño total, índice de bloque y total de fragmentos). Esto permite transmitir los datos de forma secuencial respetando los límites de tamaño de Ice y reconstruir el archivo íntegramente en el destino, evitando la excepción de desborde de buffer que puede afectar toda la operación.  
3. **Escalabilidad en Llamadas Grupales (Relay vs. Mesh):** Analice comparativamente la topología de Audio Relay centralizado frente a la topología Mesh Peer-to-Peer elegida por su equipo. Si una sala cuenta con *N* participantes activos hablando simultáneamente, ¿cuál es el número total de paquetes UDP que debe emitir cada cliente y cuál es la carga total que soporta la red del servidor en ambas alternativas?

La principal diferencia entre ambos sistemas en términos de efectividad y consumo de recursos es que en Audio Relay el principal encargado de gestionar la llamada es el servidor, quien recibe individualmente el flujo de datos de cada cliente y se encarga de devolverlo (broadcast) para que llegue a todos los demás, mientras que en peer to peer cada participante transmite réplicas de sus datagramas UDP directamente a cada uno de los demás integrantes de la llamada: Con N participantes, cada cliente emite N-1 flujos de paquetes simultáneos, mientras que el servidor no interviene en el reenvío del flujo de audio. Por el lado de la topología Mesh, el límite como decíamos radica en la capacidad de procesamiento del servidor, pues como cada cliente emite un flujo de paquetes hacia el servidor, la red del servidor soporta la carga total de distribución hacia todos los participantes.

4. **Transparencia y Callbacks en Ice:** Describa el ciclo de vida y la configuración de adaptadores de objetos requerida en el cliente para permitir que el servidor Ice invoque métodos de retorno (*callbacks*) sobre el. ¿Qué papel juegan los proxies directos vs indirectos en este flujo?

1. Ciclo de vida y adaptadores: El cliente configura y activa un adaptador de objetos local para recibir peticiones entrantes, implementa la interfaz de retorno y registra su proxy en el servidor al momento de iniciar sesión para permitir notificaciones asíncronas.  
2. Proxies directos vs. indirectos: Los proxies indirectos emplean un identificador lógico resuelto a través de un localizador, mientras que los proxies directos especifican la dirección de red exacta del endpoint del cliente, facilitando la comunicación en los callbacks.

| 5 |
| :---- |

### **Rúbrica de Evaluación Modularizada por Entregable (Escala 0.0 a 5.0)´**

La evaluacion del taller se realizar´ a de forma modular con base en la demostraci´ on funcional en vivo, el c´ odigo fuente y las´ respuestas técnicas del equipo durante la sustentaci´	on:´

| Criterio / Módulo´ | Pts | Excelente (4.5 – 5.0) | Aceptable (3.0 – 4.4) | Insuficiente (0.0 – 2.9) |
| :---- | ----- | ----- | :---- | :---- |
| **1\. Contratos Slice y** **Arquitectura** **Distribuida** | **0.7** | Diseno formal y modular del archivo˜	.ice. Define limpiamente interfaces para servidor y callbacks, structs estructurados y excepciones personalizadas. Proyecto Gradle multimodulo limpio con tareas de ejecu-´ cion independientes.´ | Contrato Slice funcional pero con definiciones monol´ıticas, tipos genericos o escaso´ manejo de excepciones. Proyecto Gradle con dependencias r´ıgidas o errores menores de compilacion.´ | Contrato Slice inexistente, incompleto o con errores graves de sintaxis que impiden compilar los stubs en Java. Arquitectura no modular. |
| **2\. Sesion, Presencia´ y Chat Directo (1 a 1\)** | **0.8** | Registro con nickname unico, detecci´ on´ de duplicados y monitor de presencia en tiempo real reactivo ante conexiones/desconexiones. Mensajer´ıa privada 1 a 1 fluida, as´ıncrona y con confirmaciones claras sin bloqueos. | Inicio de sesion y chat directo funcionan,´ pero la lista de presencia presenta retrasos, no notifica desconexiones o requiere refresco manual. | Falla el registro de usuarios, se permiten nombres duplicados o la mensajer´ıa privada 1 a 1 no entrega los mensajes al destinatario. |
| **3\. Salas Grupales y Difusion (Broadcast)´** | **0.8** | Creacion, listado y vinculaci´ on din´ amica a´ salas de chat. Propagacion instant´ anea de´ mensajes a todos los miembros de la sala sin duplicados al emisor y con estricto aislamiento entre grupos distintos. | Las salas de chat funcionan pero presentan fallas al salir usuarios, mensajes duplicados esporadicos o retrasos en la difu-´ sion concurrente.´ | Los mensajes se mezclan entre diferentes salas de chat, el broadcast falla ante multi-´ ples usuarios o la creacion de grupos co-´ lapsa el servidor. |
| **4\. Transferencia** **Multimedia sobre Ice** **(Chunking)** | **0.9** | Env´ıo de imagenes/archivos´ binarios a traves de Ice utilizando secuencias de by-´ tes y estrategia de fragmentacion (´ *chunking*). Reconstruccion´ ´ıntegra y almacenamiento en el receptor tanto en chat directo como en salas. | Transfiere archivos pequenos˜ correctamente, pero falla con archivos de mediano tamano˜ por desborde de buffer (MemoryLimitException) o faltan metadatos. | No implementa transferencia de archivos sobre Ice, utiliza sockets TCP alternos no autorizados o los archivos recibidos resultan corruptos. |
| **5\. Llamadas de Voz** **UDP (Directas y** **Grupales)** | **1.2** | Senalizaci˜ on completa en Ice (solicitar,´ timbrar, aceptar, rechazar y finalizar llamada). Transmision continua de voz sobre da-´ tagramas UDP con baja latencia y audio inteligible. Soporta llamada 1 a 1 y conferencia grupal con mute funcional. | Llamada directa 1 a 1 operativa sobre UDP, pero la llamada grupal presenta eco severo, bloqueos al ingresar mas de 2 par-´ ticipantes o fallas en la finalizacion.´ | No realiza senalizaci˜ on de llamadas en´ Ice, no hay transmision funcional de audio´ sobre UDP o utiliza protocolos s´ıncronos inadecuados. |
| **6\. Concurrencia,** **Calidad de UI y** **Sustentacion´** | **0.6** | Servidor plenamente thread-safe sin condiciones de carrera. Interfaz (CLI o JavaFX) altamente reactiva, intuitiva y profesional. README detallado. Sustentacion´ oral solida con dominio t´ ecnico por todos´ los integrantes. | Servidor funcional pero con bloqueos ocasionales por sincronizacion;´ UI basica´ con formato desordenado. Dominio tecni-´ co desigual entre los miembros del equipo. | Servidor colapsa por concurrencia no controlada. Interfaz inusable. Documentacion´ deficiente y desconocimiento del codigo´ en la sustentacion.´ |
| **CALIFICACION´** **TOTAL** | **5.0** | **Escala Institucional Universidad Icesi: Aprobatoria** ≥ **3.0** • **NRC: 12378 / 12377** |  |  |

####  **INSTRUCCIONES DE ENTREGA Y METODOLOGIA	MODALIDAD: EQUIPOS DE 3 O 4**

1. **Conformación de Equipos:´** La actividad se desarrollará en grupos conformados estrictamente por´ **3 o 4 estudiantes**. Cada integrante debe participar activamente en el diseño arquitectónico y la codificación.

2. **Repositorio de Código (Git):** El código fuente completo debe alojarse en un repositorio privado (GitHub o GitLab) con permisos de lectura para el profesor. Debe incluir el historial claro de commits de todos los integrantes.

3. **Estructura del Proyecto:** El repositorio debe contener el archivo build.gradle y los scripts wrapper (gradlew y gradlew.bat) para que cualquier evaluador pueda clonar y ejecutar el servidor y los clientes mediante:

./gradlew runServer

./gradlew runClient

4. **Documentation (´** README.md**):** El repositorio debe incluir un manual técnico en formato Markdown con:

	Diagrama de arquitectura del sistema indicando el flujo de Ice (señalización/mensajes/archivos) y el flujo UDP (voz).´

Manual de usuario detallando los comandos (si es CLI) o la gu´ıa de uso (si es JavaFX).

		Respuestas formales al Cuestionario de Analisis Conceptual (Secci´	on 4 de este documento).´

5. **Bitácora de Uso Etico de IAG (Nivel 3 \- Colaboración Asistida):´** De acuerdo con el programa oficial del curso, se permite el uso asistido de Inteligencia Artificial Generativa. Es obligatorio incluir en el informe una seccion de bit´ acora que resuma los prompts principales empleados, el c´ odigo generado que fue adaptado´ cr´ıticamente y las lecciones aprendidas durante la depuracion.´

6. **Sustentación y Demostración Práctica:´** Cada equipo realizará una sustentación en vivo frente al docente en el laboratorio, conectando múltiples clientes simultáneos desde diferentes máquinas de la red local para verificar la concurrencia, el intercambio multimedia y la calidad acustica de las llamadas UDP.´