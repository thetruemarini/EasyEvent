# EasyEvent – Versione 4

## Novità rispetto alla Versione 3

La Versione 4 introduce il **ritiro delle proposte** da parte del configuratore
e la **disdetta dell'iscrizione** da parte del fruitore, completando il ciclo
di vita delle proposte con le ultime transizioni manuali.

### Nuove classi

Nessuna nuova classe: tutte le funzionalità V4 sono implementate estendendo
le classi esistenti.

### Classi estese

- **`StatoProposta`**: lo stato `RITIRATA`, già previsto come segnaposto in V3,
  è ora pienamente attivabile dal configuratore.
- **`Proposta`**: aggiunto `verificaRitiroConsentito()` per controllare se il
  ritiro è ancora consentito nella data corrente; `rimuoviAderente()` esteso
  con il supporto alla disdetta (era già presente ma non esposto in V3).
- **`AppData`**: aggiunto `ritirareProposta()` che esegue la transizione
  APERTA/CONFERMATA → RITIRATA e invia le notifiche agli aderenti;
  aggiunto `getPropostaDaArchivio()` per ricercare una proposta per ID.
- **`ConfiguratoreController`**: aggiunto `ritirareProposta()` con gestione
  rollback su errore di I/O.
- **`FruitoreController`**: aggiunto `disdiciIscrizione()` con gestione
  rollback su errore di I/O.
- **`ConfiguratoreView`**: aggiunta voce di menu "8. Ritira proposta" con
  elenco delle proposte ritirabili e conferma esplicita.
- **`FruitoreView`**: aggiunta voce di menu "3. Disdici iscrizione" con
  possibilità di re-iscrizione alla stessa proposta.
- **`MainV4`**: punto di ingresso aggiornato; logica di avvio identica alla V3.

### Nuovi casi d'uso V4 lato configuratore

- **Ritira proposta**: il configuratore può ritirare una proposta
  APERTA o CONFERMATA per cause di forza maggiore. Il ritiro è consentito fino
  alle ore 23:59 del giorno precedente la "Data" dell'iniziativa.
  Tutti gli aderenti ricevono automaticamente una notifica di ritiro nello
  spazio personale.

### Nuovi casi d'uso V4 lato fruitore

- **Disdici iscrizione**: il fruitore può annullare la propria
  iscrizione a una proposta APERTA, purché il "Termine ultimo di iscrizione"
  non sia ancora scaduto. Dopo la disdetta è possibile re-iscriversi alla
  stessa proposta (rispettando il termine).

### Transizione RITIRATA

| Da         | A        | Attore         | Condizione                                     |
|------------|----------|----------------|------------------------------------------------|
| APERTA     | RITIRATA | Configuratore  | oggi < Data dell'iniziativa                    |
| CONFERMATA | RITIRATA | Configuratore  | oggi < Data dell'iniziativa                    |

Alla transizione verso RITIRATA vengono automaticamente inviate notifiche
nello spazio personale di tutti i fruitori iscritti alla proposta.

### Compatibilità retroattiva

Il file JSON prodotto dalla V3 (e dalla V2) viene letto correttamente: il
formato è identico tra V3 e V4 poiché lo stato `RITIRATA` era già supportato
dal parser della V3. Non è necessaria alcuna migrazione.

### Requisiti aggiuntivi / scelte implementative

1. **Granularità del ritiro**: i requisiti stabiliscono il limite di ritiro alle
   "ore 23:59" del giorno precedente alla "Data"; l'applicazione non gestisce
   l'ora (solo la data), quindi la condizione diventa `oggi < getData()` (strettamente
   minore). Questa scelta è coerente con i requisiti non funzionali che non
   impongono la gestione dell'ora.

2. **Irreversibilità del ritiro**: una proposta RITIRATA non può transitare in
   nessun altro stato. Il configuratore viene avvisato esplicitamente della
   natura irreversibile dell'operazione e deve confermarla prima che venga
   eseguita.

3. **Re-iscrizione dopo disdetta**: dopo aver disdetto, il fruitore può
   re-iscriversi alla stessa proposta nelle stesse condizioni (stato APERTA,
   termine non scaduto, posti disponibili). Non viene applicato alcun limite
   al numero di disdette e re-iscrizioni successive.

4. **Rollback transazionale**: come nelle versioni precedenti, ogni operazione
   che modifica lo stato in memoria e poi salva su disco esegue un rollback
   esplicito in caso di errore di I/O. Per `ritirareProposta()`, in caso di
   fallimento del salvataggio, viene ricaricato l'intero stato dal file su disco
   (non ancora modificato), ripristinando sia la proposta che le notifiche degli
   aderenti allo stato pre-ritiro.

---

## Struttura del progetto

```
Versione 4/
├── README_V4.md
├── run.sh / run.bat
├── sources.txt
├── data/
│   └── easyevent_data.json
└── src/main/java/it/easyevent/v4/
    ├── MainV4.java
    ├── controller/
    │   ├── ConfiguratoreController.java
    │   └── FruitoreController.java
    ├── model/
    │   ├── AppData.java
    │   ├── Campo.java
    │   ├── Categoria.java
    │   ├── Configuratore.java
    │   ├── Fruitore.java
    │   ├── Notifica.java
    │   ├── Proposta.java
    │   └── StatoProposta.java
    ├── persistence/
    │   └── PersistenceManager.java
    └── view/
        ├── ConfiguratoreView.java
        └── FruitoreView.java
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
registrarsi, iscriversi alle proposte in bacheca o disdire un'iscrizione già
effettuata. Il configuratore può ritirare proposte APERTE o CONFERMATE tramite
la voce di menu **"8. Ritira proposta"**.