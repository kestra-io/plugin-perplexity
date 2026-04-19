# Kestra Perplexity Plugin

## What

- Provides plugin components under `io.kestra.plugin.perplexity`.
- Includes classes such as `ChatCompletion`.

## Why

- What user problem does this solve? Teams need to request chat completions from Perplexity models from orchestrated workflows instead of relying on manual console work, ad hoc scripts, or disconnected schedulers.
- Why would a team adopt this plugin in a workflow? It keeps Perplexity steps in the same Kestra flow as upstream preparation, approvals, retries, notifications, and downstream systems.
- What operational/business outcome does it enable? It reduces manual handoffs and fragmented tooling while improving reliability, traceability, and delivery speed for processes that depend on Perplexity.

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
