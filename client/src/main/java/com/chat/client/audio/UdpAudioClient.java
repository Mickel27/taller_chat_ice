package com.chat.client.audio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UdpAudioClient {

    private final String serverHost;
    private final int serverPort;
    private final AudioPlayer player;
    private DatagramSocket socket;
    private volatile boolean listening = false;
    private static final byte GROUP_REGISTER = 1;
    private static final byte AUDIO_GROUP = 2;
    private static final byte GROUP_UNREGISTER = 3;
    private static final byte PRIVATE_REGISTER = 4;
    private static final byte AUDIO_PRIVATE = 5;
    private static final byte PRIVATE_UNREGISTER = 6;
    private String activeRoom;
    private String nickname;
    private String privateTarget;
    private boolean privateEndpointRegistered;

    public UdpAudioClient(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.player = new AudioPlayer();
    }

    public void sendAudioChunk(byte[] audioData){
        if (!listening || socket == null || socket.isClosed() || activeRoom == null || nickname == null) {
            return;
        }
    
        if (audioData == null || audioData.length == 0) {
            return;
        }
    
        try {
            byte[] payload = createGroupPayload(
                    AUDIO_GROUP,
                    activeRoom,
                    nickname,
                    audioData
            );
    
            InetAddress serverAddress = InetAddress.getByName(serverHost);
    
            DatagramPacket packet = new DatagramPacket(payload, payload.length, serverAddress, serverPort);
    
            socket.send(packet);
    
        } catch (IOException e) {
            if (listening) {
                System.err.println("[UDP CLIENT]: Error enviando audio: " + e.getMessage());
            }
        }
    }

    public synchronized void stop() {
        if (socket != null && !socket.isClosed() && activeRoom != null && nickname != null) {
            try {
                sendGroupControlPacket(GROUP_UNREGISTER);
            } catch (IOException e) {
                System.err.println("[UDP CLIENT]: No se pudo enviar UNREGISTER: " +e.getMessage());
            }
        }
        if (socket != null &&
                !socket.isClosed() &&
                privateEndpointRegistered &&
                nickname != null) {
            try {
                sendPrivateControlPacket(PRIVATE_UNREGISTER);
            } catch (IOException e) {
                System.err.println(
                        "[UDP CLIENT]: No se pudo desregistrar el audio privado: " +
                        e.getMessage()
                );
            }
        }
        listening = false;
        if (socket != null &&!socket.isClosed()) {
            socket.close();
        }
        player.stop();
        activeRoom = null;
        privateTarget = null;
        nickname = null;
        privateEndpointRegistered = false;
    }

    public synchronized void joinCall(String roomName, String nickname) {
        if (roomName == null ||
                roomName.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del grupo no puede estar vacio.");
        }
    
        if (nickname == null ||
                nickname.trim().isEmpty()) {
            throw new IllegalArgumentException("El nickname no puede estar vacio.");
        }
    
        if (activeRoom != null) {
            throw new IllegalStateException(
                    "Ya existe una llamada grupal activa."
            );
        }

        ensureSocketStarted();
        this.activeRoom = roomName;
        this.nickname = nickname;

        try {
            sendGroupControlPacket(GROUP_REGISTER);
        } catch (IOException e) {
            stop();
            throw new IllegalStateException( "No se pudo iniciar el audio UDP.",e);
        }
    }

    public synchronized void startPrivateEndpoint(String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "El nickname no puede estar vacio."
            );
        }

        this.nickname = nickname;
        ensureSocketStarted();

        try {
            sendPrivateControlPacket(PRIVATE_REGISTER);
            privateEndpointRegistered = true;
        } catch (IOException e) {
            stop();
            throw new IllegalStateException(
                    "No se pudo registrar el audio privado.",
                    e
            );
        }
    }

    public synchronized void stopGroupCall() {
        if (socket == null || socket.isClosed() || activeRoom == null) {
            activeRoom = null;
            return;
        }

        try {
            sendGroupControlPacket(GROUP_UNREGISTER);
        } catch (IOException e) {
            System.err.println(
                    "[UDP CLIENT]: No se pudo salir de la sala de audio: " +
                    e.getMessage()
            );
        }

        activeRoom = null;
    }

    public synchronized void startPrivateTransmission(
            String targetNickname
    ) {
        if (activeRoom != null) {
            throw new IllegalStateException(
                    "No puedes iniciar audio privado durante una llamada grupal."
            );
        }
        if (!privateEndpointRegistered) {
            throw new IllegalStateException(
                    "El endpoint de audio privado no esta registrado."
            );
        }
        if (targetNickname == null || targetNickname.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "El destinatario no puede estar vacio."
            );
        }
        privateTarget = targetNickname;
    }

    public synchronized void stopPrivateTransmission() {
        privateTarget = null;
    }

    public void sendPrivateAudioChunk(byte[] audioData) {
        if (!listening ||
                socket == null ||
                socket.isClosed() ||
                nickname == null ||
                privateTarget == null ||
                audioData == null ||
                audioData.length == 0) {
            return;
        }

        try {
            byte[] payload = createPrivatePayload(
                    AUDIO_PRIVATE,
                    nickname,
                    privateTarget,
                    audioData
            );

            sendPayload(payload);
        } catch (IOException e) {
            if (listening) {
                System.err.println(
                        "[UDP CLIENT]: Error enviando audio privado: " +
                        e.getMessage()
                );
            }
        }
    }

    private synchronized void ensureSocketStarted() {
        if (listening) {
            return;
        }

        try {
            socket = new DatagramSocket();
            listening = true;
            player.start();
            startListener();
        } catch (IOException e) {
            stop();
            throw new IllegalStateException(
                    "No se pudo iniciar el socket UDP.",
                    e
            );
        }
    }

    private void startListener() {
        Thread listenerThread = new Thread(() -> {
            byte[] buffer = new byte[4096];
    
            while (listening) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
    
                    socket.receive(packet);
    
                    playReceivedPacket(packet);
    
                } catch (IOException e) {
                    if (listening) {
                        System.err.println("[UDP CLIENT]: Error recibiendo audio: " +e.getMessage());
                    }
                }
            }
        }, "UdpAudioClientListener");
    
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void playReceivedPacket(DatagramPacket packet) throws IOException {
    
        try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(packet.getData(),0,packet.getLength()))) {
    
            byte packetType = data.readByte();

            if (packetType == AUDIO_GROUP) {
                String roomName = data.readUTF();
                data.readUTF();
                int audioLength = readAudioLength(data);

                if (activeRoom == null ||
                        !roomName.equals(activeRoom)) {
                    return;
                }

                byte[] audioData = new byte[audioLength];
                data.readFully(audioData);
                player.playChunk(audioData);
                return;
            }

            if (packetType != AUDIO_PRIVATE) {
                return;
            }

            data.readUTF();
            String targetNickname = data.readUTF();
            int audioLength = readAudioLength(data);

            if (!nickname.equals(targetNickname)) {
                return;
            }
    
            byte[] audioData = new byte[audioLength];
            data.readFully(audioData);
            player.playChunk(audioData);
        }
    }

    private void sendGroupControlPacket(byte packetType) throws IOException {
        if (socket == null ||
                socket.isClosed() ||
                activeRoom == null ||
                nickname == null) {
            return;
        }

        sendPayload(createGroupPayload(
                packetType,
                activeRoom,
                nickname,
                new byte[0]
        ));
    }

    private void sendPrivateControlPacket(byte packetType)
            throws IOException {
        if (socket == null ||
                socket.isClosed() ||
                nickname == null) {
            return;
        }

        sendPayload(createPrivatePayload(
                packetType,
                nickname,
                "",
                new byte[0]
        ));
    }

    private void sendPayload(byte[] payload) throws IOException {
        InetAddress serverAddress = InetAddress.getByName(serverHost);
        DatagramPacket packet = new DatagramPacket(
                payload,
                payload.length,
                serverAddress,
                serverPort
        );
        socket.send(packet);
    }

    private int readAudioLength(DataInputStream data)
            throws IOException {
        int audioLength = data.readInt();
        if (audioLength < 0 || audioLength > 1200) {
            throw new IOException("Tamano de audio invalido.");
        }
        return audioLength;
    }

    private byte[] createGroupPayload(
            byte packetType,
            String roomName,
            String nickname,
            byte[] audioData
    ) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(out)) {
            data.writeByte(packetType);
            data.writeUTF(roomName);
            data.writeUTF(nickname);
            data.writeInt(audioData.length);
            data.write(audioData);
        }
        return out.toByteArray();
    }

    private byte[] createPrivatePayload(
            byte packetType,
            String senderNickname,
            String targetNickname,
            byte[] audioData
    ) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(out)) {
            data.writeByte(packetType);
            data.writeUTF(senderNickname);
            data.writeUTF(targetNickname);
            data.writeInt(audioData.length);
            data.write(audioData);
        }
        return out.toByteArray();
    }
}