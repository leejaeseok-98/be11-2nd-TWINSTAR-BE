package com.TwinStar.TwinStar.common.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
public class S3Service {
    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    public S3Service(S3Client s3Client) {
        this.s3Client = s3Client;
    }


    //* MultipartFile을 받아서 S3에 업로드

    public String uploadFile(MultipartFile file, String fileName) {
        try {
            // 파일명이 중복되지 않도록 UUID 추가
            String uniqueFileName = "uploads/" + UUID.randomUUID() + "_" + fileName;

            // S3에 업로드
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(uniqueFileName)  // uniqueFileName 사용
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromBytes(file.getBytes())
            );

            return "https://" + bucketName + ".s3." + Region.AP_NORTHEAST_2.id() + ".amazonaws.com/" + uniqueFileName;
        } catch (IOException e) {
            throw new RuntimeException("S3 업로드 실패", e);
        }
    }


}
