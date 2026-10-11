# CodeLobby Backend

[![Backend CI](https://github.com/CodeLobbyStudio/codelobby-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/CodeLobbyStudio/codelobby-backend/actions/workflows/ci.yml)
## Uruchomienie lokalne

### Wymagania
- Java 21

### Uruchomienie aplikacji

Windows:
.\mvnw.cmd spring-boot:run

### Uruchomienie testów

    .\mvnw.cmd clean verify

### Health check

http://localhost:8080/actuator/health

Oczekiwana odpowiedź:
{"status":"UP"}