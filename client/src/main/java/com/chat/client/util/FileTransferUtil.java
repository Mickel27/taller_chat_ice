package com.chat.client.util;

import com.chat.slice.ChatServicePrx;
import com.chat.slice.FileChunk;
import com.chat.slice.RoomMembershipException;
import com.chat.slice.RoomNotFoundException;
import com.chat.slice.UserNotFoundException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.UUID;

public class FileTransferUtil {

    private static final int CHUNK_SIZE = 32 * 1024; // 32 KB

    public static void sendFile(ChatServicePrx server, String sender, String target, String filePath, boolean isGroup) {
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            System.err.println("[ERROR]: El archivo no existe o la ruta es invalida: " + filePath);
            return;
        }

        long totalSize = file.length();
        int totalChunks = (int) Math.ceil((double) totalSize / CHUNK_SIZE);
        if (totalChunks == 0) totalChunks = 1;

        String fileId = UUID.randomUUID().toString();
        String filename = file.getName();

        System.out.printf("[SISTEMA]: Iniciando envio de '%s' (%d bytes, %d fragmentos)...\n", filename, totalSize, totalChunks);

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            int chunkIndex = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] chunkData = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunkData, 0, bytesRead);

                FileChunk chunk = new FileChunk(
                    fileId,
                    filename,
                    totalSize,
                    chunkIndex,
                    totalChunks,
                    chunkData,
                    target,
                    isGroup
                );

                server.sendFileChunk(sender, chunk);
                chunkIndex++;
            }

            System.out.println("[SISTEMA]: Archivo enviado exitosamente.");
        } catch (UserNotFoundException e) {
            System.err.println("[ERROR]: " + e.reason);
        } catch (RoomNotFoundException e) {
            System.err.println("[ERROR]: " + e.reason);
        } catch (RoomMembershipException e) {
            System.err.println("[ERROR]: " + e.reason);
        } catch (IOException e) {
            System.err.println("[ERROR]: Fallo de lectura del archivo local: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[ERROR]: Error durante la transferencia: " + e.getMessage());
        }
    }
}