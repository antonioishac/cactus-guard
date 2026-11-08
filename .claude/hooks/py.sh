#!/usr/bin/env bash
# Executa um hook Python desta pasta com o interpretador disponível (python3 ou python).
# Uso (settings.json): bash "${CLAUDE_PROJECT_DIR}/.claude/hooks/py.sh" <script.py>
# Sem Python: não bloqueia (exit 0) — hooks são defesa adicional, não a única barreira.
dir="$(cd "$(dirname "$0")" && pwd)"
script="$dir/$1"
for c in python3 python; do
  if command -v "$c" >/dev/null 2>&1 && "$c" -I -c "" </dev/null >/dev/null 2>&1; then
    exec "$c" -I -X utf8 "$script"
  fi
done
echo "[hook] Python não encontrado; $1 ignorado." >&2
exit 0
