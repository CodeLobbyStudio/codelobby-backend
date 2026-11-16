# Lokalny przepływ lobby i Java Run

## Uruchomienie

1. Uruchom PostgreSQL z repozytorium codelobby-infra. Backend używa lokalnie portu 55433, a aplikacja HTTP portu 8080.
2. Uruchom Judge0 z nakładką suite reports i codelobby-runner zgodnie z [instrukcją Runnera](https://github.com/CodeLobbyStudio/codelobby-runner#internal-run-endpoint). Udostępnij runnerowi i backendowi ten sam CODELOBBY_INTERNAL_KEY. Klucz ma co najmniej 32 znaki i należy do środowiska obu usług.
3. Uruchom backend z DB_PASSWORD i JWT_SECRET_BASE64 zgodnie z głównym README. Jeśli Runner ma inny lokalny adres, ustaw RUNNER_BASE_URL. Domyślny adres to http://127.0.0.1:8081.
4. Przełącz `codelobby-web` na `feat/java-lobby-integration` ([PR #31](https://github.com/CodeLobbyStudio/codelobby-web/pull/31)), uruchom `npm ci` i `npm run dev`, a następnie otwórz <http://localhost:5173>. Domyślny proxy Vite kieruje `/api` do `http://localhost:8080`. Ustaw `API_PROXY_TARGET`, jeśli backend działa pod innym adresem. Użyj dwóch profili przeglądarki i dwóch kont.

## Przejście w przeglądarce

- Host wybiera **Utwórz lobby**, wpisuje nazwę i zostawia wstępnie wypełnione zadanie Java. Na potrzeby próby może ustawić limit 2 (host plus uczestnik) i czas 5 minut. Formularz zapisuje zadanie, a następnie lobby z tym zadaniem.
- Uczestnik otwiera link do zaproszenia w drugim profilu przeglądarki, loguje się i wybiera **Dołącz**. Można też przepisać kod do formularza **Dołącz kodem**. Host widzi uczestnika w poczekalni i wybiera **Rozpocznij sesję**.
- Uczestnik otwiera zadanie. Starter z przykładu daje WRONG_ANSWER. Po zamianie ciała metody countVowels na return Helpers.count(text); Run daje ACCEPTED. Niepoprawny kod zwraca COMPILATION_ERROR.
- Host kończy sesję albo czeka do endsAt. Po zamknięciu nowe Run są odrzucane.

## API i test

Wszystkie poniższe operacje wymagają JWT aplikacji. Szczegóły i workspace są dostępne dla członków lobby, a start i zakończenie dla hosta.

| Metoda | Ścieżka | Dane |
| --- | --- | --- |
| POST | /api/tasks | Przykładowe zadanie zawiera pełny JSON |
| POST | /api/lobbies | name, taskIds, allowedLanguages, maxParticipants, durationMinutes |
| POST | /api/lobbies/join | code |
| GET | /api/lobbies/{id} | Status, rola, członkowie, zadania i daty sesji |
| POST | /api/lobbies/{id}/start | Gotowe lobby przechodzi do ACTIVE |
| GET | /api/lobbies/{id}/tasks/{taskId}/workspace?language=JAVA | Opis, taskVersionId, pliki EDITABLE i READ_ONLY, publiczne testId |
| POST | /api/lobbies/{id}/tasks/{taskId}/run | taskVersionId, language, files [{path,content}] z edytowalnymi zmianami |
| POST | /api/lobbies/{id}/finish | Host kończy sesję |

Pole result zawiera status, passedTests, totalTests i testResults z publicznymi testId. Diagnostyka input, expectedOutput i actualOutput ma dowolny typ JSON lub wartość null. Kod testów pozostaje po stronie usług. Backend czeka na odpowiedź Runnera do 65 sekund, a klient HTTP w przeglądarce potrzebuje nieco dłuższego limitu, np. 70 sekund.

Z repozytorium backendu odpal test API na lokalnym stacku:

~~~powershell
python scripts/smoke_java_flow.py
~~~

Skrypt tworzy testowe konta i dane, sprawdza wyścig o ostatnie miejsce, uprawnienia, workspace oraz trzy wyniki Run. Przy domyślnej konfiguracji oczekuje uruchomionego backendu na 127.0.0.1:8080 i działającego Runnera z Judge0.

Pełny odbiór przepływu śledzi [backend #61](https://github.com/CodeLobbyStudio/codelobby-backend/issues/61). Weryfikację prawdziwego Judge0 na Windows x64 z WSL2 śledzi [Runner #38](https://github.com/CodeLobbyStudio/codelobby-runner/issues/38).
