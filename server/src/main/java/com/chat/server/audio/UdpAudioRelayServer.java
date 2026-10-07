package com.chat.server.audio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UdpAudioRelayServer implements Runnable {

    private final int port;
    private final Map<String, InetSocketAddress> userUdpAddresses = new ConcurrentHashMap<>();
    private volatile boolean running = false;
    private DatagramSocket socket;

    public UdpAudioRelayServer(int port) {
        this.port = port;
    }

    public void start() {
        running = true;
        Thread serverThread = new Thread(this, "UdpAudioRelayServerThread");
        serverThread.setDaemon(true);
        serverThread.start();
        System.out.println(">>> Servidor UDP de Audio 1-a-1 iniciado en el puerto " + port);
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

                try (DataInputStream dis = new DataInputStream(
                        new ByteArrayInputStream(receivePacket.getData(), 0, receivePacket.getLength()))) {

                    String senderNickname = dis.readUTF();
                    String targetNickname = dis.readUTF();
                    int audioLength = dis.readInt();
                    byte[] audioData = new byte[audioLength];
                    if (audioLength > 0) {
                        dis.readFully(audioData);
                    }

                    // Registrar o actualizar la dirección UDP del remitente
                    if (!senderNickname.isEmpty()) {
                        userUdpAddresses.put(senderNickname, senderAddr);
                    }

                    // Si es solo paquete de registro inicial (audioLength == 0), no retransmitir
                    if (audioLength == 0) {
                        continue;
                    }

                    // Unicast al destinatario privado
                    InetSocketAddress targetAddr = userUdpAddresses.get(targetNickname);
                    if (targetAddr != null) {
                        byte[] forwardPayload = createPayload(senderNickname, targetNickname, audioData);
                        DatagramPacket sendPacket = new DatagramPacket(
                            forwardPayload, forwardPayload.length, targetAddr.getAddress(), targetAddr.getPort());
                        socket.send(sendPacket);
                    }

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