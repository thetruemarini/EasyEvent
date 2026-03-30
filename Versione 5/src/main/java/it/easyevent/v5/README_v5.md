# EasyEvent – Versione 5

## Novità rispetto alla Versione 4

La Versione 5 aggiunge la modalità **batch** al back-end del configuratore.
Il configuratore può importare categorie, campi e proposte di iniziative leggendole
da uno o più file di testo, senza dover inserire ogni dato manualmente via CLI.
La modalità interattiva (tutte le funzionalità delle versioni precedenti) rimane
pienamente operativa.

### Classi nuove

| Classe | Ruolo |
|--------|-------|
| `BatchImporter` | Legge e interpreta i file batch, invoca il controller |
| `BatchRisultato` | Raccoglie il resoconto di un'importazione (successi, errori) |

### Classi modificate

- **`ConfiguratoreController`**: aggiunto `importaBatch(path)` che delega a `BatchImporter`
- **`ConfiguratoreView`**: aggiunta voce "9. Importa da file batch"

---

## Formato dei file batch

I file batch sono file di testo (`*.txt` o `*.batch`) con la seguente sintassi:

```
# Commento (righe che iniziano con # sono ignorate)
# Le righe vuote sono ignorate

# ─── CAMPO COMUNE ───────────────────────────────────────────────
# CAMPO_COMUNE | <nome> | <obbligatorio: si/no>
CAMPO_COMUNE | Durata | no
CAMPO_COMUNE | Compreso nella quota | no
CAMPO_COMUNE | Ora conclusiva | no
CAMPO_COMUNE | Note | no

# ─── CATEGORIA ──────────────────────────────────────────────────
# CATEGORIA | <nome> [| CAMPO_SPECIFICO | <nome> | <obbligatorio>]*
# I campi specifici sono facoltativi; se assenti la categoria è creata senza.
CATEGORIA | Sport
CATEGORIA | Arte | CAMPO_SPECIFICO | Certificato medico | si
CATEGORIA | Gite | CAMPO_SPECIFICO | Mezzo di trasporto | no | CAMPO_SPECIFICO | Guida | si

# ─── PROPOSTA ───────────────────────────────────────────────────
# PROPOSTA | <categoria> | <campo1> = <valore1> | <campo2> = <valore2> | ...
# I nomi dei campi devono corrispondere esattamente (case-insensitive).
# La proposta è validata e, se valida, pubblicata direttamente in bacheca.
# Se non valida, viene segnalata come errore e NON salvata.
PROPOSTA | Sport | Titolo = Torneo di Padel | Numero di partecipanti = 8 | Termine ultimo di iscrizione = 30/06/2026 | Luogo = Palestra Centrale | Data inizio = 10/07/2026 | Ora = 09:00 | Quota individuale = 15 | Data conclusiva = 10/07/2026 | Certificato medico = Si
```

### Regole di parsing

1. Il separatore è ` | ` (spazio-pipe-spazio). Valori che contengono `|` non sono supportati.
2. Il primo token di ogni riga (dopo il trim) determina il tipo di comando.
3. Comandi non riconosciuti vengono segnalati come warning e ignorati.
4. Se un campo comune o una categoria già esiste, l'operazione viene saltata con un warning.
5. Le proposte sono validate con le stesse regole della modalità interattiva.
   Solo le proposte valide vengono pubblicate in bacheca; le non valide generano un errore.
6. L'elaborazione continua anche in caso di errori su singole righe.

---

## Compilazione e avvio

### Linux / macOS
```bash
chmod +x run.sh && ./run.sh
```

### Windows
```cmd
run.bat
```

### Manuale
```bash
mkdir -p out
find src -name "*.java" > sources.txt
javac -encoding UTF-8 -d out @sources.txt
java -ea -cp out it.easyevent.v5.MainV5
```

---

## Credenziali demo

| Username | Password |
|----------|----------|
| mario    | mario123 |
| laura    | laura456 |

---

## File batch di esempio

Nella cartella `batch_examples/` sono presenti tre file pronti per la demo:

- `campi_e_categorie.batch` – definisce campi comuni e categorie
- `proposte.batch` – crea e pubblica proposte (richiede che categorie esistano)
- `setup_completo.batch` – file unico con campi, categorie e proposte

---

## Scelte implementative documentate

1. **Formato batch proprietario** (separatore ` | `): più leggibile di CSV
   (che potrebbe richiedere quoting dei valori), più semplice di JSON/XML per
   l'uso manuale. Limitazione: i valori dei campi non possono contenere ` | `.

2. **Proposte batch pubblicate direttamente**: poiché le sessioni batch non
   sopravvivono al logout e il requisito richiede di importare anche proposte,
   le proposte valide vengono pubblicate immediatamente in bacheca (stesso
   comportamento di `pubblicaProposta` nella modalità interattiva).

3. **Importazione atomica per riga, non per file**: ogni riga viene elaborata
   indipendentemente. Un errore su una riga non blocca le righe successive.
   Al termine dell'importazione viene mostrato un resoconto (righe elaborate,
   successi, warning, errori).

4. **Il configuratore deve essere loggato**: l'importazione batch è una
   operazione privilegiata; viene rifiutata se nessun configuratore è loggato.

5. **Compatibilità piena con V4**: il formato del file di persistenza non cambia.
   I file JSON prodotti da V5 sono leggibili da V4 e viceversa.