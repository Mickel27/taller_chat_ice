module com {
module chat {
module slice {

    // Excepción si el nickname ya se encuentra en uso al conectarse
    exception UserAlreadyExistsException {
        string reason;
    };

    // Excepción si el destinatario o usuario no existe/no está activo
    exception UserNotFoundException {
        string reason;
    };

    // Interfaz de retorno (Callback) que implementa el CLIENTE
    interface ChatCallback {
        void notifyUserConnected(string nickname);
        void notifyUserDisconnected(string nickname);
        
        // RF-02: Recibir un mensaje privado enviado por otro usuario
        void receivePrivateMessage(string senderNickname, string message);
    };

    // Interfaz del servicio principal que implementa el SERVIDOR
    interface ChatService {
        void login(string nickname, ChatCallback* cb) 
            throws UserAlreadyExistsException;
            
        void logout(string nickname) 
            throws UserNotFoundException;

        // RF-02: Enviar un mensaje privado de un remitente a un destinatario
        void sendPrivateMessage(string senderNickname, string targetNickname, string message)
            throws UserNotFoundException;
    };

};
};
};