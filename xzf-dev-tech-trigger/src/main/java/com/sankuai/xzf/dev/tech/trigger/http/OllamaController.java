package com.sankuai.xzf.dev.tech.trigger.http;

import com.sankuai.xzf.dev.tech.api.IAiService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/ollama/")
public class OllamaController implements IAiService {

    @Resource
    private OllamaChatClient chatClient;

    /**
     * http://localhost:8090/api/v1/ollama/generate?model=deepseek-r1:1.5b&message=你是谁
     * @param model
     * @param message
     * @return
     */
    @RequestMapping(value = "generate",method = RequestMethod.GET)
    @Override
    public ChatResponse generate(@RequestParam String model, @RequestParam String message) {
        return chatClient.call(new Prompt(message, OllamaOptions.create().withModel(model)));
    }

    /**
     * http://localhost:8090/api/v1/ollama/generateStream?model=deepseek-r1:1.5b&message=你是谁
     * @param model
     * @param message
     * @return
     */
    @RequestMapping(value = "generateStream",method = RequestMethod.GET)
    @Override
    public Flux<ChatResponse> generateStream(@RequestParam String model, @RequestParam String message) {
        return chatClient.stream(new Prompt(message, OllamaOptions.create().withModel(model)));
    }
}
