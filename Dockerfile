# ==================== BUILD ====================
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Copia apenas os arquivos de build primeiro (cache de dependências)
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw

# Baixa dependências (camada cacheável)
RUN ./mvnw dependency:resolve -B -q 2>/dev/null || true

# Copia código-fonte e compila
COPY src src
RUN ./mvnw package -DskipTests -B -q

# ==================== RUNTIME ====================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Cria usuário não-root
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
