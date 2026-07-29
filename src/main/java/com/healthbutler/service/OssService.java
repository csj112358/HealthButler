package com.healthbutler.service;

import org.springframework.web.multipart.MultipartFile;

public interface OssService {

    /**
     * 上传文件到OSS
     * @param file 文件
     * @param directory 目录
     * @return 文件访问URL
     */
    String uploadFile(MultipartFile file, String directory);

    /**
     * 删除OSS文件
     * @param fileUrl 文件URL
     */
    void deleteFile(String fileUrl);
}