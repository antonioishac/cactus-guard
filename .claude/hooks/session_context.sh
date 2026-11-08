#!/usr/bin/env bash
# SessionStart: imprime até 4 linhas de contexto barato (stdout vira contexto do Claude).
cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null || exit 0
branch="$(git rev-parse --abbrev-ref HEAD 2>/dev/null)" || exit 0
changes="$(git status --porcelain 2>/dev/null)"
total=$(printf '%s\n' "$changes" | grep -c . )
echo "[CactusGuard] branch: $branch | arquivos alterados: $total"
if [ "$total" -gt 0 ]; then
  mods=$(printf '%s\n' "$changes" | sed -E 's/^.. "?//' \
    | sed -nE 's#^cactusguard-backend/src/(main|test)/java/br/com/cactus/guard/([^/]+)/.*#backend:\2#p; s#^cactusguard-backend/src/main/resources/db/migration/.*#backend:migrations#p; s#^(cactusguard-web|specs|specs-frontend|\.claude)/.*#\1#p' \
    | sort | uniq -c | sort -rn | head -8 | awk '{printf "%s(%s) ", $2, $1}')
  [ -n "$mods" ] && echo "[CactusGuard] áreas alteradas: $mods"
fi
mig=$(ls cactusguard-backend/src/main/resources/db/migration 2>/dev/null | sed -nE 's/^V([0-9]+)__.*/\1/p' | sort -n | tail -1)
[ -n "$mig" ] && echo "[CactusGuard] última migration: V$mig (próxima: V$((mig+1)))"
exit 0
