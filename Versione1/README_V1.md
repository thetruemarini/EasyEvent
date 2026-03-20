# EasyEvent – Versione 1
**Progetto Ingegneria del Software – A.A. 2025-2026**

---

## Descrizione
Sistema software per la gestione di iniziative ricreative/culturali.  
La **Versione 1** implementa il back-end riservato ai soli configuratori.

---

## Requisiti
- **Java JDK 21** (o superiore) installato e nel PATH
- Sistema operativo: Windows / Linux / macOS

---

## Struttura del progetto

```
EasyEvent/
├── src/main/java/it/easyevent/
│   ├── MainV1.java                          ← Entry point V1
│   ├── model/
│   │   ├── Campo.java                       ← Entità campo (BASE/COMUNE/SPECIFICO)
│   │   ├── Categoria.java                   ← Entità categoria
│   │   ├── Configuratore.java               ← Entità configuratore
│   │   └── AppData.java                     ← Stato centrale (Singleton)
│   ├── controller/
│   │   └── ConfiguratoreController.java     ← Logica di business V1
│   ├── persistence/
│   │   └── PersistenceManager.java          ← Salvataggio/caricamento JSON
│   └── view/
│       └── ConfiguratoreView.java           ← Interfaccia testuale (CLI)
├── data/
│   └── easyevent_data.json                  ← File di persistenza (generato a runtime)
├── run.sh                                   ← Script build+run Linux/macOS
└── run.bat                                  ← Script build+run Windows
```

---

## Come eseguire

### Linux / macOS
```bash
chmod +x run.sh
./run.sh
```

### Windows
```
run.bat
```

### Manuale (qualsiasi OS)
```bash
mkdir out data
find src -name "*.java" > sources.txt   # Linux/macOS
# oppure: dir /s /b src\*.java > sources.txt   (Windows)

javac -d out -encoding UTF-8 @sources.txt
java -ea -cp out it.easyevent.MainV1
```

---

## Credenziali predefinite (primo avvio)
- **Username:** `admin`
- **Password:** `admin123`

Al primo accesso è obbligatorio scegliere credenziali personali.

---

## Funzionalità Versione 1

| Funzionalità | Descrizione |
|---|---|
| Login configuratore | Con credenziali predefinite al primo accesso |
| Cambio credenziali | Obbligatorio al primo accesso |
| Campi base | Visualizzazione (immutabili, già definiti dal sistema) |
| Campi comuni | Aggiunta, rimozione, modifica obbligatorietà |
| Categorie | Aggiunta, rimozione, visualizzazione |
| Campi specifici | Per ogni categoria: aggiunta, rimozione, modifica obbligatorietà |
| Persistenza JSON | Tutti i dati salvati in `data/easyevent_data.json` |

---

## Campi base predefiniti (immutabili per specifica)
1. Titolo *(obbligatorio)*
2. Numero di partecipanti *(obbligatorio)*
3. Termine ultimo di iscrizione *(obbligatorio)*
4. Luogo *(obbligatorio)*
5. Data *(obbligatorio)*
6. Ora *(obbligatorio)*
7. Quota individuale *(obbligatorio)*
8. Data conclusiva *(obbligatorio)*

---

## Scelte implementative documentate

1. **Credenziali predefinite univoche**: interpretazione semplice (nota 4 del documento). Username `admin`, password `admin123`, fissate a compile-time in `AppData`.

2. **Persistenza**: file JSON minimale senza librerie esterne (parser manuale). Il file viene creato in `data/easyevent_data.json`.

3. **Architettura MVC**: separazione netta tra Model (`model/`), Controller (`controller/`) e View (`view/`). La View non conosce il Model direttamente, per facilitare la futura sostituzione con una GUI (estensione futura prevista dal documento).

4. **Pattern Singleton** su `AppData`: garantisce un unico stato centralizzato dell'applicazione.

5. **Precondizioni/Postcondizioni/Invarianti**: presenti su tutte le classi del model e del controller, usando `assert` Java (attivate con il flag `-ea` in esecuzione) e `IllegalArgumentException` per input non validi.

6. **Nomi campi case-insensitive**: i confronti tra nomi di campi e categorie sono case-insensitive per robustezza.

7. **Unicità username globale**: per preparare l'integrazione di fruitori (V3), `AppData.esisteUsername()` è progettato per verificare l'unicità su tutta l'utenza (configuratori + fruitori nelle versioni successive).

---

## Note per la demo orale
Alla prima esecuzione vengono creati i campi base e salvati automaticamente.  
Nelle esecuzioni successive i dati vengono caricati da file JSON.  
Per popolare i dati demo, eseguire l'applicazione e inserire categorie/campi via CLI, oppure modificare direttamente `data/easyevent_data.json`.
