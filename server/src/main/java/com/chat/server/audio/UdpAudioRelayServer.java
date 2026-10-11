package com.chat.server.audio;

import com.chat.server.AudioCallManager;
import com.chat.server.SessionManager;
import com.chat.slice.RoomMembershipException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UdpAudioRelayServer implements Runnable {

    private final int port;
    private final AudioCallManager audioCallManager;
    private final SessionManager sessionManager;
    private final Map<String, Map<String, InetSocketAddress>> roomParticipants = new ConcurrentHashMap<>();
    private final Map<String, InetSocketAddress> privateParticipants = new ConcurrentHashMap<>();
    private volatile boolean running = false;
    private DatagramSocket socket;

    //tipificacion
    
    private static final byte GROUP_REGISTER = 1;
    private static final byte GROUP_AUDIO = 2;
    private static final byte GROUP_UNREGISTER = 3;
    private static final byte PRIVATE_REGISTER = 4;
    private static final byte PRIVATE_AUDIO = 5;
    private static final byte PRIVATE_UNREGISTER = 6;
    
    private static final int MAX_AUDIO_PAYLOAD = 1200;

    public UdpAudioRelayServer(
            int port,
            AudioCallManager audioCallManager,
            SessionManager sessionManager
    ) {
        if (audioCallManager == null) {
            throw new IllegalArgumentException("AudioCallManager no puede ser nulo.");
        }
        if (sessionManager == null) {
            throw new IllegalArgumentException("SessionManager no puede ser nulo.");
        }
        this.port = port;
        this.audioCallManager = audioCallManager;
        this.sessionManager = sessionManager;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        Thread serverThread = new Thread(this, "UdpAudioRelayServerThread");
        serverThread.setDaemon(true);
        serverThread.start();
        System.out.println(">>> Servidor UDP SFU iniciado en el puerto " + port);
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSocket(port);
            byte[] buffer = new byte[4096];

            while (running) {
                DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(receivePacket);

                InetSocketAddress senderAddr = (InetSocketAddress) receivePacket.getSocketAddress();

                try (DataInputStream data = new DataInputStream(
                        new ByteArrayInputStream(receivePacket.getData(), 0, receivePacket.getLength()))) {
                    processPacket(data, senderAddr);

                } catch (Exception e) {
                    System.err.println("[UDP RELAY ERROR]: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            if (running) {
                System.err.println("[UDP RELAY ERROR]: " + e.getMessage());
            }
        }
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        roomParticipants.clear();
        privateParticipants.clear();
    }

    private byte[] createGroupPacket(
            byte packetType,
            String roomName,
            String nickname,
            byte[] audioData
    ) throws IOException {
    
        ByteArrayOutputStream output = new ByteArrayOutputStream();
    
        try (DataOutputStream data = new DataOutputStream(output)) {
            data.writeByte(packetType);
            data.writeUTF(roomName);
            data.writeUTF(nickname);
            data.writeInt(audioData.length);
            data.write(audioData);
        }
    
        return output.toByteArray();
    }

    private void processPacket(
            DataInputStream data,
            InetSocketAddress senderAddress
    ) throws IOException {
        byte packetType = data.readByte();

        switch (packetType) {
            case GROUP_REGISTER:
            case GROUP_AUDIO:
            case GROUP_UNREGISTER:
                String roomName = data.readUTF();
                String groupNickname = data.readUTF();
                byte[] groupAudio = readAudio(data);

                if (packetType == GROUP_REGISTER) {
                    registerParticipant(roomName, groupNickname, senderAddress);
                } else if (packetType == GROUP_AUDIO) {
                    relayGroupAudio(
                            roomName,
                            groupNickname,
                            groupAudio,
                            senderAddress
                    );
                } else {
                    unregisterParticipant(
                            roomName,
                            groupNickname,
                            senderAddress
                    );
                }
                break;

            case PRIVATE_REGISTER:
                String registeringNickname = data.readUTF();
                readAudio(data);
                registerPrivateParticipant(
                        registeringNickname,
                        senderAddress
                );
                break;

            case PRIVATE_AUDIO:
                String senderNickname = data.readUTF();
                String targetNickname = data.readUTF();
                byte[] privateAudio = readAudio(data);
                relayPrivateAudio(
                        senderNickname,
                        targetNickname,
                        privateAudio,
                        senderAddress
                );
                break;

            case PRIVATE_UNREGISTER:
                String unregisteringNickname = data.readUTF();
                readAudio(data);
                unregisterPrivateParticipant(
                        unregisteringNickname,
                        senderAddress
                );
                break;

            default:
                System.err.println("[UDP RELAY]: Tipo de paquete desconocido.");
        }
    }

    private byte[] readAudio(DataInputStream data) throws IOException {
        int audioLength = data.readInt();

        if (audioLength < 0 || audioLength > MAX_AUDIO_PAYLOAD) {
            throw new IOException("Tamano de audio invalido.");
        }

        byte[] audioData = new byte[audioLength];
        if (audioLength > 0) {
            data.readFully(audioData);
        }
        return audioData;
    }

    private void registerParticipant(String roomName, String nickname, InetSocketAddress address) {
        if (!isAuthorized(roomName, nickname)) {
            System.err.println("[UDP RELAY]: Registro rechazado para '" + nickname + "'.");
            return;
        }
        roomParticipants.computeIfAbsent(roomName,
                        ignored -> new ConcurrentHashMap<>()).put(nickname, address);
    }

    private void unregisterParticipant(String roomName, String nickname, InetSocketAddress address) {
        
        Map<String, InetSocketAddress> participants = roomParticipants.get(roomName);
        if (participants == null) {
            return;
        }


        //evita que un usuario elimine la dirección de otro usuario con el mismo nickname si la dirección no coincide
        participants.remove(nickname, address);
    
        if (participants.isEmpty()) {
            roomParticipants.remove(roomName, participants);
        }

        System.out.println("[UDP RELAY]: Usuario '" + nickname + "' eliminado de la sala de audio '" + roomName + "'.");
    }

    public void removeUserFromAllRooms(String nickname) {
        roomParticipants.forEach((roomName, participants) -> {
            participants.remove(nickname);
    
            if (participants.isEmpty()) {
                roomParticipants.remove(roomName, participants);
            }
        });
    }

    private void relayGroupAudio(
            String roomName,
            String nickname,
            byte[] audioData,
            InetSocketAddress senderAddress
    ) throws IOException {
    
        if (!isAuthorized(roomName, nickname)) {
            System.err.println("[UDP RELAY]: Audio rechazado para '" + nickname + "'.");
            return;
        }
    
        Map<String, InetSocketAddress> participants =
                roomParticipants.get(roomName);
    
        if (participants == null) {
            return;
        }
    
        InetSocketAddress registeredAddress =
                participants.get(nickname);
    
        if (registeredAddress == null ||
                !registeredAddress.equals(senderAddress)) {
    
            System.err.println("[UDP RELAY]: Direccion UDP no registrada para '" + nickname + "'.");
            return;
        }
    
        byte[] payload = createGroupPacket(
                GROUP_AUDIO,
                roomName,
                nickname,
                audioData
        );
    
        for (Map.Entry<String, InetSocketAddress> entry : participants.entrySet()) {
    
            String participantNickname = entry.getKey();
    
            if (participantNickname.equals(nickname)) {
                continue;
            }
    
            InetSocketAddress destination = entry.getValue();
    
            DatagramPacket packet = new DatagramPacket(payload, payload.length, destination.getAddress(), destination.getPort());
            socket.send(packet);
        }
    }

    private void registerPrivateParticipant(
            String nickname,
            InetSocketAddress address
    ) {
        if (!sessionManager.getActiveSessions().containsKey(nickname)) {
            System.err.println(
                    "[UDP RELAY]: Registro privado rechazado para '" +
                    nickname + "'."
            );
            return;
        }

        privateParticipants.put(nickname, address);
    }

    private void unregisterPrivateParticipant(
            String nickname,
            InetSocketAddress address
    ) {
        privateParticipants.remove(nickname, address);
    }

    private void relayPrivateAudio(
            String senderNickname,
            String targetNickname,
            byte[] audioData,
            InetSocketAddress senderAddress
    ) throws IOException {
        InetSocketAddress registeredSender =
                privateParticipants.get(senderNickname);

        if (registeredSender == null ||
                !registeredSender.equals(senderAddress)) {
            return;
        }

        if (!sessionManager.getActiveSessions().containsKey(targetNickname)) {
            return;
        }

        InetSocketAddress targetAddress =
                privateParticipants.get(targetNickname);

        if (targetAddress == null) {
            return;
        }

        byte[] payload = createPrivatePacket(
                PRIVATE_AUDIO,
                senderNickname,
                targetNickname,
                audioData
        );

        DatagramPacket packet = new DatagramPacket(
                payload,
                payload.length,
                targetAddress.getAddress(),
                targetAddress.getPort()
        );
        socket.send(packet);
    }

    private byte[] createPrivatePacket(
            byte packetType,
            String senderNickname,
            String targetNickname,
            byte[] audioData
    ) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (DataOutputStream data = new DataOutputStream(output)) {
            data.writeByte(packetType);
            data.writeUTF(senderNickname);
            data.writeUTF(targetNickname);
            data.writeInt(audioData.length);
            data.write(audioData);
        }

        return output.toByteArray();
    }

    private boolean isAuthorized(String roomName, String nickname) {
        try {
            return audioCallManager.isInCall(roomName, nickname);
        } catch (RoomMembershipException e) {
            System.err.println("[UDP RELAY]: Usuario no autorizado: " + e.reason);
            return false;
        }
    }
}