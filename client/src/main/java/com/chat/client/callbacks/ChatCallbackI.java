package com.chat.client.callbacks;

import com.chat.slice.ChatCallback;
import com.chat.slice.FileChunk;
import com.zeroc.Ice.Current;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatCallbackI implements ChatCallback {

    // Mapa para reconstruir fragmentos por cada fileId
    private final Map<String, Map<Integer, byte[]>> pendingFiles = new ConcurrentHashMap<>();

    @Override
    public void notifyUserConnected(String nickname, Current current) {
        System.out.println("\n[PRESENCIA]: El usuario '" + nickname + "' se ha conectado.");
    }

    @Override
    public void notifyUserDisconnected(String nickname, Current current) {
        System.out.println("\n[PRESENCIA]: El usuario '" + nickname + "' se ha desconectado.");
    }

    @Override
    public void receivePrivateMessage(String senderNickname, String message, Current current) {
        System.out.println("\n[PRIVADO de " + senderNickname + "]: " + message);
    }

    @Override
    public void receiveGroupMessage(String roomName, String senderNickname, String message, Current current) {
        System.out.println("\n[GRUPO " + roomName + " - " + senderNickname + "]: " + message);
    }

    // RF-04: Recepcion y ensamble de fragmentos multimedia
    @Override
    public void receiveFileChunk(String senderNickname, FileChunk chunk, Current current) {
        pendingFiles.putIfAbsent(chunk.fileId, new ConcurrentHashMap<>());
        Map<Integer, byte[]> chunksMap = pendingFiles.get(chunk.fileId);
        chunksMap.put(chunk.chunkIndex, chunk.data);

        if (chunksMap.size() == chunk.totalChunks) {
            assembleAndSaveFile(senderNickname, chunk, chunksMap);
            pendingFiles.remove(chunk.fileId);
        }
    }

    private void assembleAndSaveFile(String senderNickname, FileChunk chunk, Map<Integer, byte[]> chunksMap) {
        File downloadDir = new File("downloads");
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }

        File outputFile = new File(downloadDir, chunk.filename);

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            for (int i = 0; i < chunk.totalChunks; i++) {
                byte[] data = chunksMap.get(i);
                if (data != null) {
                    fos.write(data);
                }
            }
            String origin = chunk.isGroup ? "el grupo '" + chunk.target + "'" : "el usuario '" + senderNickname + "'";
            System.out.println("\n[MULTIMEDIA]: Archivo '" + chunk.filename + "' recibido de " + origin + " y guardado en 'downloads/" + chunk.filename + "'.");
        } catch (IOException e) {
            System.err.println("\n[ERROR]: Error al guardar el archivo reconstruido: " + e.getMessage());
        }
    }

    public interface CallEventListener {
        void onIncomingCall(String callerNickname);
        void onCallAccepted(String calleeNickname);
        void onCallRejected(String calleeNickname, String reason);
        void onCallEnded(String partnerNickname);
    }

    private CallEventListener callEventListener;

    public void setCallEventListener(CallEventListener listener) {
        this.callEventListener = listener;
    }

    @Override
    public void notifyIncomingCall(String callerNickname, Current current) {
        System.out.println("\n--------------------------------------------------");
        System.out.println(">>> [LLAMADA ENTRANTE]: '" + callerNickname + "' te esta llamando.");
        System.out.println(">>> Para contestar: /call-accept " + callerNickname);
        System.out.println(">>> Para rechazar:  /call-reject " + callerNickname);
        System.out.println("--------------------------------------------------");
        System.out.print("> ");
        if (callEventListener != null) {
            callEventListener.onIncomingCall(callerNickname);
        }
    }

    @Override
    public void notifyCallAccepted(String calleeNickname, Current current) {
        System.out.println("\n--------------------------------------------------");
        System.out.println(">>> [LLAMADA CONECTADA]: '" + calleeNickname + "' acepto la llamada.");
        System.out.println(">>> Microfono activado. Habla ahora.");
        System.out.println(">>> Para silenciarte: /mute | Para colgar: /hangup");
        System.out.println("--------------------------------------------------");
        System.out.print("> ");
        if (callEventListener != null) {
            callEventListener.onCallAccepted(calleeNickname);
        }
    }

    @Override
    public void notifyCallRejected(String calleeNickname, String reason, Current current) {
        System.out.println("\n--------------------------------------------------");
        System.out.println(">>> [LLAMADA RECHAZADA]: '" + calleeNickname + "' rechazo la llamada (" + reason + ").");
        System.out.println("--------------------------------------------------");
        System.out.print("> ");
        if (callEventListener != null) {
            callEventListener.onCallRejected(calleeNickname, reason);
        }
    }

    @Override
    public void notifyCallEnded(String partnerNickname, Current current) {
        System.out.println("\n--------------------------------------------------");
        System.out.println(">>> [LLAMADA FINALIZADA]: La llamada con '" + partnerNickname + "' ha terminado.");
        System.out.println("--------------------------------------------------");
        System.out.print("> ");
        if (callEventListener != null) {
            callEventListener.onCallEnded(partnerNickname);
        }
    }

    @Override
    public void notifyAudioCallJoined(String roomName, String nickname, Current current) {
        System.out.println(
                "\n[AUDIO]: '" + nickname +
                "' entro a la llamada del grupo '" +
                roomName + "'."
        );
    }

    @Override
    public void notifyAudioCallLeft(String roomName, String nickname, Current current) {
        System.out.println(
                "\n[AUDIO]: '" + nickname +
                "' salio de la llamada del grupo '" +
                roomName + "'."
        );
    }

    @Override
    public void notifyAudioCallEnded(String roomName, Current current) {
        System.out.println(
                "\n[AUDIO]: La llamada del grupo '" +
                roomName +
                "' termino porque quedo vacia."
        );
    }
}