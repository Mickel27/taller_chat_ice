package com.chat.server;

import com.chat.slice.AudioCallAlreadyActiveException;
import com.chat.slice.AudioCallMembershipException;
import com.chat.slice.AudioCallNotFoundException;
import com.chat.slice.ChatCallbackPrx;
import com.chat.slice.RoomMembershipException;
import com.chat.slice.RoomNotFoundException;
import com.zeroc.Ice.LocalException;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AudioCallManager {

    private final RoomManager roomManager;

    private final Map<String, Set<String>> activeCalls =
            new ConcurrentHashMap<>();

    public AudioCallManager(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    public void startCall(
            String roomName,
            String nickname
    ) throws AudioCallAlreadyActiveException,
             RoomMembershipException {
    
        validateRoomMember(roomName, nickname);
    
        Set<String> existingCall = activeCalls.putIfAbsent(
                roomName,
                ConcurrentHashMap.newKeySet()
        );
    
        if (existingCall != null) {
            throw new AudioCallAlreadyActiveException(
                    "El grupo '" + roomName + "' ya tiene una llamada activa."
            );
        }
    
        activeCalls.get(roomName).add(nickname);
    
        System.out.println(
                "[AudioCallManager]: Llamada iniciada en '" +
                roomName + "' por " + nickname
        );
    }

    public void joinCall(
            String roomName,
            String nickname
    ) throws AudioCallNotFoundException,
             RoomMembershipException, AudioCallMembershipException {
    
        validateRoomMember(roomName, nickname);
    
        Set<String> participants = activeCalls.get(roomName);
    
        if (participants == null) {
            throw new AudioCallNotFoundException(
                    "No hay una llamada activa en el grupo '" +
                    roomName + "'."
            );
        }
    
        if (!participants.add(nickname)) {
            throw new AudioCallMembershipException(
                    "El usuario ya esta dentro de la llamada."
            );
        }
    
        System.out.println(
                "[AudioCallManager]: " + nickname +
                " entro a la llamada de '" + roomName + "'."
        );
    }

    public boolean leaveCall(
            String roomName,
            String nickname
    ) throws AudioCallNotFoundException,
             AudioCallMembershipException {
    
        Set<String> participants = activeCalls.get(roomName);
    
        if (participants == null) {
            throw new AudioCallNotFoundException(
                    "No hay una llamada activa en el grupo '" +
                    roomName + "'."
            );
        }
    
        if (!participants.remove(nickname)) {
            throw new AudioCallMembershipException(
                    "El usuario no pertenece a la llamada."
            );
        }
    
        boolean ended = participants.isEmpty();
    
        if (ended) {
            activeCalls.remove(roomName, participants);
    
            System.out.println(
                    "[AudioCallManager]: Llamada terminada en '" +
                    roomName + "'."
            );
        }
    
        return ended;
    }

    public boolean isInCall(
            String roomName,
            String nickname
    ) throws RoomMembershipException {
    
        validateRoomMember(roomName, nickname);
    
        Set<String> participants = activeCalls.get(roomName);
    
        return participants != null && participants.contains(nickname);
    }

    public void leaveAllCalls(String nickname) {
        activeCalls.forEach((roomName, participants) -> {
            participants.remove(nickname);
    
            if (participants.isEmpty()) {
                activeCalls.remove(roomName, participants);
    
                System.out.println(
                        "[AudioCallManager]: Llamada terminada en '" +
                        roomName + "' por quedar vacia."
                );
            }
        });
    }
    
    private void validateRoomMember(
            String roomName,
            String nickname
    ) throws RoomMembershipException {
        roomManager.isMember(roomName, nickname);
    }
}