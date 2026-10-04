package com.chat.server.servants;

import com.chat.server.RoomManager;
import com.chat.server.SessionManager;
import com.chat.slice.ChatCallbackPrx;
import com.chat.slice.ChatService;
import com.chat.slice.RoomAlreadyExistsException;
import com.chat.slice.RoomMembershipException;
import com.chat.slice.RoomNotFoundException;
import com.chat.slice.UserAlreadyExistsException;
import com.chat.slice.UserNotFoundException;
import com.zeroc.Ice.Current;

public class ChatServiceI implements ChatService {

    private final SessionManager sessionManager;
    private final RoomManager roomManager;

    public ChatServiceI(SessionManager sessionManager, RoomManager roomManager) {
        this.sessionManager = sessionManager;
        this.roomManager = roomManager;
    }

    @Override
    public void login(String nickname, ChatCallbackPrx cb, Current current) throws UserAlreadyExistsException {
        sessionManager.login(nickname, cb);
    }

    @Override
    public void logout(String nickname, Current current) throws UserNotFoundException {
        sessionManager.logout(nickname);
    }

    @Override
    public void sendPrivateMessage(String senderNickname, String targetNickname, String message, Current current)
            throws UserNotFoundException {
        sessionManager.sendPrivateMessage(senderNickname, targetNickname, message);
    }

    @Override
    public void createRoom(String roomName, String ownerNickname, ChatCallbackPrx ownerCallback, Current current)
            throws RoomAlreadyExistsException, RoomMembershipException {
        roomManager.createRoom(roomName, ownerNickname, ownerCallback);
    }

    @Override
    public void joinRoom(String roomName, String nickname, ChatCallbackPrx callback, Current current)
    throws RoomNotFoundException, RoomMembershipException {
        if (!sessionManager.getActiveSessions().containsKey(nickname)) {
            throw new RoomMembershipException("El usuario no tiene una sesion activa.");
        }
        roomManager.joinRoom(roomName, nickname, callback);
    }

    @Override
    public void leaveRoom(String roomName, String nickname, Current current)
    throws RoomNotFoundException, RoomMembershipException {
        if (!sessionManager.getActiveSessions().containsKey(nickname)) {
            throw new RoomMembershipException("El usuario no tiene una sesion activa.");
        }
        roomManager.leaveRoom(roomName, nickname);
    }

    @Override
    public void sendGroupMessage(String roomName, String senderNickname, String message, Current current)
    throws RoomNotFoundException, RoomMembershipException {
        if (!sessionManager.getActiveSessions().containsKey(senderNickname)) {
            throw new RoomMembershipException("El usuario no tiene una sesion activa.");
        }
        roomManager.sendGroupMessage(roomName, senderNickname, message);
    }
}