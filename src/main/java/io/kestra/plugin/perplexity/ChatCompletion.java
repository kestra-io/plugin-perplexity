package io.kestra.plugin.perplexity;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuperBuilder
@Getter
@NoArgsConstructor
@ToString
@EqualsAndHashCode
@Plugin(
    examples = {
        @Example(
            title = "Simple Perplexity chat",
            full = true,
            code = """
                id: perplexity_chat
                namespace: company.name

                tasks:
                  - id: chat_completion
                    type: io.kestra.plugin.perplexity.ChatCompletion
                    apiKey: '{{ secret("PERPLEXITY_API_KEY") }}'
                    model: sonar
                    messages:
                      - type: USER
                        content: "What is Kestra?"
                    temperature: 0.7
                """
        )
    }
)
public class ChatCompletion extends Task implements RunnableTask<ChatCompletion.Output> {
    private static final String API_URL = "https://api.perplexity.ai/chat/completions";

    @Schema(
        title = "API Key",
        description = "The Perplexity API key used for authentication."
    )
    @NotNull
    private Property<String> apiKey;

    @Schema(
        title = "Model",
        description = "The Perplexity model to use (e.g., `sonar`, `sonar-pro`)."
    )
    @NotNull
    private Property<String> model;

    @Schema(
        title = "Messages",
        description = "List of chat messages in conversational order."
    )
    @NotNull
    private Property<List<ChatMessage>> messages;

    @Builder.Default
    @Schema(
        title = "Temperature",
        description = "The amount of randomness in the response, valued between 0 and 2."
    )
    private Property<Double> temperature = Property.ofValue(0.2);

    @Schema(
        title = "Top P",
        description = "The nucleus sampling threshold, valued between 0 and 1."
    )
    @Builder.Default
    private Property<@Max(1) Double> topP = Property.ofValue(0.9);

    @Schema(
        title = "Top K",
        description = "The number of tokens to keep for top-k filtering."
    )
    @Builder.Default
    private Property<Integer> topK = Property.ofValue(0);

    @Schema(
        title = "Stream",
        description = "Determines whether to stream the response incrementally."
    )
    @Builder.Default
    private Property<Boolean> stream = Property.ofValue(false);

    @Schema(
        title = "Presence Penalty",
        description = "Positive values increase the likelihood of discussing new topics. Valued between 0 and 2.0."
    )
    @Builder.Default
    private Property<@Max(2) Double> presencePenalty = Property.ofValue(0.0);

    @Schema(
        title = "Frequency Penalty",
        description = "Decreases likelihood of repetition based on prior frequency. Valued between 0 and 2.0."
    )
    @Builder.Default
    private Property<@Max(2) Double> frequencyPenalty = Property.ofValue(0.0);

    @Schema(
        title = "The maximum number of tokens to generate."
    )
    private Property<Integer> maxTokens;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var renderedApiKey = runContext.render(this.apiKey).as(String.class).orElseThrow();
        var renderedModel = runContext.render(this.model).as(String.class).orElseThrow();
        var renderedTemperature = runContext.render(this.temperature).as(Double.class).orElse(0.2);
        var renderedTopP = runContext.render(this.topP).as(Double.class).orElse(0.9);
        var renderedTopK = runContext.render(this.topK).as(Integer.class).orElse(50);
        var renderedStream = runContext.render(this.stream).as(Boolean.class).orElse(false);
        var renderedPresencePenalty = runContext.render(this.presencePenalty).as(Double.class).orElse(0.0);
        var renderedFrequencyPenalty = runContext.render(this.frequencyPenalty).as(Double.class).orElse(0.0);
        var renderedMaxTokens = runContext.render(this.maxTokens).as(Integer.class).orElse(null);

        var renderedMessages = runContext.render(this.messages)
            .asList(ChatMessage.class)
            .stream()
            .map(msg -> Map.of("role", msg.type().role(), "content", Objects.toString(msg.content(), "")))
            .toList();

        var requestBody = new HashMap<String, Object>();

        requestBody.put("model", renderedModel);
        requestBody.put("messages", renderedMessages);
        requestBody.put("temperature", renderedTemperature);
        requestBody.put("top_p", renderedTopP);
        requestBody.put("top_k", renderedTopK);
        requestBody.put("stream", renderedStream);
        requestBody.put("presence_penalty", renderedPresencePenalty);
        requestBody.put("frequency_penalty", renderedFrequencyPenalty);

        if (renderedMaxTokens != null) {
            requestBody.put("max_tokens", renderedMaxTokens);
        }

        try (var client = new HttpClient(runContext, HttpConfiguration.builder().build())) {
            var httpRequest = HttpRequest.builder()
                .uri(URI.create(API_URL))
                .method("POST")
                .addHeader("Authorization", "Bearer " + renderedApiKey)
                .addHeader("Content-Type", "application/json")
                .body(HttpRequest.JsonRequestBody.builder()
                    .content(requestBody)
                    .build())
                .build();

            HttpResponse<String> response = client.request(httpRequest, String.class);

            if (response.getStatus().getCode() >= 400) {
                throw new IOException("Perplexity API error: " + response.getBody());
            }

            var parsed = JacksonMapper.ofJson().readValue(response.getBody(), Map.class);

            var usage = (Map<String, Object>) parsed.get("usage");
            if (usage != null) {
                runContext.metric(Counter.of("usage.prompt.tokens", ((Number) usage.get("prompt_tokens")).longValue()));
                runContext.metric(Counter.of("usage.completion.tokens", ((Number) usage.get("completion_tokens")).longValue()));
                runContext.metric(Counter.of("usage.total.tokens", ((Number) usage.get("total_tokens")).longValue()));
            }

            var choices = (List<Map<String, Object>>) parsed.get("choices");
            var message = (Map<String, Object>) choices.getFirst().get("message");
            var content = (String) message.get("content");


            return Output.builder()
                .outputText(content)
                .rawResponse(response.getBody())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The generated text output"
        )
        private final String outputText;

        @Schema(title = "Full, raw response from the API.")
        private final String rawResponse;
    }

    public record ChatMessage(ChatMessageType type, String content) {}

    public enum ChatMessageType {
        SYSTEM("system"),
        ASSISTANT("assistant"),
        USER("user");

        private final String role;

        ChatMessageType(String role) {
            this.role = role;
        }

        public String role() {
            return role;
        }
    }
}