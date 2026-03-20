# EasyEvent – Versione 2

## Novità rispetto alla Versione 1

La Versione 2 aggiunge la gestione delle proposte di iniziative:

- **Creazione di proposte**: il configuratore sceglie una categoria e compila i
  campi (base + comuni + specifici) della proposta.
- **Validazione automatica**: il sistema assegna lo stato `VALIDA` non appena
  tutti i campi obbligatori sono compilati e i vincoli di data sono rispettati:
  - `Termine ultimo di iscrizione` > data odierna
  - `Data` ≥ `Termine ultimo di iscrizione` + 2 giorni
  - `Data conclusiva` ≥ `Data`
- **Pubblicazione in bacheca**: una proposta `VALIDA` può essere pubblicata
  dal configuratore; diventa `APERTA` e viene salvata su file.
- **Proposte di sessione**: le proposte non pubblicate (`BOZZA` / `VALIDA`)
  esistono solo in memoria durante la sessione e vengono scartate al logout.
- **Bacheca**: visualizzazione delle proposte aperte, organizzata per categoria.

## File nuovi / modificati

| File | Stato |
|------|-------|
| `model/StatoProposta.java`      | **NUOVO**     |
| `model/Proposta.java`           | **NUOVO**     |
| `model/AppData.java`            | **MODIFICATO** (aggiunta bacheca) |
| `persistence/PersistenceManager.java` | **MODIFICATO** (aggiunta serializzazione proposte) |
| `controller/ConfiguratoreController.java` | **MODIFICATO** (nuovi metodi per proposte) |
| `view/ConfiguratoreView.java`   | **MODIFICATO** (menu 5 e 6) |
| `MainV2.java`                   | **NUOVO**     |
| `model/Campo.java`              | invariato da V1 |
| `model/Categoria.java`          | invariato da V1 |
| `model/Configuratore.java`      | invariato da V1 |

## Compilazione e avvio

### Linux / macOS
```bash
chmod +x run.sh
./run.sh
```

### Windows
```batch
run.bat
```

### Manuale
```bash
mkdir out
find src -name "*.java" > sources.txt
javac -encoding UTF-8 -d out @sources.txt
java -cp out it.easyevent.MainV2
```

## Credenziali demo (file dati pre-popolato)

| Username | Password |
|----------|----------|
| mario    | mario123 |
| laura    | laura456 |

## Formato date

Tutte le date vanno inserite nel formato **gg/mm/aaaa** (es. `25/04/2026`).

## Dati demo pre-caricati

Il file `data/easyevent_data.json` contiene:
- 2 configuratori: `mario`, `laura`
- 3 categorie: `sport`, `gite`, `arte` (con campi specifici)
- 4 campi comuni: `Durata`, `Compreso nella quota`, `Ora conclusiva`, `Note`
- 3 proposte aperte in bacheca (una per categoria)
