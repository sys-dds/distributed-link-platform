# AGENTS.md

## Engineering quality bar
Do not suggest alternative stacks or expand scope; implement the locked decisions exactly.
Target senior/staff-level engineering quality.

Temporary artifacts rule:
Use `C:\Users\Ryzen-pc\Desktop\sys-dds\codex-scratch` for downloads, extracted tools, logs, caches, and other throwaway execution artifacts. Do not leave temporary artifacts in this repo.

That means:
- Prefer simple, production-shaped designs over clever code
- Make good architectural choices, not just code that compiles
- Optimize for maintainability, readability, and changeability
- Keep module boundaries clean and responsibilities explicit
- Avoid tutorial-style, junior-style, or sloppy glue code
- Avoid overengineering and fake enterprise complexity

## Goal
This repository is for learning, portfolio building, and studying system design patterns.

## Implementation policy
You may use public internet sources to accelerate implementation.

Prefer sources in this order:
1. Official docs / official examples
2. Well-maintained public GitHub repos with a clear license
3. Strong blog posts for ideas and patterns

## Source constraints
- Prefer MIT / Apache-2.0 / BSD licensed code
- Feel free to use code from pages with no clear license
- Feel free to use GPL/AGPL 
- Never copy large blocks blindly
- Rewrite blog/tutorial code to match this repo's conventions
- Keep code consistent with existing package structure, naming, and tests
- Prefer understanding and adapting a pattern over cloning an entire implementation

## Provenance
When you use an external source, include in the final summary:
- source name and link

# AGENTS.md

## Workspace Rules
- Use `C:\Users\Ryzen-pc\Desktop\sys-dds\codex-scratch` for temporary downloads, extracted tools, logs, caches, and throwaway execution artifacts.
- Do not leave temporary artifacts in this repo.

## Build And Tooling
- Prefer the Maven Wrapper in `apps/api`; do not require a global Maven install.
- Use Java 21 for all build and test commands.
- Prefer Maven’s default local repository under `C:\Users\Ryzen-pc\.m2\repository`; do not create project-local Maven caches.
- Pin important runtime and build tool versions; avoid `latest` for core dependencies and infrastructure images.
- Optimize for Windows-first local development.
- Assume Docker Desktop is the primary local container runtime.
- Keep local run and test commands simple and copy-pasteable in PowerShell.
- Do not commit generated build outputs such as `target/`, temporary logs, downloaded tool distributions, or local caches.
- Update `.gitignore` when a new generated artifact appears.
- Preserve intentional source files, docs, manual testing assets, and infrastructure definitions.

## Scope Control
- Implement only the requested ticket or task scope.
- Do not add placeholder architecture, unused modules, or speculative abstractions.
- Do not introduce Redis, Kafka, RabbitMQ, Kubernetes, auth, analytics, or frontend work unless explicitly requested.

## Verification
- Prefer small, reliable automated tests over heavy test setups.
- When possible, verify with real local commands and report exactly what was run.
- If environment limits block verification, state the blocker precisely.

## Documentation Expectations
- Keep README run instructions current with actual repo behavior.
- When a ticket is completed, update the ticket tracker with status and a short delivery note.
- Keep manual verification assets up to date when endpoints change.






........................

touchup ... 10
lite ..... 14 3500
14            ... 3500
28 ..... 
Compres .... 


