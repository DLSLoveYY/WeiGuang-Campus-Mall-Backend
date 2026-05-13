package top.dlsloveyy.backendtest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 火山方舟（豆包）大模型调用封装。
 * 单点出站，业务 Controller 只需传入 system + user 消息。
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    @Value("${ai.ark.base-url}")
    private String baseUrl;

    @Value("${ai.ark.api-key}")
    private String apiKey;

    @Value("${ai.ark.model}")
    private String model;

    @Value("${ai.ark.timeout-ms:30000}")
    private int timeoutMs;

    @Value("${ai.ark.max-tokens:800}")
    private int maxTokens;

    @Value("${ai.ark.temperature:0.6}")
    private double temperature;

    private HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 便捷入口：只传 system / user 文本即可。
     */
    public String chat(String systemPrompt, String userPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt));
        return chat(messages);
    }

    /**
     * 多轮对话入口：messages 中每个元素含 role / content。
     */
    public String chat(List<Map<String, String>> messages) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("AI API Key 未配置");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("temperature", temperature);
            body.put("max_tokens", maxTokens);

            String payload = mapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            String respBody = response.body();

            if (status < 200 || status >= 300) {
                log.warn("AI 调用失败 status={} body={}", status, respBody);
                throw new RuntimeException("AI 服务调用失败: HTTP " + status);
            }

            JsonNode root = mapper.readTree(respBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                log.warn("AI 返回缺少 choices: {}", respBody);
                throw new RuntimeException("AI 返回为空");
            }
            String content = choices.get(0).path("message").path("content").asText("");
            return content == null ? "" : content.trim();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 调用异常", e);
            throw new RuntimeException("AI 服务异常: " + e.getMessage(), e);
        }
    }
}
