# ---- build stage ----
FROM gradle:8.10.2-jdk17 AS build
WORKDIR /app

# 캐시를 위해 의존성 먼저
COPY build.gradle settings.gradle gradlew /app/
COPY gradle /app/gradle
RUN ./gradlew --no-daemon dependencies || true

# 소스 복사 후 빌드
COPY . /app
RUN ./gradlew --no-daemon clean bootJar

# ---- run stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# 빌드된 jar 복사
COPY --from=build /app/build/libs/*.jar app.jar

# (선택) 컨테이너 시간대
ENV TZ=Asia/Seoul

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
