\# AGENTS.md



\*\*Rule:\*\* In each command, \*\*define → use\*\*. Do \*\*not\*\* escape `$`. Use generic `'path/to/file.ext'`.



---



\## 1) READ (UTF‑8 no BOM, line‑numbered)



```bash

bash -lc 'powershell -NoLogo -Command "

$OutputEncoding = \[Console]::OutputEncoding = \[Text.UTF8Encoding]::new($false);

Set-Location -LiteralPath (Convert-Path .);

function Get-Lines { param(\[string]$Path,\[int]$Skip=0,\[int]$First=40)

&nbsp; $enc=\[Text.UTF8Encoding]::new($false)

&nbsp; $text=\[IO.File]::ReadAllText($Path,$enc)

&nbsp; if($text.Length -gt 0 -and $text\[0] -eq \[char]0xFEFF){ $text=$text.Substring(1) }

&nbsp; $ls=$text -split \\"`r?`n\\"

&nbsp; for($i=$Skip; $i -lt \[Math]::Min($Skip+$First,$ls.Length); $i++){ \\"{0:D4}: {1}\\" -f ($i+1), $ls\[$i] }

}

Get-Lines -Path \\"path/to/file.ext\\" -First 120 -Skip 0

"'

```



---



\## 2) WRITE (UTF‑8 no BOM, atomic replace, backup)



```bash

bash -lc 'powershell -NoLogo -Command "

$OutputEncoding = \[Console]::OutputEncoding = \[Text.UTF8Encoding]::new($false);

Set-Location -LiteralPath (Convert-Path .);

function Write-Utf8NoBom { param(\[string]$Path,\[string]$Content)

&nbsp; $dir = Split-Path -Parent $Path

&nbsp; if (-not (Test-Path $dir)) {

&nbsp;   New-Item -ItemType Directory -Path $dir -Force | Out-Null

&nbsp; }

&nbsp; $tmp = \[IO.Path]::GetTempFileName()

&nbsp; try {

&nbsp;   $enc = \[Text.UTF8Encoding]::new($false)

&nbsp;   \[IO.File]::WriteAllText($tmp,$Content,$enc)

&nbsp;   Move-Item $tmp $Path -Force

&nbsp; }

&nbsp; finally {

&nbsp;   if (Test-Path $tmp) {

&nbsp;     Remove-Item $tmp -Force -ErrorAction SilentlyContinue

&nbsp;   }

&nbsp; }

}

$file = "path/to/your\_file.ext"

$enc  = \[Text.UTF8Encoding]::new($false)

$old  = (Test-Path $file) ? (\[IO.File]::ReadAllText($file,$enc)) : ''

Write-Utf8NoBom -Path $file -Content ($old+"`nYOUR\_TEXT\_HERE`n")

"'

```



