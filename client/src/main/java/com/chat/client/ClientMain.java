package com.chat.client;

import java.util.Scanner;

import com.chat.client.audio.AudioRecorder;
import com.chat.client.audio.UdpAudioClient;
import com.chat.client.callbacks.ChatCallbackI;
import com.chat.client.util.FileTransferUtil;
import com.chat.slice.ChatCallbackPrx;
import com.chat.slice.ChatServicePrx;
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
            
            //Control de actividad de llamada, para evitar que el cliente intente entrar a una llamada estando en otra
            String[] activeAudioRoom = { null };

            try {
                server.login(nickname, callbackProxy);
                udpAudioClient.startPrivateEndpoint(nickname);
                System.out.println(">>> Sesión iniciada correctamente como: " + nickname);
                System.out.println(">>> Mensajes privados: /msg <usuario> <mensaje>");
                System.out.println(">>> Grupos: /create <grupo>, /join <grupo>, /leave <grupo>, /group <grupo> <mensaje>");
                System.out.println(">>> Enviar archivos: /sendfile <usuario| -g grupo> <ruta>");
                System.out.println(">>> Llamadas: /call-start <grupo>, /call-join <grupo>, /call-leave <grupo>");
                System.out.println(">>> Audio grupal: /record, /stoprecord");
                System.out.println(">>> Audio individual: /record <usuario>, /stoprecord");
                System.out.println(">>> Escriba '/logout' o presione ENTER sin texto para salir.");

                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        if (audioRecorder.isRecording()) {
                            audioRecorder.stopRecording();
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

                    // Comando RF-02: Mensaje privado
                    if (input.startsWith("/msg ")) {
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
                    // RF-05: Iniciar transmisión de nota de voz
                    else if (input.equalsIgnoreCase("/record")) {
                        if (activeAudioRoom[0] == null) {
                            System.out.println("[AUDIO]: Primero debes entrar a una llamada grupal.");
                            continue;
                        }
                    
                        if (audioRecorder.isRecording()) {
                            System.out.println("[AUDIO]: Ya estas transmitiendo audio.");
                            continue;
                        }
                        System.out.println("[AUDIO]: Transmitiendo en la llamada del grupo '" +activeAudioRoom[0] + "'.");
                        audioRecorder.startRecording(udpAudioClient::sendAudioChunk);
                    }
                    // RF-05: Transmision de audio individual
                    else if (input.startsWith("/record ")) {
                        String targetNickname = input.substring(8).trim();

                        if (targetNickname.isEmpty()) {
                            System.out.println(
                                    "[AUDIO]: Uso: /record <usuario>"
                            );
                            continue;
                        }

                        if (activeAudioRoom[0] != null) {
                            System.out.println(
                                    "[AUDIO]: Sal primero de la llamada grupal."
                            );
                            continue;
                        }

                        if (audioRecorder.isRecording()) {
                            System.out.println(
                                    "[AUDIO]: Ya estas transmitiendo audio."
                            );
                            continue;
                        }

                        try {
                            udpAudioClient.startPrivateTransmission(
                                    targetNickname
                            );
                            audioRecorder.startRecording(
                                    udpAudioClient::sendPrivateAudioChunk
                            );
                            System.out.println(
                                    "[AUDIO]: Transmitiendo a '" +
                                    targetNickname + "'."
                            );
                        } catch (IllegalArgumentException |
                                 IllegalStateException e) {
                            System.err.println(
                                    "[ERROR]: " + e.getMessage()
                            );
                        }
                    }
                    // RF-05: Detener transmisión de nota de voz
                    else if (input.equalsIgnoreCase("/stoprecord")) {
                        if (audioRecorder.isRecording()) {
                            audioRecorder.stopRecording();
                            udpAudioClient.stopPrivateTransmission();
                            System.out.println("[AUDIO]: Transmisión de voz finalizada.");
                        } else {
                            System.out.println("[AUDIO]: No hay ninguna grabación en curso.");
                        }
                    }

                    else if (input.startsWith("/call-start ")) {
                        String roomName = input.substring(12).trim();
                        try {
                            server.startAudioCall(roomName, nickname);
                            udpAudioClient.joinCall(roomName, nickname);
                            activeAudioRoom[0] = roomName;
                            System.out.println("[AUDIO]: Llamada iniciada en '" + roomName + "'.");
                        } catch (Exception e) {
                            System.err.println("[ERROR]: No se pudo iniciar la llamada: " +e.getMessage());
                        }
                    }

                    else if (input.startsWith("/call-join ")) {
                        String roomName = input.substring(11).trim();
                        try {
                            server.joinAudioCall(roomName, nickname);
                            udpAudioClient.joinCall(roomName, nickname);
                            activeAudioRoom[0] = roomName;
                            System.out.println("[AUDIO]: Entraste a la llamada de '" +roomName + "'.");
                        } catch (Exception e) {
                            System.err.println("[ERROR]: " + e.getMessage());
                        }
                    }

                    else if (input.startsWith("/call-leave ")) {
                        String roomName = input.substring(12).trim();
                        if (activeAudioRoom[0] == null || !activeAudioRoom[0].equals(roomName)) {
                            System.out.println("[AUDIO]: No estas dentro de una llamada en '" +roomName + "'.");
                            continue;
                        }

                        try {
                            if (audioRecorder.isRecording()) {
                                audioRecorder.stopRecording();
                            }
                            udpAudioClient.stopGroupCall();
                            server.leaveAudioCall(roomName, nickname);
                            activeAudioRoom[0] = null;                    
                            System.out.println("[AUDIO]: Saliste de la llamada de '" +roomName + "'.");
                    
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
}