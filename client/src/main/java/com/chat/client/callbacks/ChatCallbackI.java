package com.chat.client.callbacks;

import com.chat.slice.ChatCallback;
import com.zeroc.Ice.Current;

public class ChatCallbackI implements ChatCallback {

    @Override
    public void notifyUserConnected(String nickname, Current current) {
        System.out.println("\n[PRESENCIA]: El usuario '" + nickname + "' se ha conectado.");
        System.out.print("> ");
    }

    @Override
    public void notifyUserDisconnected(String nickname, Current current) {
        System.out.println("\n[PRESENCIA]: El usuario '" + nickname + "' se ha desconectado.");
        System.out.print("> ");
    }
}