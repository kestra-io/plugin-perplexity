package io.kestra.plugin.perplexity;

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
                new ChatCompletion.ChatMessage(
                    ChatCompletion.ChatMessageType.USER,
                    "What is 2 plus 2? Answer with a number."
                )
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
}