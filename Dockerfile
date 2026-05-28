# ── 阶段 1: 构建 ──
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN apt-get update && apt-get install -y maven && \
    mvn package -DskipTests -q && \
    mvn dependency:copy-dependencies -DoutputDirectory=target/lib -q

# ── 阶段 2: 运行 ──
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /build/target/erp-framework-*.jar app.jar
COPY --from=builder /build/target/lib ./lib
COPY app.properties version.txt ./

EXPOSE 8080
ENTRYPOINT ["java", "-cp", "app.jar:lib/*", "com.erp.ErpLauncher"]
