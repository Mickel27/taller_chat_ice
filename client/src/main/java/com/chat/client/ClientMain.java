package com.chat.client;

import java.util.Scanner;

import com.chat.client.audio.AudioRecorder;
import com.chat.client.audio.UdpAudioClient;
import com.chat.client.callbacks.ChatCallbackI;
import com.chat.client.util.FileTransferUtil;
import com.chat.slice.ChatCallbackPrx;
import com.chat.slice.ChatServicePrx;
import com.chat.slice.AudioCallAlreadyActiveException;
import com.chat.slice.AudioCallNotFoundException;
import com.chat.slice.RoomAlreadyExistsException;
import com.chat.slice.RoomMembershipException;
import com.chat.slice.RoomNotFoundException;
import com.chat.slice.UserAlreadyExistsException;
import com.chat.slice.UserNotFoundException;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.ObjectPrx;
import com.zeroc.Ice.Util;

public class ClientMain {

    public static void main(String[] args) {
        try (Communicator communicator = Util.initialize(args, "config.client")) {
            
            ObjectPrx base = communicator.propertyToProxy("ChatService.Proxy");
            ChatServicePrx server = ChatServicePrx.checkedCast(base);

            if (server == null) {
                System.err.println("Error: No se pudo obtener el proxy del servidor.");
                return;
            }

            ObjectAdapter adapter = communicator.createObjectAdapter("CallbackAdapter");
            ChatCallbackI callbackServant = new ChatCallbackI();
            
            ObjectPrx cbPrxBase = adapter.addWithUUID(callbackServant);
            adapter.activate();
            ChatCallbackPrx callbackProxy = ChatCallbackPrx.uncheckedCast(cbPrxBase);

            Scanner scanner = new Scanner(System.in);
            System.out.print("Ingrese su nickname: ");
            String nickname = scanner.nextLine().trim();

            if (nickname.isEmpty()) {
                System.err.println("El nickname no puede estar vacío.");
                return;
            }

            // Inicializar utilidades UDP y Grabadora de Audio
            UdpAudioClient udpAudioClient = new UdpAudioClient("localhost", 10001);
            AudioRecorder audioRecorder = new AudioRecorder();
            // Control de actividad de llamadas (grupal y 1 a 1)
            String[] activeAudioRoom = { null };
            String[] activePrivateCall = { null };
            String[] pendingIncomingCaller = { null };
            String[] pendingOutgoingTarget = { null };

            // Registrar listener de eventos de llamada en el callback servant
            callbackServant.setCallEventListener(new ChatCallbackI.CallEventListener() {
                @Override
                public void onIncomingCall(String callerNickname) {
                    pendingIncomingCaller[0] = callerNickname;
                }

                @Override
                public void onCallAccepted(String calleeNickname) {
                    pendingOutgoingTarget[0] = null;
                    activePrivateCall[0] = calleeNickname;
                    try {
                        udpAudioClient.startPrivateTransmission(calleeNickname);
                        audioRecorder.startRecording(udpAudioClient::sendPrivateAudioChunk);
                    } catch (Exception e) {
                        System.err.println("[ERROR]: Error al activar audio privado: " + e.getMessage());
                    }
                }

                @Override
                public void onCallRejected(String calleeNickname, String reason) {
                    pendingOutgoingTarget[0] = null;
                }

                @Override
                public void onCallEnded(String partnerNickname) {
                    if (audioRecorder.isRecording()) {
                        audioRecorder.stopRecording();
                    }
                    udpAudioClient.stopPrivateTransmission();
                    activePrivateCall[0] = null;
                    if (pendingIncomingCaller[0] != null && pendingIncomingCaller[0].equals(partnerNickname)) {
                        pendingIncomingCaller[0] = null;
                    }
                    if (pendingOutgoingTarget[0] != null && pendingOutgoingTarget[0].equals(partnerNickname)) {
                        pendingOutgoingTarget[0] = null;
                    }
                }
            });

            try {
                server.login(nickname, callbackProxy);
                udpAudioClient.startPrivateEndpoint(nickname);
                System.out.println(">>> Sesión iniciada correctamente como: " + nickname);
                try {
                    String[] onlineUsers = server.listUsers();
                    System.out.println(">>> Usuarios conectados: " + String.join(", ", onlineUsers));
                } catch (Exception ignored) {}
                help();

                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        if (audioRecorder.isRecording()) {
                            audioRecorder.stopRecording();
                        }
                        if (activePrivateCall[0] != null) {
                            try {
                                server.hangupPrivateCall(nickname, activePrivateCall[0]);
                            } catch (Exception ignored) {}
                        }
                        if (pendingOutgoingTarget[0] != null) {
                            try {
                                server.hangupPrivateCall(nickname, pendingOutgoingTarget[0]);
                            } catch (Exception ignored) {}
                        }
                        if (activeAudioRoom[0] != null) {
                            try {
                                server.leaveAudioCall(activeAudioRoom[0], nickname);
                            } catch (Exception ignored) {}
                        }
                        udpAudioClient.stop();
                        server.logout(nickname);
                    } catch (Exception ignored) {}
                }));

                while (true) {
                    System.out.print("> ");
                    String input = scanner.nextLine().trim();

                    if (input.equalsIgnoreCase("/logout") || input.isEmpty()) {
                        break;
                    }

                    if (input.equalsIgnoreCase("/help")) {
                        help();
                        continue;
                    }

                    // RF-01: Listar usuarios conectados
                    if (input.equalsIgnoreCase("/users") || input.equalsIgnoreCase("/list-users") || input.equalsIgnoreCase("/online")) {
                        String[] users = server.listUsers();
                        System.out.println("\n[USUARIOS CONECTADOS] (" + users.length + "):");
                        if (users.length == 0) {
                            System.out.println("  (No hay otros usuarios en línea)");
                        } else {
                            for (String u : users) {
                                System.out.println("  - " + u + (u.equals(nickname) ? " (tú)" : ""));
                            }
                        }
                    }
                    // RF-03: Listar catálogo de salas disponibles
                    else if (input.equalsIgnoreCase("/rooms") || input.equalsIgnoreCase("/list-rooms")) {
                        String[] rooms = server.listRooms();
                        System.out.println("\n[CATÁLOGO DE SALAS DISPONIBLES] (" + rooms.length + "):");
                        if (rooms.length == 0) {
                            System.out.println("  (No hay salas creadas aún. Crea una con /create <nombre>)");
                        } else {
                            for (String r : rooms) {
                                System.out.println("  - " + r);
                            }
                        }
                    }
                    // RF-03: Listar miembros de una sala
                    else if (input.startsWith("/members ") || input.startsWith("/room-members ")) {
                        String roomName = input.startsWith("/members ") ? input.substring(9).trim() : input.substring(14).trim();
                        if (roomName.isEmpty()) {
                            System.out.println("[SISTEMA]: Uso: /members <grupo>");
                            continue;
                        }
                        try {
                            String[] members = server.listRoomMembers(roomName);
                            System.out.println("\n[MIEMBROS DE '" + roomName + "'] (" + members.length + "):");
                            for (String m : members) {
                                System.out.println("  - " + m + (m.equals(nickname) ? " (tú)" : ""));
                            }
                        } catch (RoomNotFoundException e) {
                            System.err.println("[ERROR]: " + e.reason);
                        }
                    }
                    // Comando RF-02: Mensaje privado
                    else if (input.startsWith("/msg ")) {
                        String[] parts = input.substring(5).trim().split(" ", 2);
                        if (parts.length < 2) {
                            System.out.println("[SISTEMA]: Formato incorrecto. Uso: /msg <usuario> <mensaje>");
                            continue;
                        }

                        String targetNickname = parts[0];
                        String messageText = parts[1];

                        try {
                            server.sendPrivateMessage(nickname, targetNickname, messageText);
                            System.out.println("[SISTEMA]: Mensaje enviado a '" + targetNickname + "'.");
                        } catch (UserNotFoundException e) {
                            System.err.println("[ERROR]: " + e.reason);
                        }
                    }
                    // Comandos RF-03: Gestión de grupos
                    else if (input.startsWith("/create ")) {
                        String roomName = input.substring(8).trim();
                        if (roomName.split(" ").length == 1) {
                            try {
                                server.createRoom(roomName, nickname, callbackProxy);
                                System.out.println("[SISTEMA]: Grupo '" + roomName + "' creado.");
                            } catch (RoomAlreadyExistsException | RoomMembershipException e) {
                                System.err.println("[ERROR]: " + e.getMessage());
                            }                            
                        } else {
                            System.err.println("[ERROR]: El nombre del grupo no puede tener espacios.");
                        }
                    }
                    else if (input.startsWith("/join ")) {
                        String roomName = input.substring(6).trim();
                        try {
                            server.joinRoom(roomName, nickname, callbackProxy);
                            System.out.println("[SISTEMA]: Te uniste al grupo '" + roomName + "'.");
                        } catch (RoomNotFoundException | RoomMembershipException e) {
                            System.err.println("[ERROR]: " + e.getMessage());
                        }
                    } else if (input.startsWith("/leave ")) {
                        String roomName = input.substring(7).trim();
                        try {
                            server.leaveRoom(roomName, nickname);
                            System.out.println("[SISTEMA]: Saliste del grupo '" + roomName + "'.");
                        } catch (RoomNotFoundException | RoomMembershipException e) {
                            System.err.println("[ERROR]: " + e.getMessage());
                        }
                    } else if (input.startsWith("/group ")) {
                        String[] parts = input.substring(7).trim().split(" ", 2);
                        if (parts.length < 2) {
                            System.out.println("[SISTEMA]: Formato incorrecto. Uso: /group <grupo> <mensaje>");
                            continue;
                        }
                        try {
                            server.sendGroupMessage(parts[0], nickname, parts[1]);
                        } catch (RoomNotFoundException | RoomMembershipException e) {
                            System.err.println("[ERROR]: " + e.getMessage());
                        }
                    }
                    // Comando RF-04: Enviar archivos
                    else if (input.startsWith("/sendfile ")) {
                        String[] parts = input.substring(10).trim().split(" ", 2);
                        if (parts.length < 2) {
                            System.out.println("[SISTEMA]: Formato incorrecto. Uso: /sendfile <usuario> <ruta> O /sendfile -g <grupo> <ruta>");
                            continue;
                        }

                        if (parts[0].equals("-g")) {
                            String[] groupParts = parts[1].trim().split(" ", 2);
                            if (groupParts.length < 2) {
                                System.out.println("[SISTEMA]: Formato incorrecto. Uso: /sendfile -g <grupo> <rutaArchivo>");
                                continue;
                            }
                            FileTransferUtil.sendFile(server, nickname, groupParts[0], groupParts[1], true);
                        } else {
                            FileTransferUtil.sendFile(server, nickname, parts[0], parts[1], false);
                        }
                    }
                    // Control de Audio: Silenciarse durante una llamada (1 a 1 o grupal)
                    else if (input.equalsIgnoreCase("/mute")) {
                        if (audioRecorder.isRecording()) {
                            audioRecorder.stopRecording();
                            System.out.println("[AUDIO]: Microfono silenciado.");
                        } else {
                            System.out.println("[AUDIO]: El microfono ya esta silenciado.");
                        }
                    }
                    // Control de Audio: Activar microfono en la llamada activa
                    else if (input.equalsIgnoreCase("/unmute")) {
                        if (audioRecorder.isRecording()) {
                            System.out.println("[AUDIO]: Ya estas transmitiendo audio.");
                            continue;
                        }

                        if (activeAudioRoom[0] != null) {
                            System.out.println("[AUDIO]: Microfono activado en la llamada del grupo '" + activeAudioRoom[0] + "'.");
                            audioRecorder.startRecording(udpAudioClient::sendAudioChunk);
                        } else if (activePrivateCall[0] != null) {
                            System.out.println("[AUDIO]: Microfono activado en la llamada con '" + activePrivateCall[0] + "'.");
                            audioRecorder.startRecording(udpAudioClient::sendPrivateAudioChunk);
                        } else {
                            System.out.println("[AUDIO]: Debes estar dentro de una llamada (grupal o 1 a 1) para activar el microfono.");
                        }
                    }
                    // RF-05: Solicitar llamada 1 a 1 vía Ice
                    else if (input.startsWith("/call ") || input.startsWith("/call-user ")) {
                        String targetNickname = input.startsWith("/call ") ? input.substring(6).trim() : input.substring(11).trim();
                        if (targetNickname.isEmpty()) {
                            System.out.println("[AUDIO]: Uso: /call <usuario>");
                            continue;
                        }
                        if (targetNickname.equalsIgnoreCase(nickname)) {
                            System.out.println("[AUDIO]: No puedes llamarte a ti mismo.");
                            continue;
                        }
                        if (activeAudioRoom[0] != null) {
                            System.out.println("[AUDIO]: Sal primero de la llamada grupal.");
                            continue;
                        }
                        if (activePrivateCall[0] != null) {
                            System.out.println("[AUDIO]: Ya te encuentras en una llamada activa con '" + activePrivateCall[0] + "'. Cuelga primero (/hangup).");
                            continue;
                        }

                        try {
                            server.requestPrivateCall(nickname, targetNickname);
                            pendingOutgoingTarget[0] = targetNickname;
                            System.out.println("[AUDIO]: Timbrando a '" + targetNickname + "'... Esperando respuesta (Escribe '/hangup' para cancelar).");
                        } catch (UserNotFoundException e) {
                            System.err.println("[ERROR]: " + e.reason);
                        } catch (AudioCallAlreadyActiveException e) {
                            System.err.println("[ERROR]: " + e.reason);
                        } catch (Exception e) {
                            System.err.println("[ERROR]: Error al solicitar llamada: " + e.getMessage());
                        }
                    }
                    // RF-05: Aceptar llamada 1 a 1 vía Ice
                    else if (input.startsWith("/call-accept")) {
                        String callerNickname = null;
                        if (input.length() > 12) {
                            callerNickname = input.substring(12).trim();
                        }
                        if (callerNickname == null || callerNickname.isEmpty()) {
                            callerNickname = pendingIncomingCaller[0];
                        }

                        if (callerNickname == null || callerNickname.isEmpty()) {
                            System.out.println("[AUDIO]: No tienes ninguna llamada entrante pendiente.");
                            continue;
                        }
                        if (activeAudioRoom[0] != null) {
                            System.out.println("[AUDIO]: Sal primero de la llamada grupal.");
                            continue;
                        }
                        if (activePrivateCall[0] != null) {
                            System.out.println("[AUDIO]: Ya te encuentras en una llamada activa con '" + activePrivateCall[0] + "'.");
                            continue;
                        }

                        try {
                            server.acceptPrivateCall(nickname, callerNickname);
                            activePrivateCall[0] = callerNickname;
                            pendingIncomingCaller[0] = null;
                            udpAudioClient.startPrivateTransmission(callerNickname);
                            audioRecorder.startRecording(udpAudioClient::sendPrivateAudioChunk);
                            System.out.println("[AUDIO]: Llamada conectada con '" + callerNickname + "'. Habla ahora! (/hangup para terminar, /mute para silenciar)");
                        } catch (UserNotFoundException e) {
                            System.err.println("[ERROR]: " + e.reason);
                        } catch (AudioCallNotFoundException e) {
                            System.err.println("[ERROR]: " + e.reason);
                        } catch (AudioCallAlreadyActiveException e) {
                            System.err.println("[ERROR]: " + e.reason);
                        } catch (Exception e) {
                            System.err.println("[ERROR]: Error al aceptar la llamada: " + e.getMessage());
                        }
                    }
                    // RF-05: Rechazar llamada 1 a 1 vía Ice
                    else if (input.startsWith("/call-reject")) {
                        String callerNickname = null;
                        if (input.length() > 12) {
                            callerNickname = input.substring(12).trim();
                        }
                        if (callerNickname == null || callerNickname.isEmpty()) {
                            callerNickname = pendingIncomingCaller[0];
                        }

                        if (callerNickname == null || callerNickname.isEmpty()) {
                            System.out.println("[AUDIO]: No tienes ninguna llamada entrante pendiente.");
                            continue;
                        }

                        try {
                            server.rejectPrivateCall(nickname, callerNickname, "Llamada rechazada por el usuario");
                            pendingIncomingCaller[0] = null;
                            System.out.println("[AUDIO]: Has rechazado la llamada de '" + callerNickname + "'.");
                        } catch (Exception e) {
                            System.err.println("[ERROR]: " + e.getMessage());
                        }
                    }
                    // RF-05: Colgar llamada 1 a 1, cancelar timbrado o colgar llamada grupal
                    else if (input.equalsIgnoreCase("/hangup") || input.equalsIgnoreCase("/call-hangup") || input.equalsIgnoreCase("/private-stop")) {
                        if (activePrivateCall[0] != null) {
                            String target = activePrivateCall[0];
                            try {
                                server.hangupPrivateCall(nickname, target);
                            } catch (Exception e) {
                                System.err.println("[ERROR]: " + e.getMessage());
                            }
                            if (audioRecorder.isRecording()) {
                                audioRecorder.stopRecording();
                            }
                            udpAudioClient.stopPrivateTransmission();
                            activePrivateCall[0] = null;
                            System.out.println("[AUDIO]: Has colgado la llamada con '" + target + "'.");
                        } else if (pendingOutgoingTarget[0] != null) {
                            String target = pendingOutgoingTarget[0];
                            try {
                                server.hangupPrivateCall(nickname, target);
                            } catch (Exception e) {
                                System.err.println("[ERROR]: " + e.getMessage());
                            }
                            pendingOutgoingTarget[0] = null;
                            System.out.println("[AUDIO]: Solicitud de llamada a '" + target + "' cancelada.");
                        } else if (activeAudioRoom[0] != null) {
                            String roomName = activeAudioRoom[0];
                            try {
                                if (audioRecorder.isRecording()) {
                                    audioRecorder.stopRecording();
                                }
                                udpAudioClient.stopGroupCall();
                                server.leaveAudioCall(roomName, nickname);
                                activeAudioRoom[0] = null;
                                System.out.println("[AUDIO]: Saliste de la llamada de '" + roomName + "'.");
                            } catch (Exception e) {
                                System.err.println("[ERROR]: " + e.getMessage());
                            }
                        } else {
                            System.out.println("[AUDIO]: No hay ninguna llamada activa ni saliente para colgar.");
                        }
                    }
                    // RF-06: Llamadas grupales
                    else if (input.startsWith("/call-start ")) {
                        String roomName = input.substring(12).trim();
                        try {
                            server.startAudioCall(roomName, nickname);
                            udpAudioClient.joinCall(roomName, nickname);
                            activeAudioRoom[0] = roomName;
                            audioRecorder.startRecording(
                                    udpAudioClient::sendAudioChunk
                            );
                            System.out.println("[AUDIO]: Llamada iniciada en '" + roomName + "'.");
                        } catch (Exception e) {
                            System.err.println("[ERROR]: No se pudo iniciar la llamada: " + e.getMessage());
                        }
                    }
                    else if (input.startsWith("/call-join ")) {
                        String roomName = input.substring(11).trim();
                        try {
                            server.joinAudioCall(roomName, nickname);
                            udpAudioClient.joinCall(roomName, nickname);
                            activeAudioRoom[0] = roomName;
                            audioRecorder.startRecording(
                                    udpAudioClient::sendAudioChunk
                            );
                            System.out.println("[AUDIO]: Entraste a la llamada de '" + roomName + "'.");
                        } catch (Exception e) {
                            System.err.println("[ERROR]: " + e.getMessage());
                        }
                    }
                    else if (input.startsWith("/call-leave ")) {
                        String roomName = input.substring(12).trim();
                        if (activeAudioRoom[0] == null || !activeAudioRoom[0].equals(roomName)) {
                            System.out.println("[AUDIO]: No estas dentro de una llamada en '" + roomName + "'.");
                            continue;
                        }

                        try {
                            if (audioRecorder.isRecording()) {
                                audioRecorder.stopRecording();
                            }
                            udpAudioClient.stopGroupCall();
                            server.leaveAudioCall(roomName, nickname);
                            activeAudioRoom[0] = null;                    
                            System.out.println("[AUDIO]: Saliste de la llamada de '" + roomName + "'.");
                        } catch (Exception e) {
                            System.err.println("[ERROR]: " + e.getMessage());
                        }
                    }
                    else {
                        System.out.println("[SISTEMA]: Comando no reconocido.");
                    }
                }

                scanner.close();

                if (audioRecorder.isRecording()) {
                    audioRecorder.stopRecording();
                }
                if (activePrivateCall[0] != null) {
                    try {
                        server.hangupPrivateCall(nickname, activePrivateCall[0]);
                    } catch (Exception ignored) {}
                }
                if (pendingOutgoingTarget[0] != null) {
                    try {
                        server.hangupPrivateCall(nickname, pendingOutgoingTarget[0]);
                    } catch (Exception ignored) {}
                }
                if (activeAudioRoom[0] != null) {
                    try {
                        server.leaveAudioCall(activeAudioRoom[0], nickname);
                    } catch (Exception ignored) {}
                }
                udpAudioClient.stop();
                server.logout(nickname);
                System.out.println(">>> Sesión cerrada exitosamente.");

            } catch (UserAlreadyExistsException e) {
                System.err.println("Error de Login: " + e.reason);
            } catch (UserNotFoundException e) {
                System.err.println("Error de Logout: " + e.reason);
            }

        } catch (Exception e) {
            System.err.println("Error en la ejecución del cliente: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void help() {
        System.out.println("==================== COMANDOS DISPONIBLES ====================");
        System.out.println(">>> Catálogos y listados:");
        System.out.println("      /users                  - Listar usuarios conectados actualmente");
        System.out.println("      /rooms                  - Listar catálogo de salas disponibles");
        System.out.println("      /members <grupo>        - Listar miembros de una sala específica");
        System.out.println(">>> Mensajería privada:  /msg <usuario> <mensaje>");
        System.out.println(">>> Gestión de grupos:   /create <grupo>, /join <grupo>, /leave <grupo>");
        System.out.println(">>> Mensajes grupales:   /group <grupo> <mensaje>");
        System.out.println(">>> Enviar archivos:     /sendfile <usuario> <ruta> | /sendfile -g <grupo> <ruta>");
        System.out.println(">>> Llamadas 1 a 1 (RF-05):");
        System.out.println("      /call <usuario>         - Solicitar/timbrar llamada a otro usuario");
        System.out.println("      /call-accept <usuario>  - Aceptar llamada entrante");
        System.out.println("      /call-reject <usuario>  - Rechazar llamada entrante");
        System.out.println("      /hangup                 - Colgar llamada activa o cancelar solicitud");
        System.out.println(">>> Llamadas grupales (RF-06):");
        System.out.println("      /call-start <grupo>     - Iniciar llamada grupal");
        System.out.println("      /call-join <grupo>      - Unirse a llamada grupal");
        System.out.println("      /call-leave <grupo>     - Salir de llamada grupal");
        System.out.println(">>> Control de audio:");
        System.out.println("      /mute                   - Silenciar micrófono");
        System.out.println("      /unmute                 - Reactivar micrófono");
        System.out.println(">>> Escriba '/logout' o presione ENTER sin texto para salir.");
        System.out.println("=============================================================");
    }
}