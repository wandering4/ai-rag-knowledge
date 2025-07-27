package com.sankuai.xzf.dev.tech.test;

import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RAGTest {

    @Resource
    private OllamaChatClient ollamaChatClient;

    @Resource
    private TokenTextSplitter tokenTextSplitter;

    @Resource
    private SimpleVectorStore simpleVectorStore;

    @Resource
    private PgVectorStore pgVectorStore;


    @Test
    public void uploadTest() throws IOException {
        String path= "data/file.txt";
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new FileNotFoundException("文件未找到: " + resource.getPath());
        }

        TikaDocumentReader reader = new TikaDocumentReader(path);

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
            log.info("分割后文档内容: {}", document.getContent());
            document.getMetadata().put("knowledge", "知识库名称");
        });

        //存储
        pgVectorStore.accept(documentsSplitterList);

        log.info("上传完成");
    }

    @Test
    public void chatTest(){

        String message = "意大利面应该拌什么?";

        String SYSTEM_PROMPT = """
                Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
                If unsure, simply state that you don't know.
                Another thing you need to note is that your reply must be in Chinese!
                DOCUMENTS:
                    {documents}
                """;

        SearchRequest request = SearchRequest.query(message).withTopK(5).withFilterExpression("knowledge == '知识库名称'").withSimilarityThreshold(0.8);

        List<Document> documents = pgVectorStore.similaritySearch(request);
        String documentsCollectors = documents.stream().map(Document::getContent).collect(Collectors.joining("、"));

        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT).createMessage(Map.of("documents", documentsCollectors));

        ArrayList<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(message));
        messages.add(ragMessage);

        log.info("发送给大模型消息: {}", JSON.toJSONString(messages));

        ChatResponse chatResponse = ollamaChatClient.call(new Prompt(messages, OllamaOptions.create().withModel("deepseek-r1:1.5b")));

        log.info("测试结果:{}", JSON.toJSONString(chatResponse));

    }


}
