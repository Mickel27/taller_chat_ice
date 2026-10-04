package com.chat.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.chat.slice.ChatCallbackPrx;
import com.chat.slice.RoomAlreadyExistsException;
import com.chat.slice.RoomMembershipException;
import com.chat.slice.RoomNotFoundException;
import com.zeroc.Ice.LocalException;

public class RoomManager {

    private final Map<String, Map<String, ChatCallbackPrx>> rooms = new ConcurrentHashMap<>();

    public void createRoom(String roomName, String ownerNickname, ChatCallbackPrx ownerCallback)
            throws RoomAlreadyExistsException, RoomMembershipException {

        //validar que el nombre introducido no sea inválido (vacio o whitespace)
        validateName(roomName, "El nombre del grupo");
        validateName(ownerNickname, "El nickname");
        if (ownerCallback == null) {
            throw new RoomMembershipException("El callback del propietario no puede ser nulo.");
        }

        //en caso de ser aprobado, se crea un conjunto tipo ConcurrentHashMap de los miembros del grupo
        Map<String, ChatCallbackPrx> members = new ConcurrentHashMap<>();
        members.put(ownerNickname, ownerCallback);

        //revisar que el grupo no exista. .putIfAbsent() pone el grupo en el grupo de grupos y retorna null si ya existe esa instancia
        if (rooms.putIfAbsent(roomName, members) != null) {
            throw new RoomAlreadyExistsException("El grupo '" + roomName + "' ya existe.");
        }
        //confirmación
        System.out.println("[RoomManager]: Grupo creado -> " + roomName + " por " + ownerNickname);
    }

    public void joinRoom(String roomName, String nickname, ChatCallbackPrx callback)
            throws RoomNotFoundException, RoomMembershipException {
        Map<String, ChatCallbackPrx> members = getRoom(roomName);
        validateName(nickname, "El nickname");
        if (callback == null) {
            throw new RoomMembershipException("El callback del usuario no puede ser nulo.");
        }
        if (members.putIfAbsent(nickname, callback) != null) {
            throw new RoomMembershipException("El usuario '" + nickname + "' ya pertenece al grupo.");
        }
        System.out.println("[RoomManager]: Usuario '" + nickname + "' se unio a " + roomName);
    }

    public void leaveRoom(String roomName, String nickname)
            throws RoomNotFoundException, RoomMembershipException {
        Map<String, ChatCallbackPrx> members = getRoom(roomName);
        if (members.remove(nickname) == null) {
            throw new RoomMembershipException("El usuario '" + nickname + "' no pertenece al grupo.");
        }
        if (members.isEmpty()) {
            rooms.remove(roomName, members);
        }
    }

    public void leaveAllRooms(String nickname) {
        rooms.forEach((roomName, members) -> {
            members.remove(nickname);
            if (members.isEmpty()) {
                rooms.remove(roomName, members);
            }
        });
    }

    public void sendGroupMessage(String roomName, String senderNickname, String message)
            throws RoomNotFoundException, RoomMembershipException {
        Map<String, ChatCallbackPrx> members = getRoom(roomName);
        if (!members.containsKey(senderNickname)) {
            throw new RoomMembershipException("El usuario '" + senderNickname + "' no pertenece al grupo.");
        }
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacio.");
        }

        members.forEach((nickname, callback) -> {
            try {
                callback.receiveGroupMessage(roomName, senderNickname, message);
            } catch (LocalException e) {
                members.remove(nickname, callback);
                System.err.println("[RoomManager]: Cliente no responsivo (" + nickname
                        + "). Eliminado del grupo '" + roomName + "'.");
            }
        });
    }

    private Map<String, ChatCallbackPrx> getRoom(String roomName) throws RoomNotFoundException {
        validateName(roomName, "El nombre del grupo");
        Map<String, ChatCallbackPrx> members = rooms.get(roomName);
        if (members == null) {
            throw new RoomNotFoundException("El grupo '" + roomName + "' no existe.");
        }
        return members;
    }

    private void validateName(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " no puede estar vacio.");
        }
    }
}