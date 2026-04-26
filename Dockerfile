# 1. Java 17 베이스 이미지 사용
FROM eclipse-temurin:17-jdk-jammy

# 2. 작업 디렉토리 설정
WORKDIR /app

# 3. 빌드된 JAR 파일을 컨테이너로 복사
# (CI에서 빌드된 JAR를 올리거나, EC2에서 빌드한 뒤 복사)
COPY target/*.jar app.jar

# 4. 포트 설정
EXPOSE 8081

# 5. 실행 명령어
# 5. 실행 명령어 (t3.small 2GB 메모리에 최적화: 1GB 할당)
ENTRYPOINT ["java", "-Xms1024m", "-Xmx1024m", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
