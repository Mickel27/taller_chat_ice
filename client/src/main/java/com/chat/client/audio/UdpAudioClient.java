package com.chat.client.audio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UdpAudioClient {

    private final String serverHost;
    private final int serverPort;
    private final AudioPlayer player;
    private DatagramSocket socket;
    private volatile boolean listening = false;

    public UdpAudioClient(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.player = new AudioPlayer();
    }

    public void registerAndStart(String nickname) {
        if (listening) return;

        try {
            socket = new DatagramSocket();
            player.start();
            listening = true;

            // Ping de registro inicial al servidor UDP
            sendAudioChunk(nickname, "", new byte[0]);

            Thread listenerThread = new Thread(() -> {
                byte[] buffer = new byte[4096];
                while (listening) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);

                        try (DataInputStream dis = new DataInputStream(
                                new ByteArrayInputStream(packet.getData(), 0, packet.getLength()))) {

                            String senderNickname = dis.readUTF();
                            String targetNickname = dis.readUTF();
                            int audioLength = dis.readInt();
                            byte[] audioData = new byte[audioLength];
                            dis.readFully(audioData);

                            player.playChunk(audioData);
                        }
                    } catch (Exception e) {
                        if (listening) {
                            System.err.println("[UDP CLIENT ERROR]: " + e.getMessage());
                        }
                    }
                }
            }, "UdpAudioClientListener");
            listenerThread.setDaemon(true);
            listenerThread.start();

        } catch (Exception e) {
            System.err.println("[UDP CLIENT]: Error al iniciar socket UDP: " + e.getMessage());
        }
    }

    public void sendAudioChunk(String senderNickname, String targetNickname, byte[] audioData) {
        if (socket == null || socket.isClosed()) return;

        try {
            byte[] payload = createPayload(senderNickname, targetNickname, audioData);
            InetAddress serverAddr = InetAddress.getByName(serverHost);
            DatagramPacket packet = new DatagramPacket(payload, payload.length, serverAddr, serverPort);
            socket.send(packet);
        } catch (Exception e) {
            System.err.println("[UDP CLIENT]: Error enviando fragmento de audio: " + e.getMessage());
        }
    }

    public void stop() {
        listening = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        player.stop();
    }

    private byte[] createPayload(String senderNickname, String targetNickname, byte[] audioData) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeUTF(senderNickname);
        dos.writeUTF(targetNickname);
        dos.writeInt(audioData.length);
        dos.write(audioData);
        dos.flush();
        return baos.toByteArray();
    }
}