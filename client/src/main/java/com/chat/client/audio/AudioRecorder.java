package com.chat.client.audio;

import javax.sound.sampled.*;
import java.util.function.Consumer;

public class AudioRecorder {

    public static final AudioFormat FORMAT = new AudioFormat(16000.0f, 16, 1, true, false);
    private TargetDataLine line;
    private volatile boolean isRecording = false;

    public void startRecording(Consumer<byte[]> chunkConsumer) {
        if (isRecording) return;

        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                System.err.println("[AUDIO]: Formato de microfono no soportado.");
                return;
            }

            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(FORMAT);
            line.start();
            isRecording = true;

            new Thread(() -> {
                byte[] buffer = new byte[1024]; // Bloques de 1KB
                while (isRecording) {
                    int bytesRead = line.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        byte[] chunk = new byte[bytesRead];
                        System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                        chunkConsumer.accept(chunk);
                    }
                }
            }).start();

        } catch (LineUnavailableException e) {
            System.err.println("[AUDIO]: Error al acceder al microfono: " + e.getMessage());
        }
    }

    public void stopRecording() {
        isRecording = false;
        if (line != null) {
            line.stop();
            line.close();
        }
    }

    public boolean isRecording() {
        return isRecording;
    }
}