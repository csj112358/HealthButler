package com.healthbutler.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.healthbutler.service.OssService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class OssServiceImpl implements OssService {

    @Autowired
    private OSS ossClient;

    @Value("${aliyun.oss.bucket-name}")
    private String bucketName;

    @Value("${aliyun.oss.endpoint}")
    private String endpoint;

    @Override
    public String uploadFile(MultipartFile file, String directory) {
        try {
            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            // 按日期组织目录结构
            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String fileName = directory + "/" + datePath + "/" + UUID.randomUUID().toString() + extension;

            // 设置文件元数据
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(file.getContentType());
            metadata.setContentLength(file.getSize());

            // 上传文件
            InputStream inputStream = file.getInputStream();
            ossClient.putObject(bucketName, fileName, inputStream, metadata);
            inputStream.close();

            // 返回文件访问URL
            return "https://" + bucketName + "." + endpoint + "/" + fileName;
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @Override
    public void deleteFile(String fileUrl) {
        try {
            // 从URL中提取文件key
            String baseUrl = "https://" + bucketName + "." + endpoint + "/";
            if (fileUrl.startsWith(baseUrl)) {
                String fileKey = fileUrl.substring(baseUrl.length());
                ossClient.deleteObject(bucketName, fileKey);
            }
        } catch (Exception e) {
            throw new RuntimeException("文件删除失败", e);
        }
    }
}