package com.chat.server;

import com.chat.slice.AudioCallAlreadyActiveException;
import com.chat.slice.AudioCallMembershipException;
import com.chat.slice.AudioCallNotFoundException;
import com.chat.slice.RoomMembershipException;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AudioCallManager {

    private final RoomManager roomManager;

    private final Map<String, Set<String>> activeCalls =
            new ConcurrentHashMap<>();

    // Mapas para llamadas privadas 1 a 1
    // callee -> caller (llamadas entrantes pendientes)
    private final Map<String, String> pendingCalls = new ConcurrentHashMap<>();
    // caller -> callee (llamadas salientes pendientes)
    private final Map<String, String> callerToPendingTarget = new ConcurrentHashMap<>();
    // usuario -> pareja en llamada 1 a 1 activa (bidireccional)
    private final Map<String, String> activePrivateCalls = new ConcurrentHashMap<>();

    public AudioCallManager(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    public boolean isUserInAnyCall(String nickname) {
        if (activePrivateCalls.containsKey(nickname)) {
            return true;
        }
        for (Set<String> members : activeCalls.values()) {
            if (members.contains(nickname)) {
                return true;
            }
        }
        return false;
    }

    public void requestPrivateCall(String caller, String target)
            throws AudioCallAlreadyActiveException {
        if (caller.equals(target)) {
            throw new IllegalArgumentException("No puedes llamarte a ti mismo.");
        }
        if (isUserInAnyCall(caller)) {
            throw new AudioCallAlreadyActiveException("Ya te encuentras en una llamada activa.");
        }
        if (isUserInAnyCall(target)) {
            throw new AudioCallAlreadyActiveException("El usuario '" + target + "' se encuentra ocupado en otra llamada.");
        }
        if (callerToPendingTarget.containsKey(caller)) {
            throw new AudioCallAlreadyActiveException("Ya tienes una solicitud de llamada saliente pendiente.");
        }
        if (pendingCalls.containsKey(target)) {
            throw new AudioCallAlreadyActiveException("El usuario '" + target + "' tiene otra llamada entrante pendiente.");
        }

        pendingCalls.put(target, caller);
        callerToPendingTarget.put(caller, target);
        System.out.println("[AudioCallManager]: Solicitud de llamada 1 a 1 de '" + caller + "' a '" + target + "'.");
    }

    public void acceptPrivateCall(String callee, String caller)
            throws AudioCallNotFoundException, AudioCallAlreadyActiveException {
        String registeredCaller = pendingCalls.get(callee);
        if (registeredCaller == null || !registeredCaller.equals(caller)) {
            throw new AudioCallNotFoundException("No hay una llamada pendiente de '" + caller + "'.");
        }
        if (isUserInAnyCall(callee)) {
            throw new AudioCallAlreadyActiveException("Ya te encuentras en una llamada activa.");
        }
        if (isUserInAnyCall(caller)) {
            pendingCalls.remove(callee);
            callerToPendingTarget.remove(caller);
            throw new AudioCallAlreadyActiveException("El usuario '" + caller + "' ya entro a otra llamada.");
        }

        pendingCalls.remove(callee);
        callerToPendingTarget.remove(caller);

        activePrivateCalls.put(callee, caller);
        activePrivateCalls.put(caller, callee);
        System.out.println("[AudioCallManager]: Llamada 1 a 1 aceptada entre '" + caller + "' y '" + callee + "'.");
    }

    public void rejectPrivateCall(String callee, String caller)
            throws AudioCallNotFoundException {
        String registeredCaller = pendingCalls.get(callee);
        if (registeredCaller == null || !registeredCaller.equals(caller)) {
            throw new AudioCallNotFoundException("No hay una llamada pendiente de '" + caller + "'.");
        }

        pendingCalls.remove(callee);
        callerToPendingTarget.remove(caller);
        System.out.println("[AudioCallManager]: Llamada de '" + caller + "' rechazada por '" + callee + "'.");
    }

    public String hangupPrivateCall(String sender, String target)
            throws AudioCallNotFoundException {
        String partner = activePrivateCalls.get(sender);
        if (partner != null && partner.equals(target)) {
            activePrivateCalls.remove(sender);
            activePrivateCalls.remove(target);
            System.out.println("[AudioCallManager]: Llamada 1 a 1 finalizada entre '" + sender + "' y '" + target + "'.");
            return partner;
        }

        // Caso: cancelar una llamada saliente pendiente
        String pendingTarget = callerToPendingTarget.get(sender);
        if (pendingTarget != null && pendingTarget.equals(target)) {
            callerToPendingTarget.remove(sender);
            pendingCalls.remove(target);
            System.out.println("[AudioCallManager]: Solicitud de llamada a '" + target + "' cancelada por '" + sender + "'.");
            return target;
        }

        throw new AudioCallNotFoundException("No tienes una llamada activa ni pendiente con '" + target + "'.");
    }

    public boolean isPrivateCallActive(String user1, String user2) {
        String partner = activePrivateCalls.get(user1);
        return partner != null && partner.equals(user2);
    }

    public String getActivePrivatePartner(String nickname) {
        return activePrivateCalls.get(nickname);
    }

    public String cleanUserPrivateCalls(String nickname) {
        String partner = activePrivateCalls.remove(nickname);
        if (partner != null) {
            activePrivateCalls.remove(partner);
            return partner;
        }

        String target = callerToPendingTarget.remove(nickname);
        if (target != null) {
            pendingCalls.remove(target);
            return target;
        }

        String caller = pendingCalls.remove(nickname);
        if (caller != null) {
            callerToPendingTarget.remove(caller);
            return caller;
        }

        return null;
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