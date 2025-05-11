package com.vaultx.user.context.service.file;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final MinioClient minioClient;

    @Value("${vaultx.files.bucket-name:vaultx-files}")
    private String bucketName;

    public void saveEncryptedFile(UUID fileId, byte[] data) throws IOException {
        try {
            // Ensure bucket exists
            boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!bucketExists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }

            // Upload file
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(fileId.toString())
                                .stream(inputStream, data.length, -1)
                                .contentType("application/octet-stream")
                                .build());
            }
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException
                 | InvalidResponseException | NoSuchAlgorithmException | ServerException | XmlParserException e) {
            throw new IOException("Failed to store file in MinIO: " + e.getMessage(), e);
        }
    }

    public Resource loadAsResource(UUID fileId) throws IOException {
        try {
            // Check if file exists
            minioClient.statObject(StatObjectArgs.builder().bucket(bucketName).object(fileId.toString()).build());

            // Download file
            GetObjectResponse response = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileId.toString())
                            .build());

            byte[] data = response.readAllBytes();
            return new ByteArrayResource(data);
        } catch (ErrorResponseException e) {
            if (e.errorResponse().code().equals("NoSuchKey")) {
                throw new FileNotFoundException("File not found: " + fileId);
            }
            throw new IOException("Failed to load file from MinIO: " + e.getMessage(), e);
        } catch (InsufficientDataException | InternalException | InvalidKeyException | InvalidResponseException
                 | NoSuchAlgorithmException | ServerException | XmlParserException e) {
            throw new IOException("Failed to load file from MinIO: " + e.getMessage(), e);
        }
    }
}