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

    /**
     * 解析git仓库代码
     * @param repoUrl
     * @param username
     * @param token
     * @return
     */
    Response<String> analyzeGitRepository(String repoUrl, String username, String token) throws Exception;
}
