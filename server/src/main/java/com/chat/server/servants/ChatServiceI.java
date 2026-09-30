package com.chat.server.servants;

import com.chat.slice.ChatCallbackPrx;
import com.chat.slice.ChatService;
import com.chat.slice.UserAlreadyExistsException;
import com.chat.slice.UserNotFoundException;
import com.zeroc.Ice.Current;

public class ChatServiceI implements ChatService {

    private final SessionManager sessionManager;

    public ChatServiceImpl(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void login(String nickname, ChatCallbackPrx cb, Current current) throws UserAlreadyExistsException {
        sessionManager.login(nickname, cb);
    }

    @Override
    public void logout(String nickname, Current current) throws UserNotFoundException {
        sessionManager.logout(nickname);
    }
}