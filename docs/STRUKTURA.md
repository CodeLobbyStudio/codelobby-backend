# Struktura backendu

Na start trzymamy to prosto i dzielimy kod głównie po funkcjach biznesowych.

- `auth/` — logowanie, rejestracja i rzeczy związane z kontem
- `lobby/` — tworzenie lobby, dołączanie, host i uczestnicy
- `task/` — zadania, języki, starter code i testy
- `submission/` — wysłane rozwiązania i wyniki
- `common/` — rzeczy wspólne, które faktycznie są używane w kilku miejscach
- `resources/db/migration/` — migracje bazy
- `test/` — testy backendu

Nie rozbijamy tego teraz na kilkadziesiąt warstw i katalogów. Jak pojawi się realny kod, będziemy dokładali controller/service/repository tam, gdzie będzie to miało sens.
