# Build stage
FROM eclipse-temurin:26-jdk AS builder
WORKDIR /app

ARG GITHUB_TOKEN=
ARG GITHUB_ACTOR=algorithmikk
RUN if [ -n "$GITHUB_TOKEN" ]; then \
      mkdir -p /root/.m2 && \
      printf '%s\n' \
        '<settings><servers><server><id>github</id><username>'"$GITHUB_ACTOR"'</username><password>'"$GITHUB_TOKEN"'</password></server></servers>' \
        '<profiles><profile><id>github-packages</id><repositories><repository><id>github</id>' \
        '<url>https://maven.pkg.github.com/algorithmikk/umameats-api-parent</url></repository></repositories></profile></profiles>' \
        '<activeProfiles><activeProfile>github-packages</activeProfile></activeProfiles></settings>' \
        > /root/.m2/settings.xml; \
    fi
COPY . .
RUN ./mvnw clean package -DskipTests

# Run stage

# Extract layers + best-effort JDK AOT Cache (Phase C)

# Extract layered JAR (Phase C). AOT training is offline (test/perf/train-aot-cache.sh) —
# do not train during image build (corrupt caches crash JDK 26 boot).
FROM eclipse-temurin:26-jre AS extract
WORKDIR /extract
COPY --from=builder /app/target/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# Run stage
FROM eclipse-temurin:26-jre
WORKDIR /app
COPY --from=extract /extract/extracted/dependencies/ ./
COPY --from=extract /extract/extracted/spring-boot-loader/ ./
COPY --from=extract /extract/extracted/snapshot-dependencies/ ./
COPY --from=extract /extract/extracted/application/ ./
COPY docker/entrypoint.sh /app/entrypoint.sh
USER root
RUN chmod +x /app/entrypoint.sh 
EXPOSE 8080
ENTRYPOINT ["/app/entrypoint.sh"]
