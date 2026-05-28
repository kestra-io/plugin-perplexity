# How to use the Perplexity plugin

Run Perplexity AI chat completions from Kestra flows.

## Authentication

Set `apiKey` (required) to your Perplexity API key. Store secrets in [secrets](https://kestra.io/docs/concepts/secret) and apply connection properties globally with [plugin defaults](https://kestra.io/docs/workflow-components/plugin-defaults).

## Tasks

`ChatCompletion` sends a chat conversation to a Perplexity model — set `model` (required, e.g. `sonar` or `sonar-pro`), `messages` (required, list of objects with `type` (`SYSTEM`, `ASSISTANT`, or `USER`) and `content`), and `apiKey` (required). Tune generation with `temperature` (default 0.2), `topP` (default 0.9), `topK` (default 0), `presencePenalty` (default 0.0), `frequencyPenalty` (default 0.0), and `maxTokens`. Set `jsonResponseSchema` to constrain the output to a JSON schema. The output includes `outputText` (the model's reply) and `rawResponse` (full JSON payload).
