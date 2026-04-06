## Repo-local rules
- Use `C:\Users\Ryzen-pc\Desktop\sys-dds\codex-scratch` for temporary files, downloads, extracted tools, logs, caches, and other throwaway execution artifacts.
- Do not leave temporary artifacts in this repo.

## Execution rule
- Never report compile/test results from stale build outputs. If signatures, contracts, migrations, runtime wiring, or tests changed, do a fresh targeted rebuild first.