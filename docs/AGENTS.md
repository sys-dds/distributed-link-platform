## Repo-local rules

These are the broader elevated tool approvals we’ve established in this session, with the exact binary paths to use:
rg
C:\Users\Ryzen-pc\AppData\Local\Microsoft\WinGet\Packages\BurntSushi.ripgrep.MSVC_Microsoft.Winget.Source_8wekyb3d8bbwe\ripgrep-15.1.0-x86_64-pc-windows-msvc\rg.exe
fd
C:\Users\Ryzen-pc\AppData\Local\Microsoft\WinGet\Packages\sharkdp.fd_Microsoft.Winget.Source_8wekyb3d8bbwe\fd-v10.4.2-x86_64-pc-windows-msvc\fd.exe
jq
C:\Users\Ryzen-pc\AppData\Local\Microsoft\WinGet\Packages\jqlang.jq_Microsoft.Winget.Source_8wekyb3d8bbwe\jq.exe
sd
C:\Users\Ryzen-pc\AppData\Local\Microsoft\WinGet\Packages\chmln.sd_Microsoft.Winget.Source_8wekyb3d8bbwe\sd-v1.1.0-x86_64-pc-windows-msvc\sd.exe
delta
C:\Users\Ryzen-pc\AppData\Local\Microsoft\WinGet\Packages\dandavison.delta_Microsoft.Winget.Source_8wekyb3d8bbwe\delta-0.19.2-x86_64-pc-windows-msvc\delta.exe
just
C:\Users\Ryzen-pc\AppData\Local\Microsoft\WinGet\Packages\Casey.Just_Microsoft.Winget.Source_8wekyb3d8bbwe\just.exe
If you want, I can also format that as a compact memory note.
Use wrapper scripts for compile/test/clean when available.
- Get-Command rg -All
- rg --version
- Use `C:\Users\Ryzen-pc\Desktop\sys-dds\codex-scratch` for temporary files, downloads, extracted tools, logs, caches, and other throwaway execution artifacts.
- Do not leave temporary artifacts in this repo.

## Execution rule
do not run automated tests
do not write new tests
focus on implementing the checklist fully
only do a fresh build/compile to catch obvious breakage