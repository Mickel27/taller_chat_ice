module com {
module chat {
module slice {

    // Excepción personalizada si el nickname ya se encuentra en uso
    exception UserAlreadyExistsException {
        string reason;
    };

    // Excepción personalizada si el usuario no está registrado
    exception UserNotFoundException {
        string reason;
    };

    // Interfaz de retorno (Callback) que implementa el CLIENTE
    interface ChatCallback {
        void notifyUserConnected(string nickname);
        void notifyUserDisconnected(string nickname);
    };

    // Interfaz del servicio principal que implementa el SERVIDOR
    interface ChatService {
        void login(string nickname, ChatCallback* cb) 
            throws UserAlreadyExistsException;
            
        void logout(string nickname) 
            throws UserNotFoundException;
    };

};
};
};