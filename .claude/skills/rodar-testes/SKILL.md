---
name: rodar-testes
description: Executa testes do backend CactusGuard (um teste, um padrão/módulo ou a suíte) com Maven e JDK 25, sem mascarar o exit code, e resume falhas lendo só o necessário dos relatórios do surefire. Use para verificar mudanças no cactusguard-backend.
argument-hint: "[NomeDoTest | Padrao*Test | all]"
---

Alvo: $ARGUMENTS (vazio = pergunte se é módulo ou suíte; prefira o menor escopo).

1. Comando (um único Bash, exit code preservado):
   - teste/padrão: `cd cactusguard-backend && JAVA_HOME=/c/Java/jdk-25.0.2 mvn -q test -Dtest='$ARGUMENTS' -Dsurefire.failIfNoSpecifiedTests=false; echo "EXIT=$?"`
   - suíte (`all`): `cd cactusguard-backend && JAVA_HOME=/c/Java/jdk-25.0.2 mvn -q test; echo "EXIT=$?"`
   - Nunca `| tail`/`| grep` sem `set -o pipefail`. Suíte completa demora: use `run_in_background` se for seguir trabalhando.
2. Se `EXIT` ≠ 0: liste as classes com falha (`grep -lE "FAILURE|ERROR" cactusguard-backend/target/surefire-reports/*.txt`) e leia apenas as primeiras ~30 linhas do `.txt` de cada uma.
3. Diferencie falha de compilação, contexto Spring que não sobe (geralmente entity/H2) e asserção.
4. Responda em até 8 linhas: comando, EXIT, testes executados/falhos, causa provável e próximo passo.
