package com.chat.client;

import java.util.Scanner;

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

            try {
                server.login(nickname, callbackProxy);
                System.out.println(">>> Sesión iniciada correctamente como: " + nickname);
                System.out.println(">>> Uso para mensajes privados: /msg <usuario> <mensaje>");
                System.out.println(">>> Grupos: /create <grupo>, /join <grupo>, /leave <grupo>, /group <grupo> <mensaje>");
                System.out.println(">>> Enviar archivos: /sendfile <usuario> <ruta> O /sendfile -g <grupo> <ruta>");
                System.out.println(">>> Escriba '/logout' o presione ENTER sin texto para salir.");

                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        server.logout(nickname);
                    } catch (Exception ignored) {}
                }));

                while (true) {
                    System.out.print("> ");
                    String input = scanner.nextLine().trim();

                    if (input.equalsIgnoreCase("/logout") || input.isEmpty()) {
                        break;
                    }

                    // Comando RF-02: /msg <destinatario> <mensaje>
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
                    // Comandos RF-03: /create, /group, /join, /leave
                    else if (input.startsWith("/create ")) {
                        String roomName = input.substring(8).trim();
                        if (roomName.split(" ").length == 1) {
                            try {
                                server.createRoom(roomName, nickname, callbackProxy);
                                System.out.println("[SISTEMA]: Grupo '" + roomName + "' creado.");
                            } catch (RoomAlreadyExistsException e) {
                                System.err.println("[ERROR]: " + e.reason);
                            } catch (RoomMembershipException e) {
                                System.err.println("[ERROR]: " + e.reason);
                            }                            
                        } else {
                            System.err.println("[ERROR]: El nombre del grupo no puede tener espacios");
                        }
                    }
                    else if (input.startsWith("/join ")) {
                        String roomName = input.substring(6).trim();
                        try {
                            server.joinRoom(roomName, nickname, callbackProxy);
                            System.out.println("[SISTEMA]: Te uniste al grupo '" + roomName + "'.");
                        } catch (RoomNotFoundException e) {
                            System.err.println("[ERROR]: " + e.reason);
                        } catch (RoomMembershipException e) {
                            System.err.println("[ERROR]: " + e.reason);
                        }
                    } else if (input.startsWith("/leave ")) {
                        String roomName = input.substring(7).trim();
                        try {
                            server.leaveRoom(roomName, nickname);
                            System.out.println("[SISTEMA]: Saliste del grupo '" + roomName + "'.");
                        } catch (RoomNotFoundException e) {
                            System.err.println("[ERROR]: " + e.reason);
                        } catch (RoomMembershipException e) {
                            System.err.println("[ERROR]: " + e.reason);
                        }
                    } else if (input.startsWith("/group ")) {
                        String[] parts = input.substring(7).trim().split(" ", 2);
                        if (parts.length < 2) {
                            System.out.println("[SISTEMA]: Formato incorrecto. Uso: /group <grupo> <mensaje>");
                            continue;
                        }
                        try {
                            server.sendGroupMessage(parts[0], nickname, parts[1]);
                        } catch (RoomNotFoundException e) {
                            System.err.println("[ERROR]: " + e.reason);
                        } catch (RoomMembershipException e) {
                            System.err.println("[ERROR]: " + e.reason);
                        }
                    }
                    // Comando RF-04: /sendfile <usuario| -g grupo> <rutaArchivo>
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
                    else {
                        System.out.println("[SISTEMA]: Comando no reconocido.");
                    }
                }

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