"""PreToolUse (Edit|Write): bloqueia gravação de segredos óbvios.

Bloqueia: arquivos .env/.env.* (exceto .env.example/.env.sample/.env.template),
chaves privadas PEM, JWT_SECRET/ENCRYPTION_KEY/PASSWORD com valor literal no estilo
VAR=valor, chaves AWS e tokens GitHub. Placeholders ${VAR} são permitidos.
"""
import json
import re
import sys

ENV_OK = (".env.example", ".env.sample", ".env.template")
PATTERNS = [
    (re.compile(r"-----BEGIN (RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----"), "chave privada PEM"),
    (re.compile(r"^\s*(export\s+)?[A-Z0-9_]*(JWT_SECRET|ENCRYPTION_KEY|SECRET_KEY|API_KEY|PASSWORD|TOKEN)\s*=\s*(?![\"']?\$\{)[\"']?[^\s\"'$#]{8,}", re.M),
     "segredo literal em VAR=valor"),
    (re.compile(r"\bAKIA[0-9A-Z]{16}\b"), "AWS access key"),
    (re.compile(r"\bgh[pousr]_[A-Za-z0-9]{36,}\b"), "token GitHub"),
]


def main() -> None:
    data = json.load(sys.stdin)
    ti = data.get("tool_input", {}) or {}
    path = (ti.get("file_path") or "").replace("\\", "/")
    base = path.rsplit("/", 1)[-1]
    if (base == ".env" or base.startswith(".env.")) and base not in ENV_OK:
        sys.stderr.write(f"BLOQUEADO: não grave {base} pelo Claude. Segredos ficam em variáveis de "
                         "ambiente/cofre; documente as chaves em .env.example (sem valores).\n")
        sys.exit(2)
    text = ti.get("content") if "content" in ti else ti.get("new_string", "")
    for rx, label in PATTERNS:
        if rx.search(text or ""):
            sys.stderr.write(f"BLOQUEADO: conteúdo parece conter {label} em {base}. Use ${{VAR}} / "
                             "variável de ambiente (ver specs/042-credenciais-integracoes.md).\n")
            sys.exit(2)


if __name__ == "__main__":
    main()
