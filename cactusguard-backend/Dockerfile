# syntax=docker/dockerfile:1
#
# Imagem do backend CactusGuard — build reprodutível e auto-contido (não depende de target/ local).
#   docker build -t cactusguard-backend cactusguard-backend
#
# Imagens base fixadas por DIGEST (índice multi-arquitetura). O Dependabot (ecosystem "docker")
# abre PR quando a tag ganha novo digest. Para atualizar manualmente:
#   docker buildx imagetools inspect eclipse-temurin:25-jre-alpine   (linha "Digest:")

# ---------- 1) build ----------
FROM eclipse-temurin:25-jdk-alpine@sha256:c7c3d0bdf7f2bbc5a05a6712ed334d198360105634ea9adf76fca94f381b1e3f AS build
WORKDIR /build

# Camada de dependências (cacheada enquanto pom.xml/wrapper não mudam)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -q dependency:go-offline

COPY src/ src/
# Testes rodam no workflow backend.yml (mvn verify); aqui só empacota.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -q -DskipTests package \
 && cp target/cactusguard-*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---------- 2) runtime ----------
FROM eclipse-temurin:25-jre-alpine@sha256:e1898fd6a11fc965a994d8ace186fd3585aeb636cf8f05f47a723fcfee0b0918

LABEL org.opencontainers.image.title="cactusguard-backend" \
      org.opencontainers.image.description="CactusGuard - plataforma PLD/FT (backend)" \
      org.opencontainers.image.vendor="CactusGuard"

# Usuário/grupo sem privilégio com UID/GID fixos (compatível com runAsNonRoot/runAsUser no Kubernetes)
RUN addgroup -S -g 10001 cactus \
 && adduser -S -D -H -u 10001 -G cactus -s /sbin/nologin cactus \
 && mkdir -p /app/reports \
 && chown -R 10001:10001 /app/reports

WORKDIR /app

# Camadas do Spring Boot (dependências mudam menos que o código da aplicação)
COPY --from=build --chown=root:root /build/extracted/dependencies/ ./
COPY --from=build --chown=root:root /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=root:root /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=root:root /build/extracted/application/ ./

# Código pertence ao root (somente leitura para o processo); só /app/reports é gravável (relatórios PDF/Excel).
USER 10001:10001

EXPOSE 8080

# Sem segredos na imagem: JWT_SECRET, chaves de criptografia e CACTUS_DB_* entram por variável de ambiente/cofre.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "application.jar"]
