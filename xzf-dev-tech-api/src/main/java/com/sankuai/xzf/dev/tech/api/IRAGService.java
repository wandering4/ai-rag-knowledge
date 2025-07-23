package com.sankuai.xzf.dev.tech.api;

import com.sankuai.xzf.dev.tech.api.response.Response;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IRAGService {
    /**
     * 查询所有ragTag
     * @return
     */
    Response<List<String>> queryRagTagList();

    /**
     * 上传文件
     * @param ragTag
     * @param files
     * @return
     */
    Response<String> uploadFile(String ragTag, List<MultipartFile> files);
}
