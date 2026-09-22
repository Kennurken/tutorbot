# ---- build stage -------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
# Warm the dependency cache in its own layer so code changes do not re-download everything.
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- runtime stage -----------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /workspace/target/tutorbot-*.jar app.jar
USER app
EXPOSE 8080
# Tuned for a 512 MB free-tier container: serial GC, small thread stacks, 70% of RAM for the heap.
ENV JAVA_OPTS="-XX:+UseSerialGC -XX:MaxRAMPercentage=70 -Xss512k -XX:TieredStopAtLevel=1"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
