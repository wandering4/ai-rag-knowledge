package com.sankuai.xzf.dev.tech.test;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.PathResource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class JGitTest {

    @Resource
    private OllamaChatClient ollamaChatClient;

    @Resource
    private TokenTextSplitter tokenTextSplitter;

    @Resource
    private SimpleVectorStore simpleVectorStore;

    @Resource
    private PgVectorStore pgVectorStore;

    private static final String repoUrl="https://gitcode.net/KnowledgePlanet/ai-rag-knowledge.git";
    private static final String username="2301_76632762";
    private static final String password="";
    private static final String localPath="./cloned-repo";

    @Test
    public void gitPullTest() throws IOException, GitAPIException {
        log.info("克隆路径:{}",new File(localPath).getAbsoluteFile());

        FileUtils.deleteDirectory(new File(localPath));

        Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(new File(localPath))
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
                .call();
        git.close();
    }


    @Test
    public void uploadFileTest() throws IOException, GitAPIException {

        String projectName = extractProjectName(repoUrl);

        Files.walkFileTree(Paths.get(localPath),new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
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
                    documentsSplitterList.forEach(document -> {
                        log.info("切割后文档内容:{}", document.getContent());
                        document.getMetadata().put("knowledge", projectName);
                    });

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

    }

    private String extractProjectName(String repoUrl){
        String[] parts = repoUrl.split("/");
        String projectNameWithGit = parts[parts.length - 1];
        return projectNameWithGit.replace(".git", "");
    }
}
