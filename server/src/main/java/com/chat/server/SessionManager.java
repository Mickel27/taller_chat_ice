package com.chat.server.servants;

import com.chat.slice.ChatCallbackPrx;
import com.chat.slice.UserAlreadyExistsException;
import com.chat.slice.UserNotFoundException;
import com.zeroc.Ice.LocalException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    // Mapa concurrente para thread-safety de clientes activos (nickname -> Proxy del Callback)
    private final Map<String, ChatCallbackPrx> activeSessions = new ConcurrentHashMap<>();

    public void login(String nickname, ChatCallbackPrx callback) throws UserAlreadyExistsException {
        if (nickname == null || nickname.trim().isEmpty()) {
            throw new IllegalArgumentException("El nickname no puede estar vacio.");
        }

        // Rechazar conexion si el nickname ya existe en la plataforma (RF-01)
        if (activeSessions.containsKey(nickname)) {
            throw new UserAlreadyExistsException("El nickname '" + nickname + "' ya esta en uso.");
        }

        // Registrar el proxy de retorno (callback) del cliente
        activeSessions.put(nickname, callback);
        System.out.println("[SessionManager]: Usuario registrado -> " + nickname);

        // Monitor de Presencia: Notificar la conexion a todos los demas usuarios conectados (RF-01)
        broadcastPresence(nickname, true);
    }

    public void logout(String nickname) throws UserNotFoundException {
        if (!activeSessions.containsKey(nickname)) {
            throw new UserNotFoundException("El usuario '" + nickname + "' no esta registrado.");
        }

        activeSessions.remove(nickname);
        System.out.println("[SessionManager]: Usuario desconectado -> " + nickname);

        // Monitor de Presencia: Notificar la desconexion (RF-01)
        broadcastPresence(nickname, false);
    }

    private void broadcastPresence(String subjectNickname, boolean isConnecting) {
        activeSessions.forEach((user, callbackPrx) -> {
            try {
                if (isConnecting) {
                    callbackPrx.notifyUserConnected(subjectNickname);
                } else {
                    callbackPrx.notifyUserDisconnected(subjectNickname);
                }
            } catch (LocalException e) {
                // Tolerancia a fallos: Detección y remoción automática de clientes caídos abruptamente (RF-01)
                System.err.println("[SessionManager]: Cliente no responsivo (" + user + "). Eliminando sesion fantasma...");
                activeSessions.remove(user);
            }
        });
    }

    public Map<String, ChatCallbackPrx> getActiveSessions() {
        return activeSessions;
    }
}