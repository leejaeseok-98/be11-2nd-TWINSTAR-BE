# ============================================
# 1. Build Stage
# ============================================
FROM gradle:8.5-jdk17 AS build
WORKDIR /app

# Gradle 의존성 캐싱 최적화
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon || true

# 소스 코드 복사 및 빌드
COPY src ./src
RUN gradle clean bootJar -x test --no-daemon

# ============================================
# 2. Runtime Stage
# ============================================
FROM openjdk:17-jdk-slim
WORKDIR /app

# 한국 시간대 설정
RUN apt-get update && \
    apt-get install -y tzdata && \
    ln -sf /usr/share/zoneinfo/Asia/Seoul /etc/localtime && \
    echo "Asia/Seoul" > /etc/timezone && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# 빌드된 JAR 파일 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 포트 노출
EXPOSE 8080

# 헬스체크 (옵션)
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]