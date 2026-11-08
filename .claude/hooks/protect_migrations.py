"""PreToolUse (Edit|Write|Bash|PowerShell): impede alterar migration Flyway já versionada.

"Já aplicada" = arquivo rastreado pelo git (git ls-files --error-unmatch).
Criar arquivo NOVO em db/migration é permitido.
Exceção documentada: CACTUS_ALLOW_MIGRATION_EDIT=1 (todas) ou lista "V15,V16__x.sql"
(prefixo de versão ou nome de arquivo), apenas para migrations comprovadamente
nunca aplicadas em nenhum ambiente — registrar o motivo no PR.
Exit 2 + stderr = bloqueio (contrato de hooks do Claude Code).
"""
import json
import os
import re
import subprocess
import sys

MIG_DIR = "cactusguard-backend/src/main/resources/db/migration/"
MUTATING = re.compile(
    r"(\bsed\s+-i|\brm\s|\bmv\s|\bgit\s+(mv|rm|checkout|restore)\b|\btruncate\b|>>?\s*[\"']?[^\s|;&]*db/migration|"
    r"\bSet-Content\b|\bAdd-Content\b|\bOut-File\b|\bRemove-Item\b|\bMove-Item\b|\bRename-Item\b|\bClear-Content\b)",
    re.IGNORECASE,
)


def allowed(name: str) -> bool:
    raw = os.environ.get("CACTUS_ALLOW_MIGRATION_EDIT", "").strip()
    if not raw:
        return False
    if raw == "1":
        return True
    return any(name == t or name.startswith(t.rstrip("_") + "__") for t in (x.strip() for x in raw.split(",")) if t)


def block(msg: str) -> None:
    sys.stderr.write(msg + "\n")
    sys.exit(2)


def tracked(root: str, rel: str) -> bool:
    r = subprocess.run(["git", "-C", root, "ls-files", "--error-unmatch", rel],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return r.returncode == 0


def main() -> None:
    data = json.load(sys.stdin)
    tool = data.get("tool_name", "")
    ti = data.get("tool_input", {}) or {}
    root = (os.environ.get("CLAUDE_PROJECT_DIR") or data.get("cwd") or ".").replace("\\", "/").rstrip("/")

    if tool in ("Bash", "PowerShell"):
        cmd = (ti.get("command") or "").replace("\\", "/")
        if "db/migration" in cmd and MUTATING.search(cmd):
            names = re.findall(r"V\d+__[\w.-]+\.sql", cmd)
            if names and all(allowed(n) for n in names):
                return
            block("BLOQUEADO: comando altera/remove arquivos em db/migration. Migrations Flyway "
                  "já aplicadas são imutáveis (checksum). Crie uma nova V<n+1>__descricao.sql "
                  "com o Write tool (skill /nova-migration).")
        return

    path = (ti.get("file_path") or "").replace("\\", "/")
    idx = path.find(MIG_DIR)
    if idx < 0 or not path.endswith(".sql"):
        return
    rel = path[idx:]
    name = rel.rsplit("/", 1)[-1]
    if not os.path.exists(path) or not tracked(root, rel):
        return  # arquivo novo (ou ainda não versionado): permitido
    if allowed(name):
        sys.stderr.write(f"[aviso] edição de {name} liberada por CACTUS_ALLOW_MIGRATION_EDIT.\n")
        return
    block(f"BLOQUEADO: {name} é uma migration Flyway já versionada (git) — editar quebra o "
          "checksum e diverge dos ambientes onde já rodou. Crie uma NOVA migration "
          "V<próximo>__descricao.sql com o ajuste (skill /nova-migration). Exceção "
          "(migration nunca aplicada em nenhum ambiente): CACTUS_ALLOW_MIGRATION_EDIT=" + name.split("__")[0])


if __name__ == "__main__":
    main()
