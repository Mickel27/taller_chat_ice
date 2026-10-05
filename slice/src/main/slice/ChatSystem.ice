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

    exception RoomAlreadyExistsException {
        string reason;
    };

    exception RoomNotFoundException {
        string reason;
    };

    exception RoomMembershipException {
        string reason;
    };

    // RF-04: Secuencia de bytes para el transporte binario de fragmentos
    sequence<byte> ByteSeq;

    // RF-04: Estructura de metadatos y contenido del fragmento (Chunk)
    struct FileChunk {
        string fileId;
        string filename;
        long totalSize;
        int chunkIndex;
        int totalChunks;
        ByteSeq data;
        string target;        // Destinatario (nickname de usuario o nombre de grupo)
        bool isGroup;         // true si es mensaje de grupo, false para chat privado
    };

    // Interfaz de retorno (Callback) que implementa el CLIENTE
    interface ChatCallback {
        void notifyUserConnected(string nickname);
        void notifyUserDisconnected(string nickname);
        
        // RF-02: Recibir un mensaje privado enviado por otro usuario
        void receivePrivateMessage(string senderNickname, string message);
        
        // RF-03: Recibir mensaje grupal
        void receiveGroupMessage(string roomName, string senderNickname, string message);

        // RF-04: Recibir fragmento de archivo multimedia
        void receiveFileChunk(string senderNickname, FileChunk chunk);
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
        
        // RF-02: Crear sala
        void createRoom(string roomName, string ownerNickname, ChatCallback* ownerCallback)
            throws RoomAlreadyExistsException, RoomMembershipException;

        void joinRoom(string roomName, string nickname, ChatCallback* callback)
            throws RoomNotFoundException, RoomMembershipException;

        void leaveRoom(string roomName, string nickname)
            throws RoomNotFoundException, RoomMembershipException;

        void sendGroupMessage(string roomName, string senderNickname, string message)
            throws RoomNotFoundException, RoomMembershipException;

        // RF-04: Enviar fragmento de archivo multimedia
        void sendFileChunk(string senderNickname, FileChunk chunk)
            throws UserNotFoundException, RoomNotFoundException, RoomMembershipException;
    };

};
};
};