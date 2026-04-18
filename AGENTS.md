# Kestra Perplexity Plugin

## What

- Provides plugin components under `io.kestra.plugin.perplexity`.
- Includes classes such as `ChatCompletion`.

## Why

- This plugin integrates Kestra with Perplexity.
- It provides tasks that request chat completions from Perplexity models.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `perplexity`

Infrastructure dependencies (Docker Compose services):

- `app`

### Key Plugin Classes

- `io.kestra.plugin.perplexity.ChatCompletion`

### Project Structure

```
plugin-perplexity/
├── src/main/java/io/kestra/plugin/perplexity/
├── src/test/java/io/kestra/plugin/perplexity/
├── build.gradle
└── README.md
```

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
