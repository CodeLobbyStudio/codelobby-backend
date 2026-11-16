# Lokalny przepływ lobby i Java Run

## Uruchomienie

1. Uruchom PostgreSQL z repozytorium codelobby-infra. Backend używa lokalnie portu 55433, a aplikacja HTTP portu 8080.
2. Uruchom Judge0 z nakładką suite reports i codelobby-runner zgodnie z [instrukcją Runnera](https://github.com/CodeLobbyStudio/codelobby-runner#internal-run-endpoint). Udostępnij runnerowi i backendowi ten sam CODELOBBY_INTERNAL_KEY. Klucz ma co najmniej 32 znaki i należy do środowiska obu usług.
3. Uruchom backend z DB_PASSWORD i JWT_SECRET_BASE64 zgodnie z głównym README. Jeśli Runner ma inny lokalny adres, ustaw RUNNER_BASE_URL. Domyślny adres to http://127.0.0.1:8081.
4. Uruchom frontend z gałęzią integrującą web #30 oraz #27. Użyj oddzielnych sesji przeglądarki dla hosta i uczestnika.

## Przejście w przeglądarce

- Host tworzy zadanie wyłącznie dla Javy. Pole javaPublicTests dla języka wskazuje klasę main publicznego zestawu i uporządkowane identyfikatory przypadków. Pliki typu VISIBLE_TEST zapisują przypadki do CODELOBBY_REPORT_PATH zgodnie z kontraktem Runnera. Gotową treść formularza i plików zawiera [przykładowe zadanie](examples/create-java-task.json).
- Host tworzy lobby z limitem 2 (host plus uczestnik) i czasem 5 minut.
- Uczestnik dołącza kodem. Host widzi go w poczekalni i startuje sesję.
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
