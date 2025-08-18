package io.kestra.plugin.perplexity;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class ChatCompletionTest {

    private static final String PERPLEXITY_API_KEY = System.getenv("PERPLEXITY_API_KEY");

    @Inject
    private RunContextFactory runContextFactory;

    @EnabledIfEnvironmentVariable(named = "PERPLEXITY_API_KEY", matches = ".+")
    @Test
    void shouldGetResultsWithChatCompletion() throws Exception {
        var runContext = runContextFactory.of(Map.of(
            "apiKey", PERPLEXITY_API_KEY,
            "model", "sonar",
            "messages", List.of(
                ChatCompletion.ChatMessage.builder()
                    .type(ChatCompletion.ChatMessageType.USER)
                    .content("What is 2 plus 2? Answer with a number.")
                    .build()
                )
        ));

        var task = ChatCompletion.builder()
            .apiKey(Property.ofExpression("{{ apiKey }}"))
            .model(Property.ofExpression("{{ model }}"))
            .messages(Property.ofExpression("{{ messages }}"))
            .build();

        var output = task.run(runContext);

        assertThat(output, notNullValue());
        assertThat(output.getOutputText(), notNullValue());
        assertThat(output.getOutputText(), containsString("4"));
    }

    @EnabledIfEnvironmentVariable(named = "PERPLEXITY_API_KEY", matches = ".*")
    @Test
    void shouldGetStructuredJsonWithSchema_Perplexity() throws Exception {
        var schema = """
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
            """;

        var runContext = runContextFactory.of(Map.of(
            "apiKey", PERPLEXITY_API_KEY,
            "model", "sonar",
            "messages", List.of(
                ChatCompletion.ChatMessage.builder()
                    .type(ChatCompletion.ChatMessageType.USER)
                    .content("Make a JSON todo from this note: schedule team check-in next week; tags: work, planning; add a brief note.")
                    .build()
            ),
            "jsonResponseSchema", schema
        ));

        var task = io.kestra.plugin.perplexity.ChatCompletion.builder()
            .apiKey(Property.ofExpression("{{ apiKey }}"))
            .model(Property.ofExpression("{{ model }}"))
            .messages(Property.ofExpression("{{ messages }}"))
            .jsonResponseSchema(Property.ofExpression("{{ jsonResponseSchema }}"))
            .build();

        var output = task.run(runContext);
        System.out.println(output.getOutputText());
        assertThat(output.getOutputText(), notNullValue());
        assertThat(output.getRawResponse(), notNullValue());

        var mapper = new ObjectMapper();
        var node = mapper.readTree(output.getOutputText());

        assertThat(node.isObject(), is(true));
        assertThat(node.path("title").isTextual(), is(true));
        assertThat(node.path("done").isBoolean(), is(true));
        assertThat(node.path("tags").isArray(), is(true));

        if (!node.path("notes").isMissingNode()) {
            assertThat(node.path("notes").isTextual(), is(true));
        }

        assertThat(node.path("done").asBoolean(), is(false));

        var titleLower = node.path("title").asText().toLowerCase();
        assertThat(titleLower, anyOf(containsString("check"), containsString("team"), containsString("meeting")));

        var tags = new java.util.ArrayList<String>();
        node.path("tags").forEach(t -> tags.add(t.asText().toLowerCase()));

        var joined = String.join(" ", tags);
        assertThat(joined, allOf(containsString("work"), containsString("planning")));
    }
}