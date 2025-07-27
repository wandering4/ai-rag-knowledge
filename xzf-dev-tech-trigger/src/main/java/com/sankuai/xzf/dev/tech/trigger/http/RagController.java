package com.sankuai.xzf.dev.tech.trigger.http;

import com.sankuai.xzf.dev.tech.api.IRAGService;
import com.sankuai.xzf.dev.tech.api.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.core.io.PathResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/rag/")
public class RagController implements IRAGService {

    @Resource
    private OllamaChatClient ollamaChatClient;

    @Resource
    private TokenTextSplitter tokenTextSplitter;

    @Resource
    private SimpleVectorStore simpleVectorStore;

    @Resource
    private PgVectorStore pgVectorStore;

    @Resource
    private RedissonClient redissonClient;

    private static final String localPath="./cloned-repo/";


    @RequestMapping(value = "query_rag_tag_list", method = RequestMethod.GET)
    @Override
    public Response<List<String>> queryRagTagList() {
        RList<String> elements = redissonClient.getList("ragTag");
        return Response.success(elements);
    }

    @RequestMapping(value = "file/upload", method = RequestMethod.POST, headers = "content-type=multipart/form-data")
    @Override
    public Response<String> uploadFile(@RequestParam String ragTag, @RequestParam("file") List<MultipartFile> files) {
        log.info("上传知识库：{}", ragTag);
        for (MultipartFile file : files) {
            TikaDocumentReader reader = new TikaDocumentReader(file.getResource());
            List<Document> documents = reader.get();
            List<Document> documentSplitterList = tokenTextSplitter.apply(documents);
            documents.forEach(document -> {
                document.getMetadata().put("knowledge", ragTag);
            });
            documentSplitterList.forEach((documentSplitter) -> documentSplitter.getMetadata().put("knowledge", ragTag));
            pgVectorStore.accept(documentSplitterList);
            RList<String> elements = redissonClient.getList("ragTag");
            if (!elements.contains(ragTag)) {
                elements.add(ragTag);
            }
        }
        log.info("知识库上传完成:{}", ragTag);
        return Response.success();
    }


    @RequestMapping(value = "analyze_git_repository", method = RequestMethod.POST)
    @Override
    public Response<String> analyzeGitRepository(String repoUrl, String username, String token) throws Exception{
        //todo:判断repoUrl合法
        String projectName = extractProjectName(repoUrl);
        //uuid生成一个路径避免影响
        String path=localPath+ UUID.randomUUID();
        log.info("克隆路径:{}",new File(path).getAbsoluteFile());

        //预清理文件
        FileUtils.deleteDirectory(new File(path));

        //克隆仓库代码
        Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(new File(path))
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, token))
                .call();

        //遍历所有文件切割上传
        Files.walkFileTree(Paths.get(path),new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                //todo:给文件类型加一个白名单或者黑名单,比如.pack文件无法被解析就加入黑名单

                log.info("{}遍历解析路径,上传知识库:{}",projectName,file.getFileName());

                try {
                    TikaDocumentReader reader = new TikaDocumentReader(new PathResource(file));

                    //切割文件
                    List<Document> documents = reader.get();
                    List<Document> documentsSplitterList = tokenTextSplitter.apply(documents);

                    //数据去重和打标
                    documents = documents.stream()
                            .filter(doc -> doc.getContent() != null && !doc.getContent().isEmpty())
                            .collect(Collectors.toMap(
                                    Document::getContent,  // 以内容作为去重依据
                                    doc -> {
                                        doc.getMetadata().put("knowledge", "知识库名称");  // 在去重时直接修改
                                        return doc;
                                    },
                                    (existing, replacement) -> existing  // 保留第一个出现的文档
                            ))
                            .values()
                            .stream()
                            .toList();


                    //打标
                    documentsSplitterList.forEach(document -> document.getMetadata().put("knowledge", projectName));

                    //存储
                    pgVectorStore.accept(documentsSplitterList);
                }catch (Exception e){
                    log.error("遍历解析路径，上传知识库失败:{}",file.getFileName(),e);
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                log.info("Failed to access file:{} - {}", file.toString(), exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });

        //清理下载文件
        FileUtils.deleteDirectory(new File(path));

        //添加ragTag
        RList<String> elements = redissonClient.getList("ragTag");
        if (!elements.contains(projectName)) {
            elements.add(projectName);
        }

        git.close();

        log.info("遍历解析路径上传完成:{}",repoUrl);

        return Response.success();
    }

    //获取项目名称
    private String extractProjectName(String repoUrl){
        String[] parts = repoUrl.split("/");
        String projectNameWithGit = parts[parts.length - 1];
        return projectNameWithGit.replace(".git", "");
    }
}
