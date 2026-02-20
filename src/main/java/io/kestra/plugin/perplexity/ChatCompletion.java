package io.kestra.plugin.perplexity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

@SuperBuilder
@Getter
@NoArgsConstructor
@ToString
@EqualsAndHashCode
@Plugin(
    examples = {
        @Example(
            title = "Ask a question to Perplexity",
            full = true,
            code = """
                id: perplexity_chat
                namespace: company.team

                tasks:
                  - id: ask_ai
                    type: io.kestra.plugin.perplexity.ChatCompletion
                    apiKey: '{{ secret("PERPLEXITY_API_KEY") }}'
                    model: sonar
                    messages:
                      - type: USER
                        content: "What is Kestra?"
                    temperature: 0.7
                """
        ),
        @Example(
            title = "Perplexity chat with Structured Output (JSON Schema)",
            full = true,
            code = """
                id: perplexity_structured
                namespace: company.name

                tasks:
                  - id: chat_completion_structured
                    type: io.kestra.plugin.perplexity.ChatCompletion
                    apiKey: '{{ secret("PERPLEXITY_API_KEY") }}'
                    model: sonar
                    messages:
                      - type: USER
                        content: "Make a JSON todo from this casual note: schedule team check-in next week; tags: work, planning;"
                    jsonResponseSchema: |
                      {
                        "type": "object",
                        "additionalProperties": false,
                        "required": ["title", "done", "tags"],
                        "properties": {
                          "title": { "type": "string" },
                          "done":  { "type": "boolean" },
                          "tags":  { "type": "array", "items": { "type": "string" } },
                          "notes": { "type": "string" }
                        }
                      }
                """
        ),
        @Example(
            title = "System prompt with length and repetition controls",
            full = true,
            code = """
                id: perplexity_guarded
                namespace: company.team

                tasks:
                  - id: chat_completion_guarded
                    type: io.kestra.plugin.perplexity.ChatCompletion
                    apiKey: '{{ secret("PERPLEXITY_API_KEY") }}'
                    model: sonar-pro
                    messages:
                      - type: SYSTEM
                        content: "You are a brief release-notes generator."
                      - type: USER
                        content: "Summarize changes: added metrics; fixed retry bug; improved docs."
                    maxTokens: 120
                    presencePenalty: 0.8
                    frequencyPenalty: 0.6
                """
        )
    }
)
@Schema(
    title = "Send chat completion to Perplexity",
    description = "Calls Perplexity `/chat/completions` with rendered messages and returns the first choice. Requires an API key bearer token; defaults: temperature 0.2, top_p 0.9, top_k 0, stream false, penalties 0.0. Supports optional JSON Schema structured output via `response_format`."
)
public class ChatCompletion extends Task implements RunnableTask<ChatCompletion.Output> {
    private static final String API_URL = "https://api.perplexity.ai/chat/completions";

    @Schema(
        title = "Perplexity API key",
        description = "Bearer token sent in the Authorization header; store as a secret."
    )
    @NotNull
    private Property<String> apiKey;

    @Schema(
        title = "Model name",
        description = "Model identifier accepted by Perplexity (for example `sonar`, `sonar-pro`)."
    )
    @NotNull
    private Property<String> model;

    @Schema(
        title = "Conversation messages",
        description = "Ordered chat turns; roles are mapped to `system`, `assistant`, or `user` before sending."
    )
    @NotNull
    private Property<List<ChatMessage>> messages;

    @Builder.Default
    @Schema(
        title = "Temperature",
        description = "Randomness factor between 0 and 2; default 0.2."
    )
    private Property<Double> temperature = Property.ofValue(0.2);

    @Schema(
        title = "Top P",
        description = "Nucleus sampling probability cap (0–1); default 0.9."
    )
    @Builder.Default
    private Property<@Max(1) Double> topP = Property.ofValue(0.9);

    @Schema(
        title = "Top K",
        description = "Top-k filter size; `0` keeps all tokens (default)."
    )
    @Builder.Default
    private Property<Integer> topK = Property.ofValue(0);

    @Schema(
        title = "Stream",
        description = "Passes `stream` to the API; keep false (default) because the task reads only the final response body."
    )
    @Builder.Default
    private Property<Boolean> stream = Property.ofValue(false);

    @Schema(
        title = "Presence Penalty",
        description = "Penalty for repeating topics; range 0–2; default 0.0."
    )
    @Builder.Default
    private Property<@Max(2) Double> presencePenalty = Property.ofValue(0.0);

    @Schema(
        title = "Frequency Penalty",
        description = "Penalty based on token frequency; range 0–2; default 0.0."
    )
    @Builder.Default
    private Property<@Max(2) Double> frequencyPenalty = Property.ofValue(0.0);

    @Schema(
        title = "Maximum tokens",
        description = "Upper bound on completion tokens; omit to let the model decide."
    )
    private Property<Integer> maxTokens;

    @Schema(
        title = "JSON Response Schema",
        description = "JSON Schema string for Structured Output; passed as `response_format` with `type: json_schema` and `json_schema.schema` set to the provided document."
    )
    private Property<String> jsonResponseSchema;


    @Override
    public Output run(RunContext runContext) throws Exception {
        var rApiKey = runContext.render(this.apiKey).as(String.class).orElseThrow();
        var rModel = runContext.render(this.model).as(String.class).orElseThrow();
        var rTemperature = runContext.render(this.temperature).as(Double.class).orElse(0.2);
        var rTopP = runContext.render(this.topP).as(Double.class).orElse(0.9);
        var rTopK = runContext.render(this.topK).as(Integer.class).orElse(0);
        var rStream = runContext.render(this.stream).as(Boolean.class).orElse(false);
        var rPresencePenalty = runContext.render(this.presencePenalty).as(Double.class).orElse(0.0);
        var rFrequencyPenalty = runContext.render(this.frequencyPenalty).as(Double.class).orElse(0.0);
        var rMaxTokens = runContext.render(this.maxTokens).as(Integer.class).orElse(null);
        var rJsonResponseSchema = runContext.render(this.jsonResponseSchema).as(String.class).orElse(null);

        var rMessages = runContext.render(this.messages)
            .asList(ChatMessage.class)
            .stream()
            .map(msg -> Map.of("role", msg.type().role(), "content", Objects.toString(msg.content(), "")))
            .toList();

        var requestBody = new HashMap<String, Object>();

        requestBody.put("model", rModel);
        requestBody.put("messages", rMessages);
        requestBody.put("temperature", rTemperature);
        requestBody.put("top_p", rTopP);
        requestBody.put("top_k", rTopK);
        requestBody.put("stream", rStream);
        requestBody.put("presence_penalty", rPresencePenalty);
        requestBody.put("frequency_penalty", rFrequencyPenalty);

        if (rMaxTokens != null) {
            requestBody.put("max_tokens", rMaxTokens);
        }

        if (rJsonResponseSchema != null) {
            ObjectNode responseFormat = getJsonNodes(rJsonResponseSchema);
            requestBody.put("response_format", responseFormat);
        }

        try (var client = new HttpClient(runContext, HttpConfiguration.builder().build())) {
            var httpRequest = HttpRequest.builder()
                .uri(URI.create(API_URL))
                .method("POST")
                .addHeader("Authorization", "Bearer " + rApiKey)
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

    private static ObjectNode getJsonNodes(String schemaJson) throws JsonProcessingException {
        var mapper = new ObjectMapper();
        JsonNode schemaNode = mapper.readTree(schemaJson);

        // { type: "json_schema", json_schema: { schema: {...} } }
        ObjectNode jsonSchemaNode = mapper.createObjectNode();
        jsonSchemaNode.set("schema", schemaNode);

        ObjectNode responseFormat = mapper.createObjectNode();
        responseFormat.put("type", "json_schema");
        responseFormat.set("json_schema", jsonSchemaNode);

        return responseFormat;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Completion text",
            description = "First choice `message.content` returned by Perplexity."
        )
        private final String outputText;

        @Schema(
            title = "Raw response",
            description = "Full JSON payload returned by the Perplexity API as a string."
        )
        private final String rawResponse;
    }

    @Builder
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
