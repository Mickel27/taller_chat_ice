package com.chat.server;

import com.chat.server.servants.ChatServiceI;
import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.ObjectAdapter;
import com.zeroc.Ice.Util;

public class ServerMain {

    public static void main(String[] args) {
        System.out.println(">>> Iniciando Servidor Ice Chat...");

        try (Communicator communicator = Util.initialize(args, "config.server")) {
            SessionManager sessionManager = new SessionManager();
            RoomManager roomManager = new RoomManager();

            sessionManager.setRoomManager(roomManager);

            // Crear adaptador de objetos usando la propiedad definida en config.server
            ObjectAdapter adapter = communicator.createObjectAdapter("ChatServiceAdapter");

            // Registrar el servant en el adaptador
            ChatServiceI chatService = new ChatServiceI(sessionManager, roomManager);
            adapter.add(chatService, Util.stringToIdentity("ChatService"));

            adapter.activate();
            System.out.println(">>> Servidor Ice escuchando peticiones en el puerto 10000.");

            communicator.waitForShutdown();
        } catch (Exception e) {
            System.err.println("Error fatal en la ejecucion del servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}