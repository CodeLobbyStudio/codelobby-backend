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


## PostgreSQL i Flyway

Backend korzysta z PostgreSQL 17 i Flyway do zarządzania schematem bazy danych.

### Lokalna baza danych

Baza jest uruchamiana przez Docker Compose z repozytorium `codelobby-infra`.

W PowerShell:

```powershell
cd C:\dev\CodeLobby\codelobby-infra
docker compose --env-file .env -f local/compose.yaml up -d
```

Parametry lokalnego połączenia:
- Host: `127.0.0.1`
- Port: `55433`
- Database: `codelobby`
- Username: `codelobby`
- Password: z lokalnego pliku `codelobby-infra/.env`

### Uruchomienie backendu

W PowerShell, w katalogu `codelobby-backend`:

```powershell
$line = Get-Content ..\codelobby-infra\.env |
    Where-Object { $_ -match '^POSTGRES_PASSWORD=' } |
    Select-Object -First 1

$env:DB_PASSWORD = $line.Substring('POSTGRES_PASSWORD='.Length)

.\mvnw.cmd spring-boot:run
```

Flyway automatycznie wykonuje brakujące migracje podczas uruchamiania aplikacji.

Hibernate jest skonfigurowany z `ddl-auto=validate`, dlatego nie tworzy i nie aktualizuje tabel.

### Testy integracyjne

Wymagany jest uruchomiony Docker Desktop.

```powershell
.\mvnw.cmd clean verify
```

Testcontainers uruchamia osobny PostgreSQL na czas testów.

Testy nie korzystają z lokalnej bazy developerskiej i nie wymagają ustawienia `DB_PASSWORD`.

### Migracje

Pliki migracji znajdują się w:

`src/main/resources/db/migration`

Po wykonaniu migracji Flyway zapisuje jej historię w tabeli `flyway_schema_history`.

Nie należy modyfikować już zastosowanych i współdzielonych migracji. Kolejne zmiany schematu dodajemy jako nowe pliki `V2__...sql`, `V3__...sql` itd.
