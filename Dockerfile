# 使用官方 Maven 與 JDK 17 映像檔進行建置 (Build Stage)
FROM maven:3.9.6-eclipse-temurin-17-focal AS build
WORKDIR /app
COPY pom.xml .
# 預先下載相依套件 (利用 Docker 快取機制加速後續建置)
RUN mvn dependency:go-offline

# 複製原始碼並打包
COPY src ./src
RUN mvn package -DskipTests

# 執行階段 (Runtime Stage) - 使用輕量級的 JRE 映像檔
FROM eclipse-temurin:17-jre-focal
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Cloud Run 預設會提供 PORT 環境變數 (通常是 8080)
ENV PORT=8080
EXPOSE 8080

# 啟動應用程式
ENTRYPOINT ["java", "-jar", "app.jar"]
