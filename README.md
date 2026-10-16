# CodeLobby Backend

[![Backend CI](https://github.com/CodeLobbyStudio/codelobby-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/CodeLobbyStudio/codelobby-backend/actions/workflows/ci.yml)
## Uruchomienie lokalne

### Wymagania
- Java 21
- Docker Desktop do uruchomienia lokalnej bazy i testów integracyjnych

### Uruchomienie aplikacji

Najpierw uruchom bazę i ustaw `DB_PASSWORD` oraz `JWT_SECRET_BASE64`, jak opisano poniżej. Potem w katalogu backendu uruchom:

```powershell
.\mvnw.cmd spring-boot:run
```

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

Poniższy fragment odczytuje hasło bazy z pliku `.env` i generuje lokalny sekret JWT. Obie zmienne będą ustawione w bieżącym oknie PowerShell.

```powershell
$line = Get-Content ..\codelobby-infra\.env |
    Where-Object { $_ -match '^POSTGRES_PASSWORD=' } |
    Select-Object -First 1

$env:DB_PASSWORD = $line.Substring('POSTGRES_PASSWORD='.Length)

$key = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($key)
$rng.Dispose()
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($key)

.\mvnw.cmd spring-boot:run
```

Sekret JWT musi mieć dokładnie 32 bajty po odkodowaniu Base64. Przy zmianie sekretu wcześniejsze tokeny przestają działać, więc przy kolejnych uruchomieniach możesz używać tej samej wartości. Nie zapisuj jej w repo.

Jeśli uruchamiasz aplikację z IntelliJ, dodaj `DB_PASSWORD` i `JWT_SECRET_BASE64` w konfiguracji uruchomienia: **Run → Edit Configurations → Environment variables**.

Flyway automatycznie wykonuje brakujące migracje podczas uruchamiania aplikacji.

Hibernate jest skonfigurowany z `ddl-auto=validate`, dlatego nie tworzy i nie aktualizuje tabel.

### Testy integracyjne

Wymagany jest uruchomiony Docker Desktop.

```powershell
.\mvnw.cmd clean verify
```

Testcontainers uruchamia osobny PostgreSQL na czas testów.

Testy nie korzystają z lokalnej bazy developerskiej. Mają też własny sekret JWT w `src/test/resources/application-test.yaml`, więc do testów nie trzeba ustawiać `DB_PASSWORD` ani `JWT_SECRET_BASE64`.

### Migracje

Pliki migracji znajdują się w:

`src/main/resources/db/migration`

Po wykonaniu migracji Flyway zapisuje jej historię w tabeli `flyway_schema_history`.

Nie należy modyfikować już zastosowanych i współdzielonych migracji. Kolejne zmiany schematu dodajemy jako nowe pliki `V2__...sql`, `V3__...sql` itd.
