MCS
---
System uruchamiający serwer Minecraft przy próbie wejścia jakiegoś gracza. Powiedzmy.

---
## Działanie
MCS działa jako serwer nasłuchujący na połączenia protokołu Minecrafta oraz jako monitor wyłączający serwer w razie braku aktywności. (Obecnie) MCS powinno uruchamiać się w skrypcie, który uruchamia serwer Minecraft z pluginem i po jego zamknięciu serwer MCS. Przykład takiego skryptu to:
```bash
while true
do
  java -jar paper-1.20.1-171.jar nogui
  java -jar mcs.jar
done
```
Tak, nie jest to idealne rozwiązanie. Działa? Działa.

## Zastosowanie
MCS został stworzony dla prostych serwerów Minecraft dla znajomych w zamyśle uproszczenia włączania / wyłączenia serwera, jeśli nikt na nim obecnie nie gra lub ktoś chce na niego wejść.
Innymi słowy: jeśli (masz znajomych i) stawiasz dla nich serwer, a z jakiegoś powodu (zasoby np.) nie chcesz by serwer był aktywny 24/7, ten projekt jest dla ciebie.

## Notka bezpieczeństwa
**MCS nie został przetestowany pod kątem przeróżnych ataków, ani pod działanie proxy. Nie zaleca się stosowania tego projektu na większych serwerach / na sieciach serwerów.** O ile MCS posiada różne systemy weryfikacji gracza (online-mode / whitelista), nie został przetestowany pod kątem ataków DoS, albo nieprawidłowych pakietów. W najgorszym wypadku proces MCS'a w takim przypadku może się zamknąć (a serwer uruchomić na następne 10 minut), ale mimo to nie radziłbym uruchamiać MCS'a w środowiskach poza zamkniętym gronem znajomych graczy.

MCS nie jest dodatkowym zabezpieczeniem serwera – jest tylko à la monitorem, który pilnuje uruchamiania i zamykania serwera. Jedyny system weryfikacji uprawnień do startu serwera to ustawienia Twojego serwera Minecraft.

Systemy weryfikacji bazują się na pliku `server.properties` (posortowane w kolejności od najbezpieczniejszego do najmniej bezpiecznego):

| opcje z pliku `server.properties`                 | premium | whitelista | sposób działania                                                                                                                           |
|---------------------------------------------------|---------|------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| `online-mode=true`<br/>`enforce-whitelist=true`   | ✓       | ✓          | sprawdzany jest status gracza (premium / cracked) i whitelista (z pliku `whitelist.json`) – w identyczny sposób jak na samym serwerze      |
| `online-mode=true`<br/>`enforce-whitelist=false`  | ✓       | ✘          | sprawdzany jest status gracza (premium / cracked) i jeśli gracz jest premium, serwer zostaje odpalony                                      |
| `online-mode=false`<br/>`enforce-whitelist=true`  | ✘       | ✓          | sprawdzana jest tylko whitelista (z pliku `whitelist.json`) – po nicku, status gracza nie (co oznacza że gracz może ustawić dowolny nick!) |
| `online-mode=false`<br/>`enforce-whitelist=false` | ✘       | ✘          | totalny brak weryfikacji: ktoś wchodzi na serwer? odpalamy!                                                                                |

## Instalacja i użycie
Do uruchomienia serwera wymagana jest Java 17.

#### Wspierane wersje Minecrafta
| Komponent | Wersja               |
|-----------|----------------------|
| Plugin    | Spigot / Paper 1.19+ |
| Mod       | Forge 1.19.2         |
| Serwer    | 1.12.2 – 1.20.1      |

1. Pobierz najnowszą wersję monitora z zakładki [_Wydania_](https://github.com/fratik/mcs/releases)
2. Wrzuć jarkę z monitorem do folderu ze swoim serwerem Minecraft oraz do folderu `plugins/` lub `mods/` (jeśli jesteś na Linuxie, użyj symbolicznego dowiązania – będzie wygodniej aktualizować)
3. Przygotuj skrypt na wzór przykładowego skryptu wyżej, którego będziesz używać do uruchamiania serwera
4. Upewnij się, że ustawienia Twojego serwera pokrywają się z plikiem `server.properties` (np. jeśli włączyłeś/aś whitelistę na serwerze komendą `/whitelist on` upewnij się, że w `server.properties` jest `enforce-whitelist=on` oraz że odpowiedni gracze są w pliku `whitelist.json`)
5. Uruchom skrypt przygotowany w pkt 3
6. Profit???

#### Jak edytować whitelistę?
1. Wejdź na serwer
   1. jeśli był on wyłączony i uruchomił go MCS, wróć do kroku 1.
2. Wpisz `/whitelist add/remove nazwaużytkownika`.
   1. (nie, poważnie, tu nie ma żadnej filozofii – whitelista MCS'a synchronizuje się z whitelistą serwera Minecraft)

#### Jak zamknąć serwer?
`/stop` zamyka serwer jak zawsze, natomiast skrypt przygotowany w punkcie czwartym powoduje, że jeśli serwer się zamknie, to zostanie uruchomiony serwer MCS'a. Wystarczy, że zamkniesz okienko ze skryptem lub użyjesz Ctrl+C w konsoli ze skryptem żeby go zakończyć.

## Środowisko dev
(aka jak zbuildować projekt???)

- `gradlew.bat build` (Windows)
- `./gradlew build` (UNIX)

Jarki znajdą się w `build/libs/` i `forge/build/libs/`. Zalecane użycie jarki bez dopisków (np. `mcs-1.0.jar`, nie `mcs-1.0-dev-all.jar`)