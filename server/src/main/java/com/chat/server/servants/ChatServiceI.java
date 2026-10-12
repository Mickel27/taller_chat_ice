package com.chat.server.servants;

import com.chat.server.AudioCallManager;
import com.chat.slice.AudioCallAlreadyActiveException;
import com.chat.slice.AudioCallMembershipException;
import com.chat.slice.AudioCallNotFoundException;
import com.chat.server.RoomManager;
import com.chat.server.SessionManager;
import com.chat.slice.ChatCallbackPrx;
import com.chat.slice.ChatService;
import com.chat.slice.FileChunk;
import com.chat.slice.RoomAlreadyExistsException;
import com.chat.slice.RoomMembershipException;
import com.chat.slice.RoomNotFoundException;
import com.chat.slice.UserAlreadyExistsException;
import com.chat.slice.UserNotFoundException;
import com.zeroc.Ice.Current;
import com.zeroc.Ice.LocalException;

public class ChatServiceI implements ChatService {

    private final SessionManager sessionManager;
    private final RoomManager roomManager;
    private final AudioCallManager audioCallManager;

    public ChatServiceI(SessionManager sessionManager, RoomManager roomManager, AudioCallManager audioCallManager) {
        this.sessionManager = sessionManager;
        this.roomManager = roomManager;
        this.audioCallManager = audioCallManager;
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

    @Override
    public void sendFileChunk(String senderNickname, FileChunk chunk, Current current)
            throws UserNotFoundException, RoomNotFoundException, RoomMembershipException {
        if (!sessionManager.getActiveSessions().containsKey(senderNickname)) {
            throw new RoomMembershipException("El usuario no tiene una sesion activa.");
        }

        if (chunk.isGroup) {
            roomManager.sendGroupFileChunk(senderNickname, chunk);
        } else {
            sessionManager.sendPrivateFileChunk(senderNickname, chunk);
        }
    }

    @Override
    public void requestPrivateCall(String callerNickname, String targetNickname, Current current)
            throws UserNotFoundException, AudioCallAlreadyActiveException {
        requireActiveUser(callerNickname);

        ChatCallbackPrx targetCb = sessionManager.getCallback(targetNickname);
        if (targetCb == null) {
            throw new UserNotFoundException("El usuario '" + targetNickname + "' no existe o no esta conectado.");
        }

        audioCallManager.requestPrivateCall(callerNickname, targetNickname);

        try {
            targetCb.notifyIncomingCall(callerNickname);
        } catch (LocalException e) {
            audioCallManager.cleanUserPrivateCalls(targetNickname);
            throw new UserNotFoundException("El usuario '" + targetNickname + "' se desconecto inesperadamente.");
        }
    }

    @Override
    public void acceptPrivateCall(String calleeNickname, String callerNickname, Current current)
            throws UserNotFoundException, AudioCallNotFoundException, AudioCallAlreadyActiveException {
        requireActiveUser(calleeNickname);

        ChatCallbackPrx callerCb = sessionManager.getCallback(callerNickname);
        if (callerCb == null) {
            audioCallManager.cleanUserPrivateCalls(callerNickname);
            throw new UserNotFoundException("El usuario '" + callerNickname + "' ya no esta conectado.");
        }

        audioCallManager.acceptPrivateCall(calleeNickname, callerNickname);

        try {
            callerCb.notifyCallAccepted(calleeNickname);
        } catch (LocalException e) {
            audioCallManager.hangupPrivateCall(calleeNickname, callerNickname);
            throw new UserNotFoundException("El usuario '" + callerNickname + "' se desconecto al contestar.");
        }
    }

    @Override
    public void rejectPrivateCall(String calleeNickname, String callerNickname, String reason, Current current)
            throws UserNotFoundException, AudioCallNotFoundException {
        requireActiveUser(calleeNickname);

        audioCallManager.rejectPrivateCall(calleeNickname, callerNickname);

        ChatCallbackPrx callerCb = sessionManager.getCallback(callerNickname);
        if (callerCb != null) {
            try {
                callerCb.notifyCallRejected(calleeNickname, reason);
            } catch (LocalException ignored) {}
        }
    }

    @Override
    public void hangupPrivateCall(String senderNickname, String targetNickname, Current current)
            throws UserNotFoundException, AudioCallNotFoundException {
        requireActiveUser(senderNickname);

        String partner = audioCallManager.hangupPrivateCall(senderNickname, targetNickname);

        ChatCallbackPrx targetCb = sessionManager.getCallback(partner);
        if (targetCb != null) {
            try {
                targetCb.notifyCallEnded(senderNickname);
            } catch (LocalException ignored) {}
        }
    }

    private void requireActiveUser(String nickname) throws UserNotFoundException {
        if (!sessionManager.getActiveSessions().containsKey(nickname)) {
            throw new UserNotFoundException("El usuario '" + nickname + "' no tiene una sesion activa.");
        }
    }

    @Override
    public void startAudioCall(
            String roomName,
            String nickname,
            Current current
    ) throws RoomNotFoundException,
             RoomMembershipException,
             AudioCallAlreadyActiveException {
    
        requireActiveSession(nickname);
    
        audioCallManager.startCall(roomName, nickname);
    
        roomManager.notifyAudioCallJoined(roomName, nickname);
    }

    @Override
    public void joinAudioCall(
            String roomName,
            String nickname,
            Current current
    ) throws RoomNotFoundException,
             RoomMembershipException,
             AudioCallNotFoundException,
             AudioCallMembershipException {
    
        requireActiveSession(nickname);
    
        audioCallManager.joinCall(roomName, nickname);
    
        roomManager.notifyAudioCallJoined(
                roomName,
                nickname
        );
    }

    @Override
    public void leaveAudioCall(
            String roomName,
            String nickname,
            Current current
    ) throws RoomNotFoundException,
             RoomMembershipException,
             AudioCallNotFoundException,
             AudioCallMembershipException {
    
        requireActiveSession(nickname);
    
        boolean ended = audioCallManager.leaveCall(
                roomName,
                nickname
        );
    
        roomManager.notifyAudioCallLeft(
                roomName,
                nickname
        );
    
        if (ended) {
            roomManager.notifyAudioCallEnded(roomName);
        }
    }

    @Override
    public boolean isInAudioCall(
            String roomName,
            String nickname,
            Current current
    ) throws RoomNotFoundException,
             RoomMembershipException {
    
        requireActiveSession(nickname);
    
        return audioCallManager.isInCall(roomName, nickname);
    }

    private void requireActiveSession(String nickname)
            throws RoomMembershipException {
    
        if (!sessionManager.getActiveSessions().containsKey(nickname)) {
            throw new RoomMembershipException(
                    "El usuario no tiene una sesion activa."
            );
        }
    }
}