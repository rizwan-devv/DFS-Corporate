package com.dfs.corporate.service;

import com.dfs.corporate.web.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path root;

    public FileStorageService(@Value("${app.upload-dir}") String uploadDir) throws IOException {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    public String store(Long partyId, String documentCode, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File is required");
        }
        try {
            return storeBytes(partyId, documentCode, file.getBytes(), extension(file.getOriginalFilename()));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        }
    }

    public String storeBytes(Long partyId, String documentCode, byte[] bytes, String extension) {
        if (bytes == null || bytes.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "File is required");
        }
        try {
            Path dir = root.resolve("party-" + partyId);
            Files.createDirectories(dir);
            String ext = extension == null || extension.isBlank()
                    ? ""
                    : (extension.startsWith(".") ? extension : "." + extension);
            String filename = documentCode + "-" + UUID.randomUUID() + ext;
            Path target = dir.resolve(filename);
            Files.write(target, bytes);
            return target.toString();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        }
    }

    public Path resolve(String storedPath) {
        Path path = Path.of(storedPath).toAbsolutePath().normalize();
        if (!path.startsWith(root) && !Files.exists(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "File not found");
        }
        if (!Files.exists(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "File not found");
        }
        return path;
    }

    private String extension(String name) {
        if (name == null || !name.contains(".")) return "";
        return name.substring(name.lastIndexOf('.'));
    }
}
