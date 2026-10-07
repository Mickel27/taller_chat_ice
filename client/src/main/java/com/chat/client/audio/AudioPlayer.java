package com.chat.client.audio;

import javax.sound.sampled.*;

public class AudioPlayer {

    private SourceDataLine line;
    private volatile boolean isPlaying = false;

    public void start() {
        if (isPlaying) return;

        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, AudioRecorder.FORMAT);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(AudioRecorder.FORMAT);
            line.start();
            isPlaying = true;
        } catch (LineUnavailableException e) {
            System.err.println("[AUDIO]: Error al inicializar reproductor: " + e.getMessage());
        }
    }

    public void playChunk(byte[] chunk) {
        if (isPlaying && line != null) {
            line.write(chunk, 0, chunk.length);
        }
    }

    public void stop() {
        isPlaying = false;
        if (line != null) {
            line.drain();
            line.stop();
            line.close();
        }
    }

    public boolean isPlaying() {
        return isPlaying;
    }
}