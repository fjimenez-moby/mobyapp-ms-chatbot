# Multi-stage build para Chatbot Service (con RAG)

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Copiar archivos de configuración de Maven
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

# Descargar dependencias
RUN mvn dependency:go-offline -B

# Copiar código fuente
COPY src ./src

# Compilar la aplicación
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Crear usuario no-root
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Copiar el JAR compilado
COPY --from=build /app/target/*.jar app.jar

# Crear directorios para almacenamiento de documentos y base de datos H2
RUN mkdir -p /app/storage/documents /app/data && \
    chown -R appuser:appgroup /app

USER appuser

# Exponer puerto
EXPOSE ${CHATBOT_PORT:-8086}

# Variables de entorno
ENV JAVA_OPTS="-Xmx768m -Xms512m" \
    CHATBOT_STORAGE_PATH=/app/storage/documents

# Healthcheck
HEALTHCHECK --interval=30s --timeout=3s --start-period=90s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:${CHATBOT_PORT:-8086}/api/chat/ping || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]