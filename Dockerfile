# Build stage
FROM eclipse-temurin:26-jdk AS builder
WORKDIR /app

ARG GITHUB_TOKEN=
ARG GITHUB_ACTOR=algorithmikk
RUN if [ -n "$GITHUB_TOKEN" ]; then \
      mkdir -p /root/.m2 && \
      printf '%s\n' \
        '<settings><servers>' \
        '<server><id>github</id><username>'"$GITHUB_ACTOR"'</username><password>'"$GITHUB_TOKEN"'</password></server>' \
        '<server><id>github-messaging</id><username>'"$GITHUB_ACTOR"'</username><password>'"$GITHUB_TOKEN"'</password></server>' \
        '</servers><profiles><profile><id>github-packages</id><repositories>' \
        '<repository><id>github</id><url>https://maven.pkg.github.com/algorithmikk/umameats-api-parent</url></repository>' \
        '<repository><id>github-messaging</id><url>https://maven.pkg.github.com/algorithmikk/umameats-messaging</url></repository>' \
        '</repositories></profile></profiles>' \
        '<activeProfiles><activeProfile>github-packages</activeProfile></activeProfiles></settings>' \
        > /root/.m2/settings.xml; \
    fi
COPY . .
# Install vendored messaging first so package resolution does not need Packages ACL.
RUN ./mvnw -f .build/umameats-messaging/pom.xml clean install -DskipTests \
 && ./mvnw clean package -DskipTests

# Extract the jar into an app jar plus a lib directory.
#
# Deliberately not `--layers --launcher`. That form is better for image layer reuse, but
# it puts the application on the classpath as a directory, which produced an AOT cache
# that could be created yet never mapped at boot on amd64. This jar-plus-lib layout is
# what the Spring Boot AOT cache documentation uses. Copying lib/ separately keeps most
# of the layer-caching benefit, since dependencies change far less often than code.
FROM eclipse-temurin:26-jre AS extract
WORKDIR /extract
COPY --from=builder /app/target/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --destination extracted \
 && mv extracted/*.jar extracted/application.jar

# Run stage
FROM eclipse-temurin:26-jre
WORKDIR /app
COPY --from=extract /extract/extracted/lib/ ./lib/
COPY --from=extract /extract/extracted/application.jar ./app.jar

# Build the JDK AOT cache. See docker/build-aot.sh for why this runs in the final image
# and what it falls back to; it always exits 0, leaving no cache behind if none can be
# verified, in which case the entrypoint boots normally.
COPY docker/build-aot.sh /tmp/build-aot.sh
RUN sh /tmp/build-aot.sh && rm -f /tmp/build-aot.sh
COPY docker/entrypoint.sh /app/entrypoint.sh
USER root
RUN chmod +x /app/entrypoint.sh 
EXPOSE 8080
# Heap is left at the JVM default (MaxRAMPercentage=25). These tasks run below the
# 2-CPU/1792MB server-class threshold, so the JVM selects SerialGC (confirmed in
# production: gc="Copy" / gc="MarkSweepCompact"). Raising this to 70% was tried and
# reverted: on a 512MB task it leaves too little for metaspace, code cache and thread
# stacks, and a repeated local benchmark showed no latency benefit at any size.
ENTRYPOINT ["/app/entrypoint.sh"]
