package com.fss.backend.file;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileStorage {
    String save(MultipartFile file) throws IOException;
    String saveBytes(String originalFilename, byte[] bytes) throws IOException;
    void delete(String relativePath) throws IOException;
}
