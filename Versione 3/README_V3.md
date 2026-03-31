# EasyEvent – Versione 3

## Novità rispetto alla Versione 2

La Versione 3 introduce il **fruitore** e il **front-end** dell'applicazione, rendendo
la bacheca visibile e interagibile anche da parte degli utenti finali.

### Nuove classi

|        Classe        |                                 Ruolo                                       |
|----------------------|-----------------------------------------------------------------------------|
| `Fruitore`           | Utente del front-end; gestisce credenziali e notifiche personali            |
| `Notifica`           | Messaggio automatico recapitato al fruitore al cambio stato di una proposta |
| `FruitoreController` | Logica di registrazione, login, iscrizione e gestione notifiche             |
| `FruitoreView`       | Interfaccia testuale (CLI) del front-end                                    |

### Classi estese

- **`StatoProposta`**: aggiunti `CONFERMATA`, `ANNULLATA`, `CONCLUSA`
  (e `RITIRATA` come segnaposto per la V4, non ancora attivabile dall'utente)
- **`Proposta`**: aggiunti `aderenti`, `storicoStati`, `aggiungiAderente()`,
  `transitaStato()`, `isIscrizioneAperta()`, `getTermineIscrizione()`,
  `getDataConclusiva()`, `getNumeroMaxPartecipanti()`
- **`AppData`**: aggiunti `fruitori`, `prossimoIdNotifica`, `aggiornaTransizioni()`,
  `esisteUsernameGlobale()`, `notificaAderenti()`; la "bacheca" di V2 è ora
  "archivio" (contiene tutti gli stati, non solo APERTA)
- **`PersistenceManager`**: serializza/deserializza fruitori, notifiche, aderenti,
  storicoStati; legge sia `"archivio"` (V3) sia `"bacheca"` (compat. V2)
- **`ConfiguratoreController`**: aggiunto `getArchivio()`, `aggiornaTransizioni()`
- **`ConfiguratoreView`**: aggiunta voce di menu "7. Visualizza archivio proposte"
- **`MainV3`**: avvia il menu di selezione ruolo (Configuratore / Fruitore)
  e applica le transizioni automatiche di stato all'avvio

### Nuovo caso d'uso V3 lato configuratore

- **Visualizza archivio proposte**: il configuratore può consultare
  tutte le proposte pubblicate con il loro stato corrente (APERTA, CONFERMATA,
  ANNULLATA, CONCLUSA), raggruppate per stato.

### Nuovi casi d'uso V3 lato fruitore

- **Registrazione**: primo accesso; il fruitore sceglie username e
  password. Lo username deve essere univoco tra tutti gli utenti (configuratori
  e fruitori).
- **Login**: accesso con credenziali personali (massimo 3 tentativi).
- **Visualizza bacheca**: visualizzazione delle proposte APERTE,
  categoria per categoria.
- **Aderisci a una proposta**: iscrizione a una proposta aperta,
  possibile solo fino alle ore 23:59 del "Termine ultimo di iscrizione" e solo
  se non è stato raggiunto il numero massimo di partecipanti.
- **Spazio personale**: consultazione e cancellazione delle notifiche.

### Transizioni automatiche di stato

All'avvio dell'applicazione (da qualsiasi utente) vengono applicate le transizioni
pendenti, gestendo il caso in cui l'app non fosse in esecuzione alla mezzanotte:

|     Da     |     A      |                      Condizione                               |
|------------|------------|---------------------------------------------------------------|
| APERTA     | CONFERMATA | oggi > Termine iscrizione AND aderenti >= Numero partecipanti |
| APERTA     | ANNULLATA  | oggi > Termine iscrizione AND aderenti < Numero partecipanti  |
| CONFERMATA | CONCLUSA   | oggi > Data conclusiva                                        |

Alla transizione APERTA→CONFERMATA o APERTA→ANNULLATA vengono automaticamente
inviate notifiche nello spazio personale di tutti i fruitori iscritti.

### Compatibilità retroattiva

Il file JSON prodotto dalla V2 (campo `"bacheca"`) viene letto correttamente:
le proposte vengono migrate nell'archivio V3 con aderenti e storico vuoti.

### Requisiti aggiuntivi / scelte implementative

1. **Granularità delle iscrizioni**: i requisiti stabiliscono il limite di
   iscrizione alle "ore 23:59"; l'applicazione non gestisce l'ora (solo la data),
   quindi un fruitore può iscriversi in qualsiasi momento del giorno indicato
   come "Termine ultimo di iscrizione". Questa scelta è coerente con i requisiti
   non funzionali che non impongono la gestione dell'ora.

2. **Unicità username**: lo username di ogni fruitore è univoco tra tutti gli
   utenti del sistema (configuratori inclusi), con confronto case-insensitive.

3. **Rollback transazionale**: ogni operazione che modifica lo stato in memoria
   e poi salva su disco esegue un rollback esplicito in caso di errore di I/O,
   garantendo la coerenza tra memoria e persistenza.

4. **`getNumeroMaxPartecipanti()` restituisce `-1`** in caso di campo non
   parsabile (dati corrotti): in questo caso `aggiungiAderente()` rifiuta
   l'iscrizione e `aggiornaTransizioni()` tratta la proposta come ANNULLATA,
   rendendo visibile l'anomalia invece di silenziare l'errore.

---

## Struttura del progetto

```
Versione 3/
├── README_V3.md
├── run.sh / run.bat
├── sources.txt
├── data/
│   └── easyevent_data.json
└── src/main/java/it/easyevent/
    ├── MainV3.java
    ├── controller/
    │   ├── ConfiguratoreController.java
    │   └── FruitoreController.java        ← NUOVO
    ├── model/
    │   ├── AppData.java
    │   ├── Campo.java
    │   ├── Categoria.java
    │   ├── Configuratore.java
    │   ├── Fruitore.java                  ← NUOVO
    │   ├── Notifica.java                  ← NUOVO
    │   ├── Proposta.java
    │   └── StatoProposta.java
    ├── persistence/
    │   └── PersistenceManager.java
    └── view/
        ├── ConfiguratoreView.java
        └── FruitoreView.java              ← NUOVO
```

---

## Esecuzione

**Linux / macOS:**
```bash
chmod +x run.sh && ./run.sh
```

**Windows:**
```cmd
run.bat
```

Al primo avvio selezionare **"1. Configuratore"** e autenticarsi con le credenziali
predefinite (`admin` / `admin123`) per impostare le categorie e creare le prime
proposte. Successivamente sarà possibile accedere come **"2. Fruitore"** per
registrarsi e iscriversi alle proposte in bacheca.
