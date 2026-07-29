# Build stage
FROM eclipse-temurin:25-jdk AS builder
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
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]