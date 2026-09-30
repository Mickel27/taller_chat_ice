package com.chat.client;

import com.chat.client.callbacks.ChatCallbackI;
import com.chat.slice.ChatCallbackPrx;
import com.chat.slice.ChatServicePrx;
import com.chat.slice.UserAlreadyExistsException;
import com.chat.slice.UserNotFoundException;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.ObjectPrx;
import com.zeroc.Ice.Util;

import java.util.Scanner;

public class ClientMain {

    public static void main(String[] args) {
        // Inicialización del Communicator con la configuración del cliente
        try (Communicator communicator = Util.initialize(args, "config.client")) {
            
            // 1. Conexión con el servidor mediante el proxy
            ObjectPrx base = communicator.propertyToProxy("ChatService.Proxy");
            ChatServicePrx server = ChatServicePrx.checkedCast(base);

            if (server == null) {
                System.err.println("Error: No se pudo obtener el proxy del servidor.");
                return;
            }

            // 2. Creación del ObjectAdapter local para callbacks
            ObjectAdapter adapter = communicator.createObjectAdapter("CallbackAdapter");
            ChatCallbackI callbackServant = new ChatCallbackI();
            
            // Registro del servant local y obtención de su proxy único
            ObjectPrx cbPrxBase = adapter.addWithUUID(callbackServant);
            adapter.activate();
            ChatCallbackPrx callbackProxy = ChatCallbackPrx.uncheckedCast(cbPrxBase);

            // 3. Captura del nickname e inicio de sesión (RF-01)
            Scanner scanner = new Scanner(System.in);
            System.out.print("Ingrese su nickname: ");
            String nickname = scanner.nextLine().trim();

            if (nickname.isEmpty()) {
                System.err.println("El nickname no puede estar vacío.");
                return;
            }

            try {
                // Registro de sesión y envío del callback al servidor
                server.login(nickname, callbackProxy);
                System.out.println(">>> Sesión iniciada correctamente como: " + nickname);

                // ShutdownHook para garantizar desconexión limpia al cerrar la consola (RF-01)
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        server.logout(nickname);
                    } catch (Exception ignored) {}
                }));

                System.out.println("Escriba '/logout' o presione ENTER para salir.");
                System.out.print("> ");

                while (true) {
                    String input = scanner.nextLine().trim();
                    if (input.equalsIgnoreCase("/logout") || input.isEmpty()) {
                        break;
                    }
                }

                // Cierre de sesión explícito
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