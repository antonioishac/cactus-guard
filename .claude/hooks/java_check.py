"""PostToolUse (Edit|Write em *.java): checagem leve, sem build.

Avisa (exit 2 -> stderr vai para o Claude; a edição já ocorreu) quando o trecho gravado tem:
- System.out/err.println ou printStackTrace();
- log de PII em claro (cpf/cnpj/document/email/phone/telefone sem mask/hash);
- controller com @*Mapping e nenhum @PreAuthorize no arquivo.
"""
import json
import re
import sys

PRINT = re.compile(r"System\.(out|err)\.print|\.printStackTrace\(\)")
LOG_PII = re.compile(r"\blog(ger)?\.(trace|debug|info|warn|error)\s*\((?P<args>.*)", re.I)
PII = re.compile(r"\b\w*(cpf|cnpj|document|email|phone|telefone|celular)\w*\b", re.I)
SAFE = re.compile(r"mask|hash|Masker", re.I)
MAPPING = re.compile(r"@(Get|Post|Put|Patch|Delete|Request)Mapping")


def main() -> None:
    data = json.load(sys.stdin)
    ti = data.get("tool_input", {}) or {}
    path = (ti.get("file_path") or "").replace("\\", "/")
    if not path.endswith(".java") or "/src/test/" in path:
        return
    snippet = ti.get("content") if "content" in ti else ti.get("new_string", "")
    snippet = snippet or ""
    issues = []
    if PRINT.search(snippet):
        issues.append("System.out/printStackTrace: use logger SLF4J (@Slf4j) com trace id.")
    for line in snippet.splitlines():
        m = LOG_PII.search(line)
        if m and PII.search(m.group("args")) and not SAFE.search(m.group("args")):
            issues.append(f"possível PII em log: `{line.strip()[:120]}` -> use DataMasker/hash (LGPD, spec 040).")
    if path.endswith("Controller.java"):
        try:
            with open(path, encoding="utf-8") as f:
                full = f.read()
        except OSError:
            full = snippet
        if MAPPING.search(full) and "@PreAuthorize" not in full and "/auth/" not in path and "Health" not in path:
            issues.append("controller sem @PreAuthorize: defina a role por endpoint (spec 020).")
    if issues:
        sys.stderr.write(f"[java-check] {path.rsplit('/', 1)[-1]}:\n- " + "\n- ".join(issues[:5]) + "\n")
        sys.exit(2)


if __name__ == "__main__":
    main()
