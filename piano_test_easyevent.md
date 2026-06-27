# Piano completo dei test — EasyEvent (refactoring parte B)

Questo piano applica al progetto i principi visti nell'esercitazione di testing con il professore: **piramide dei test** (molti unit test alla base, pochi test di integrazione sopra), **struttura Arrange-Act-Assert**, **iniezione del tempo** (mai `LocalDate.now()` dentro un test), **test double** per le dipendenze verso il mondo esterno (file system), e nomenclatura parlante dei metodi di test.

Per ogni sezione trovi: (1) **il principio** che giustifica l'intervento, (2) **cosa testare** con file path e firme reali del codice, (3) i **casi di test** concreti, (4) le **note di verifica**. Alla fine c'è la parte infrastrutturale (come compilare ed eseguire JUnit senza Maven) e l'ordine di esecuzione consigliato.

---

## 0. Inquadramento: la piramide applicata a EasyEvent

**Principio (sbobina, pag. 11-12).** La piramide distribuisce lo sforzo: alla base molti **unit test** su logica pura in memoria (rapidi, deterministici, isolati dall'esterno); al centro i **test di integrazione** che toccano risorse esterne (file system); in cima, pochissimi **end-to-end / smoke test**. «Testare logica pura e calcoli che risiedono interamente in memoria è relativamente semplice e veloce. La complessità aumenta non appena si devono coinvolgere risorse esterne.»

Mappando il progetto su questa griglia:

| Livello | Classi del progetto | Perché qui |
|---|---|---|
| **Unit (base, la maggioranza)** | `Proposta`, `AppData`, `Categoria`, `Campo`/`CampoBase`/`CampoComune`/`CampoSpecifico`, `Username`, `IdProposta`, `IdNotifica`, `Configuratore`, `Fruitore`, `Notifica` | Logica di dominio pura, nessuna I/O. Il tempo è già un parametro (`LocalDate dataOggi`), quindi sono deterministici per costruzione. |
| **Integrazione (centro)** | `PersistenceManager`, `BatchImporter` | Toccano il file system (lettura/scrittura). Vanno testati con file temporanei, non con mock del filesystem. |
| **End-to-end / smoke (cima, pochi)** | flusso `MainV5` → controller → persistence | Solo 1-2 smoke test «il sistema non sta fumando»: avvio, una proposta pubblicata, salvataggio+ricarica coerente. |

I `Controller` e le `View` **non** sono il bersaglio principale: dipendono da `Scanner`/`System.out` (interazione utente). La sbobina insegna proprio a spostare la logica fuori da questi strati. Dopo il refactoring parte B, la regola di business vive in `AppData` (`categoriaUsataInSessione`, `campoUsatoInSessione`) ed è quindi testabile in unità senza passare dal controller. Questo è esattamente l'esito desiderato dal corso: il test diventa possibile *perché* il design è disaccoppiato.

**Nota sul TDD a posteriori.** Il codice esiste già, quindi qui non si fa TDD ortodosso (test-prima). Si scrivono *characterization test* che fissano il comportamento attuale: la sbobina (pag. 7) li giustifica come rete di sicurezza per il refactoring — «è possibile effettuare modifiche strutturali al codice […] con la sicurezza di poter eseguire immediatamente l'intera suite di test». Sono esattamente ciò che protegge l'introduzione del pattern State su `Proposta` (punto 4 del piano di refactoring).

---

## 1. `Proposta` — il SUT più importante (macchina a stati + validazione)

**Principio.** È la classe con più logica pura del sistema: validazione, ciclo di vita a 7 stati, gestione aderenti, ritiro. Tutto il tempo è iniettato (`aggiornaStato(LocalDate)`, `transitaStato(StatoProposta, LocalDate)`, `aggiungiAderente(String, LocalDate)`), quindi i test sono deterministici senza alcun test double (pag. 19, 25: «iniettare il tempo come dipendenza anziché accedervi direttamente»). Questa è la classe il cui pattern State verrà introdotto: **i test qui sono la garanzia che il refactoring State preservi il comportamento**.

**File di test:** `test/easyevent/proposta/PropostaTest.java`

### 1a. Costruzione e invariante

Codice sotto test (`src/easyevent/proposta/Proposta.java`, costruttore principale e `repOk()`):

```java
public Proposta(IdProposta id, String nomeCategoria, String usernameCreatore,
        LinkedHashMap<String, Boolean> campiOrdinati) { ... }
```

Casi:
- `costruttore_DatiValidi_StatoInizialeBozza` → dopo costruzione `getStato() == BOZZA` e `getDataPubblicazione() == null`.
- `costruttore_IdNull_LanciaIllegalArgument`.
- `costruttore_CategoriaBlank_LanciaIllegalArgument`.
- `costruttore_CreatoreBlank_LanciaIllegalArgument`.
- `costruttore_CampiNull_LanciaIllegalArgument`.
- `costruttore_InizializzaValoriVuotiPerOgniCampo` → ogni campo presente in `getNomiCampi()` ha `getValore(campo).isEmpty()`.

### 1b. Validazione (`validazioneErrori` / `aggiornaStato`)

Principio: è la logica più ricca e va coperta caso per caso, un comportamento per test. Il bersaglio è il tipo di `ErroreValidazione.Tipo` restituito (struttura, non testo italiano).

Codice: `public List<ErroreValidazione> validazioneErrori(LocalDate dataOggi)` e `public void aggiornaStato(LocalDate dataOggi)`.

Casi (ognuno: Arrange una proposta con i campi giusti, Act `validazioneErrori(oggi)`, Assert sul `Tipo` presente/assente):
- `validazione_CampoObbligatorioVuoto_RestituisceErrore` → tipo `CAMPO_OBBLIGATORIO_VUOTO`.
- `validazione_TuttiICampiObbligatoriValorizzati_NessunErrore` → lista vuota.
- `validazione_DataFormatoErrato_RestituisceErrore` → tipo `DATA_FORMATO_NON_VALIDO` (es. `Data inizio = "31-13-2026"`).
- `validazione_TermineNonFuturo_RestituisceErrore` → tipo `TERMINE_NON_FUTURO` (termine = oggi o passato).
- `validazione_DataInizioTroppoVicinaAlTermine_RestituisceErrore` → tipo `DATA_INIZIO_TROPPO_VICINA` (regola: data inizio ≥ termine + 2 giorni).
- `validazione_DataConclusivaPrimaDellInizio_RestituisceErrore` → tipo `DATA_CONCLUSIVA_PRECEDENTE`.
- `validazione_NumPartecipantiNonPositivo_RestituisceErrore` → tipo `NUM_PARTECIPANTI_NON_POSITIVO` (valore `"0"` o `"-3"`).
- `validazione_NumPartecipantiNonNumerico_RestituisceErrore` → tipo `NUM_PARTECIPANTI_NON_NUMERICO` (valore `"abc"`).
- `validazione_OraFormatoNonValido_RestituisceErrore` → tipo `ORA_FORMATO_NON_VALIDO` (`"25:00"`, `"10:70"`, `"1030"`, `"10:"`).
- `validazione_OraFormatoValido_NessunErroreOra` (`"09:30"`, `"23:59"`, `"0:0"`).
- `aggiornaStato_PropostaCompleta_DiventaValida` → da BOZZA a VALIDA.
- `aggiornaStato_PropostaIncompleta_RestaBozza`.
- `aggiornaStato_PropostaGiaPubblicata_NonRetrocede` → se APERTA/CONFERMATA/ecc., `aggiornaStato` non cambia lo stato.

> Suggerimento: estrai un metodo helper `proposataValida(LocalDate oggi)` nel test che costruisce una proposta con tutti i campi validi rispetto a `oggi`, così ogni caso modifica un solo campo (= un solo comportamento per test).

### 1c. Pubblicazione e revert

Codice: `pubblicaInBacheca(LocalDate)`, `revertToValida()`.

- `pubblica_DaValida_DiventaAperta` → stato APERTA, `dataPubblicazione` valorizzata, storico contiene un `CambioStato(APERTA, ...)`.
- `pubblica_DaBozza_LanciaModificaNonConsentita` → tipo `STATO_PROPOSTA_NON_VALIDO`.
- `pubblica_DataNull_LanciaIllegalArgument`.
- `revert_DaAperta_TornaValida` → stato VALIDA, `dataPubblicazione == null`, storico ridotto di 1.
- `revert_DaStatoNonAperto_LanciaModificaNonConsentita`.

### 1d. Transizioni di stato (cuore del futuro pattern State)

Principio: la tabella delle transizioni valide in `transitaStato` è esattamente la responsabilità che il pattern State sposterà nelle classi di stato. **Questi test devono restare verdi identici prima e dopo l'introduzione del pattern.**

Codice: `public void transitaStato(StatoProposta nuovoStato, LocalDate data)`.

Transizioni **valide** (Assert: nessuna eccezione, `getStato()` aggiornato, storico +1):
- `transizione_ApertaAConfermata_Consentita`
- `transizione_ApertaAdAnnullata_Consentita`
- `transizione_ApertaARitirata_Consentita`
- `transizione_ConfermataAConclusa_Consentita`
- `transizione_ConfermataARitirata_Consentita`

Transizioni **non valide** (Assert: `ModificaNonConsentitaException` con tipo `TRANSIZIONE_STATO_NON_VALIDA`) — almeno un campione rappresentativo:
- `transizione_BozzaAConfermata_Vietata`
- `transizione_ValidaAdAperta_Vietata` (la pubblicazione passa da `pubblicaInBacheca`, non da `transitaStato`)
- `transizione_ConclusaAQualsiasi_Vietata`
- `transizione_AnnullataAConfermata_Vietata`
- `transizione_RitirataAQualsiasi_Vietata`
- `transizione_StatoNull_LanciaIllegalArgument`, `transizione_DataNull_LanciaIllegalArgument`.

### 1e. Gestione aderenti (iscrizioni)

Codice: `aggiungiAderente(String, LocalDate)`, `rimuoviAderente(String, LocalDate)`, `isAderito`, `getNumAderenti`. Le eccezioni sono `IscrizioneException` con `TipoErrore`.

- `iscrizione_PropostaApertaIscrizioniAperte_AggiungeAderente` → `getNumAderenti() == 1`, `isAderito(u) == true`.
- `iscrizione_PropostaNonAperta_LanciaPropostaNonAperta` (stato BOZZA/VALIDA).
- `iscrizione_OltreTermine_LanciaIscrizioniChiuse` (oggi dopo `Termine ultimo di iscrizione`).
- `iscrizione_GiaIscritto_LanciaGiaIscritto`.
- `iscrizione_NumPartecipantiNonValido_LanciaNumNonValido` (campo `Numero di partecipanti` non numerico).
- `iscrizione_PostiEsauriti_LanciaPostiEsauriti` (riempire fino a `numMax`).
- `iscrizione_UsernameNullOBlank_LanciaIllegalArgument`.
- `rimozione_AderentePresente_LoRimuove` → `isAderito` torna false.
- `rimozione_AderenteNonIscritto_LanciaNonIscritto`.
- `rimozione_IscrizioniChiuse_LanciaIscrizioniChiuse`.
- `isAderito_CaseInsensitive` → iscritto come `"Mario"`, `isAderito("mario") == true` (coerente con la regola case-insensitive del dominio).

### 1f. Ritiro (`verificaRitiroConsentito`)

Codice: `public void verificaRitiroConsentito(LocalDate oggi)`, eccezione `RitiroNonConsensitoException`.

- `ritiro_PropostaApertaConDataFutura_Consentito` → nessuna eccezione.
- `ritiro_StatoNonRitirabile_LanciaStatoNonRitirabile` (es. BOZZA, CONCLUSA).
- `ritiro_DataEventoNonValorizzata_LanciaDataNonValorizzata`.
- `ritiro_DataEventoPassata_LanciaDataEventoPassata`.

### 1g. Protezione dei valori dopo pubblicazione

Codice: `setValore(String, String)`.

- `setValore_CampoInesistente_LanciaCampoNonPresente`.
- `setValore_PropostaInBozza_Consentito`.
- `setValore_PropostaPubblicata_LanciaPropostaGiaPubblicata` (stato APERTA → tipo `PROPOSTA_GIA_PUBBLICATA`).

---

## 2. `AppData` — orchestratore del dominio

**Principio.** Dopo il refactoring parte B, le regole di business («una categoria/campo è rimovibile solo se nessuna proposta di sessione la usa», transizioni automatiche alla scadenza) vivono qui, non nei controller. Sono logica pura in memoria → unit test classici. La notifica agli aderenti (`aggiornaTransizioni` → `notificaAderenti`) è un ottimo banco di prova: verifica che lo stato cambi **e** che il fruitore riceva la notifica, senza alcuna I/O.

**File di test:** `test/easyevent/core/AppDataTest.java`

### 2a. Registrazione utenti e unicità username

Codice: `aggiungiConfiguratore`, `aggiungiFruitore`, `esisteUsernameGlobale`.

- `aggiungiConfiguratore_NuovoUsername_LoAggiunge`.
- `aggiungiConfiguratore_UsernameNull_LanciaIllegalArgument`.
- `aggiungiConfiguratore_UsernameDuplicato_LanciaElementoGiaEsistente` (tipo `USERNAME`).
- `aggiungiFruitore_UsernameGiaUsatoDaConfiguratore_LanciaElementoGiaEsistente` → verifica unicità **globale** (config + fruitori condividono lo spazio nomi).
- `rimuoviFruitore_Esistente_RestituisceTrue` / `_Inesistente_RestituisceFalse`.

### 2b. Campi base e comuni

Codice: `inizializzaCampiBase`, `isCampiBaseInitialized`, `aggiungiCampoComune`, `rimuoviCampoComune`, `esisteCampoComune`, `modificaObbligatorietaCampoComune`.

- `inizializzaCampiBase_PrimaChiamata_CreaTuttiICampiBase` → `getCampiBase().size() == NOMI_CAMPI_BASE.length`.
- `inizializzaCampiBase_SecondaChiamata_NonRiInizializza` (idempotenza, `isCampiBaseInitialized()` già true).
- `aggiungiCampoComune_Nuovo_LoAggiunge`.
- `aggiungiCampoComune_Duplicato_LanciaElementoGiaEsistente`.
- `rimuoviCampoComune_Esistente_RestituisceTrue`.
- `modificaObbligatorietaCampoComune_Esistente_AggiornaFlag`.

### 2c. Categorie e regole di rimozione di sessione (chiave del refactoring B)

Codice:
```java
public boolean categoriaUsataInSessione(List<Proposta> proposteSessione, String nomeCategoria)
public boolean campoUsatoInSessione(List<Proposta> proposteSessione, String nomeCampo)
```

Principio: è la regola che la parte B ha spostato dal controller al model. Testarla qui dimostra che il refactoring ha raggiunto l'obiettivo (regola riusabile, testabile senza UI).

- `categoriaUsataInSessione_PropostaUsaCategoria_RestituisceTrue`.
- `categoriaUsataInSessione_NessunaProposta_RestituisceFalse`.
- `categoriaUsataInSessione_ListaNull_RestituisceFalse`.
- `categoriaUsataInSessione_NomeCaseInsensitive_RestituisceTrue`.
- `campoUsatoInSessione_PropostaUsaCampo_RestituisceTrue`.
- `campoUsatoInSessione_CampoNonUsato_RestituisceFalse`.
- `campoUsatoInSessione_NullSafe_RestituisceFalse`.

### 2d. Archivio, bacheca e filtri

Codice: `aggiungiPropostaAperta`, `pubblicaPropostaDiretta`, `getBacheca`, `getBachecaPerCategoria`, `getProposteRitirabili`, `getProposteIscrittoFruitore`, `getCategorieConProposteAperte`.

- `aggiungiPropostaAperta_StatoNonAperto_LanciaIllegalArgument` (solo APERTE entrano in archivio).
- `pubblicaPropostaDiretta_PropostaValida_RestituisceListaVuotaEEntraInBacheca`.
- `pubblicaPropostaDiretta_PropostaNonValida_RestituisceErrori` (lista non vuota, proposta NON in bacheca).
- `getBacheca_SoloProposteAperte` (mescolare stati e verificare il filtro).
- `getBachecaPerCategoria_FiltraPerCategoria`.
- `getCategorieConProposteAperte_OrdinateAlfabeticamente_SenzaDuplicati`.
- `getProposteIscrittoFruitore_SoloProposteApertaConIscritto`.

### 2e. Transizioni automatiche + notifiche (test ricco, ancora unit)

Codice: `public int aggiornaTransizioni(LocalDate oggi)` (+ `notificaAderenti` privato, osservato tramite le notifiche del fruitore).

Principio: simile al kata «birthday greetings» — la data «oggi» è iniettata, quindi posso forzare la scadenza. Verifico **due cose insieme**: la transizione di stato e l'effetto collaterale (notifica al fruitore).

- `aggiornaTransizioni_ApertaScadutaConPostiPieni_DiventaConfermata` → stato CONFERMATA, e ogni aderente (Fruitore in AppData) ha una `Notifica` con tipo `PROPOSTA_CONFERMATA`.
- `aggiornaTransizioni_ApertaScadutaSenzaPostiPieni_DiventaAnnullata` → tipo notifica `PROPOSTA_ANNULLATA`.
- `aggiornaTransizioni_ApertaNonAncoraScaduta_RestaAperta` → 0 modifiche, 0 notifiche.
- `aggiornaTransizioni_ConfermataDopoDataConclusiva_DiventaConclusa`.
- `aggiornaTransizioni_RestituisceNumeroProposteModificate`.
- `ritirareProposta_PropostaAperta_DiventaRitirataENotifica` → stato RITIRATA, aderenti notificati con `PROPOSTA_RITIRATA`.

> Setup tipo: in `AppData` registri un `Fruitore`, crei una `Proposta` APERTA con `Termine` scaduto rispetto a `oggi`, iscrivi il fruitore, chiami `aggiornaTransizioni(oggi)`, poi controlli `getFruitore(u).getNotifiche()`.

### 2f. Generatori di ID e invariante

- `getNuovoIdProposta_IncrementaContatore` → due chiamate restituiscono ID consecutivi e distinti.
- `getNuovoIdNotifica_IncrementaContatore`.
- `repOk_DopoOperazioniValide_RestaVero` (sanity check dell'invariante dopo una sequenza di operazioni).

---

## 3. Gerarchia `Campo` e `Categoria`

**Principio.** Polimorfismo (`setObbligatorio` differente per tipo) e value semantics (`equals`/`hashCode` case-insensitive). Logica pura: unit test diretti. La sbobina insegna a testare *un comportamento alla volta*: qui ogni sottoclasse ha la sua regola.

**File:** `test/easyevent/categoria/CampoTest.java`, `test/easyevent/categoria/CategoriaTest.java`

### 3a. `Campo` e sottoclassi

- `campoBase_SetObbligatorio_LanciaModificaNonConsentita` (tipo `CAMPO_BASE_IMMUTABILE`) — è la regola distintiva del polimorfismo.
- `campoBase_SempreObbligatorio` → `isObbligatorio() == true` dopo costruzione.
- `campoComune_SetObbligatorio_Consentito` → il flag cambia.
- `campoSpecifico_SetObbligatorio_Consentito`.
- `campo_NomeBlank_LanciaIllegalArgument`.
- `campo_GetTipo_CoerenteConSottoclasse` (BASE/COMUNE/SPECIFICO).
- `campo_EqualsCaseInsensitiveEStessoTipo` → `new CampoComune("Luogo",...).equals(new CampoComune("luogo",...))`.
- `campo_EqualsTipoDiverso_NonUguale` → stesso nome ma tipo diverso ⇒ non uguali.
- `campo_IsData_RiconosceCampiData` (`"Data inizio"`, `"Termine ultimo di iscrizione"`).
- `campo_IsOra_RiconosceCampoOra`.
- `campo_IsInEvidenza_RiconosceCampiBacheca` (es. `"Luogo"`, `"Quota individuale"`).

### 3b. `Categoria`

Codice: `aggiungiCampoSpecifico`, `rimuoviCampoSpecifico`, `contieneCampo`, `modificaObbligatorietaCampoSpecifico`, `repOk`.

- `categoria_NomeBlank_LanciaIllegalArgument`.
- `aggiungiCampoSpecifico_Nuovo_LoAggiunge`.
- `aggiungiCampoSpecifico_DuplicatoCaseInsensitive_LanciaElementoGiaEsistente`.
- `aggiungiCampoSpecifico_Null_LanciaIllegalArgument`.
- `rimuoviCampoSpecifico_Esistente_RestituisceTrue` / `_Inesistente_RestituisceFalse`.
- `contieneCampo_PresenteCaseInsensitive_RestituisceTrue`.
- `modificaObbligatorieta_CampoEsistente_RestituisceTrue`.
- `getCampiSpecifici_RestituisceListaNonModificabile` → tentare `add` lancia `UnsupportedOperationException`.
- `repOk_NomiDuplicati_Falso` (test dell'invariante stessa, costruendo lo scenario via API).

---

## 4. Value Object: `Username`, `IdProposta`, `IdNotifica`

**Principio.** Risolvono la *primitive obsession*; la loro intera ragione d'essere è una validazione e una semantica di uguaglianza corrette. Sono i test più rapidi e «kata-like» della suite (come `StringCalculator`: piccoli, focalizzati, un comportamento ciascuno).

**File:** `test/easyevent/model/UsernameTest.java`, `test/easyevent/proposta/IdPropostaTest.java`, `test/easyevent/notifica/IdNotificaTest.java`

### `Username`
- `username_ValoreValido_LoMemorizzaTrimmato` (`"  mario "` → `"mario"`).
- `username_Null_LanciaIllegalArgument`.
- `username_Blank_LanciaIllegalArgument`.
- `username_EqualsCaseInsensitive` (`"Mario".equals("mario")`).
- `username_HashCodeCoerenteConEquals` → due username uguali (case-insensitive) hanno lo stesso `hashCode`.

### `IdProposta` / `IdNotifica` (identici per struttura)
- `id_ValoreNonNegativo_LoMemorizza`.
- `id_ValoreNegativo_LanciaIllegalArgument`.
- `id_EqualsSuStessoValore`.
- `id_HashCodeCoerente`.
- (facoltativo) `idProposta_DiversoTipoDaIdNotifica` — sono tipi distinti, non confrontabili: questo è un *compile-time test* (il fatto che il progetto compili lo dimostra), non runtime.

---

## 5. `Configuratore`, `Fruitore`, `Notifica`

**Principio.** Entità di modello con piccola logica (credenziali, gestione lista notifiche). La sbobina (pag. 26) tratta la notifica come dato strutturato, non frase: i test verificano i **campi**, non testo italiano.

**File:** `test/easyevent/model/ConfiguratoreTest.java`, `FruitoreTest.java`, `test/easyevent/notifica/NotificaTest.java`

### `Configuratore`
- `configuratore_NuovoAccount_PrimoAccessoVero`.
- `verificaCredenziali_UsernameCaseInsensitivePasswordEsatta_Vero`.
- `verificaCredenziali_PasswordSbagliata_Falso`.
- `verificaCredenziali_PasswordCaseSensitive_Falso` (la password NON è case-insensitive: `equals`, non `equalsIgnoreCase`).
- `impostaCredenzialiPersonali_AggiornaEDisattivaPrimoAccesso`.
- `impostaCredenziali_NuovoUsernameBlank_LanciaIllegalArgument`.
- `equals_StessoUsername_Uguale` (case-insensitive).

### `Fruitore`
- `aggiungiNotifica_Valida_EntraInLista` → `getNotifiche().size() == 1`.
- `aggiungiNotifica_Null_LanciaIllegalArgument`.
- `rimuoviNotifica_IdPresente_RestituisceTrueELaRimuove`.
- `rimuoviNotifica_IdAssente_RestituisceFalse`.
- `ripristinaNotifiche_SostituisceLaLista`.
- `getNotifiche_NonModificabile` (`UnsupportedOperationException` su `add`).

### `Notifica`
- `notifica_CostruttoreValido_RepOkVero`.
- `notifica_IdNull_LanciaIllegalArgument`, `tipoNull`, `dataCreazioneNull`.
- `notifica_CampiTestualiNull_SostituitiConStringaVuota` (es. `titoloProposta==null` ⇒ getter ritorna `""`).
- `notifica_GetterRestituisconoDatiGrezzi` (verifica che i campi siano dati, non frasi formattate).

---

## 6. `PersistenceManager` — test di INTEGRAZIONE (file system)

**Principio (sbobina pag. 27-29, 30).** È l'analogo di `FileEmployeeRepository`: legge/scrive su disco, quindi **non è un unit test** ma un **test di integrazione**. La regola «niente file system negli unit test» qui si applica al contrario: questi test *devono* toccare il disco, ma in modo controllato, usando una **directory/file temporaneo** creato e distrutto dal test (`@TempDir` di JUnit 5), così restano deterministici e ripetibili.

**File:** `test/easyevent/persistence/PersistenceManagerTest.java`

Codice sotto test: `boolean carica(AppData)`, `salvaSicuro(AppData)`, `caricaSicuro(AppData)`, costruttore `PersistenceManager(String dataFilePath)`.

Casi (seguono esattamente la progressione del professore: file inesistente → vuoto → con contenuto → round-trip):
- `carica_FileInesistente_RestituisceFalse` (analogo a «readFromNonExistingFile»). Path dentro `@TempDir` mai creato.
- `caricaSicuro_FileInesistente_NonLanciaEPreservaAppDataVuota`.
- `salvaECarica_RoundTrip_DatiCoerenti` — il test centrale: costruisci un `AppData` con 1 configuratore, 1 fruitore, 1 categoria, 1 campo comune, 1 proposta APERTA con aderenti; salva su file temp; crea un nuovo `AppData`; `carica`; verifica che i contatori, gli username, lo stato della proposta e gli aderenti coincidano. Questo è il test che protegge la serializzazione JSON custom durante il refactoring (incluso il futuro pattern State: lo `StatoProposta` enum deve sopravvivere al round-trip).
- `salvaECarica_PropostaConStoricoStati_PreservaStorico`.
- `salvaECarica_FruitoreConNotifiche_PreservaTipoNotifica` (verifica che l'enum `TipoNotifica` si serializzi/deserializzi).
- `carica_FileMalformato_GestitoSenzaCrash` (JSON troncato → `caricaSicuro` ritorna false o gestisce, non propaga eccezione tecnica grezza).

> Nota di principio: questi test sono più lenti e più «fragili» degli unit (pag. 10). Tienili separati dagli unit (cartella/naming distinto) così puoi eseguire la base della piramide a ogni salvataggio e i test di integrazione meno spesso.

---

## 7. `BatchImporter` — test di INTEGRAZIONE + parsing

**Principio.** Combina parsing (logica) e lettura file (I/O). La sbobina suggerisce di iniettare le dipendenze: qui `AppData` e la `SalvaCallback` sono già iniettate nel costruttore, quindi puoi passare un **callback finto** (self-shunt / lambda no-op) per evitare di salvare davvero su disco durante il test del parsing.

```java
public BatchImporter(AppData appData, String usernameCreatore, SalvaCallback salvaCallback)
public BatchRisultato importa(String percorsoFile)
```

**File:** `test/easyevent/batch/BatchImporterTest.java`, `test/easyevent/batch/BatchRisultatoTest.java`

### 7a. `BatchImporter` (file temporanei + callback no-op)

Arrange tipico: `SalvaCallback noop = () -> {}` e un file `.txt` scritto in `@TempDir`.

- `costruttore_AppDataNull_LanciaIllegalArgument`, `usernameBlank`, `callbackNull`.
- `importa_FileInesistente_LanciaPersistenzaException` (tipo `FILE_NON_TROVATO`).
- `importa_PercorsoNonRegolare_LanciaPersistenzaException` (passa una directory).
- `importa_RigaCampoComune_AggiungeCampoAppData`.
- `importa_RigaCategoriaConCampiSpecifici_CreaCategoria`.
- `importa_RigaProposta_PubblicaInBacheca` (proposta valida ⇒ entra in `getBacheca()`).
- `importa_RigaCommentoOVuota_Ignorata` (riga `#...` o vuota non conta come successo/errore).
- `importa_RigaProrpostaNonValida_ProduceWarningOErrore` (manca un campo obbligatorio).
- `importa_CategoriaDuplicata_ProduceErroreMaNonInterrompe` → l'errore su una riga **non** blocca le successive (regola esplicita della classe: «un errore su una riga non interrompe l'elaborazione delle righe successive»).
- `importa_SalvaCallbackInvocata_QuandoCiSonoSuccessi` (usa un callback che incrementa un contatore — proprio come il `greetingsCount` del kata — e verifica che venga chiamato).
- `importaMultipli_AggregaRisultati` → i conteggi dei singoli file si sommano.

### 7b. `BatchRisultato` (unit puro, niente I/O)

- `aggiungiSuccesso_IncrementaSuccessi`.
- `aggiungiWarning_IncrementaWarnings_ConRigaEMessaggio`.
- `aggiungiErrore_IncrementaErrori`.
- `isSenzaErrori_ConSoliWarning_Vero`.
- `isCompletamenteOk_ConWarning_Falso`.
- `aggiungi_SommaDueRisultati` (merge dei conteggi e delle voci).

---

## 8. Smoke test end-to-end (pochissimi, in cima alla piramide)

**Principio (sbobina pag. 12).** Gli end-to-end servono da *smoke test*: «il sistema non sta fumando». Non coprono tutto, verificano 1-2 percorsi critici. Qui il flusso completo coinvolge `Scanner`/`System.out`, quindi un vero E2E sarebbe fragile. Limìtati a uno smoke test «componibile» che salta la UI:

**File:** `test/easyevent/smoke/FlussoBaseSmokeTest.java`

- `smoke_CreaPubblicaSalvaRicarica_StatoCoerente`: in memoria, crei `AppData`, inizializzi campi base, aggiungi categoria, crei una proposta valida, `pubblicaPropostaDiretta`, salvi con `PersistenceManager` su `@TempDir`, ricarichi in un nuovo `AppData`, verifichi che la bacheca contenga 1 proposta APERTA. Un solo test che esercita la spina dorsale del sistema.

(La tecnica di intercettazione di `System.out` con `ByteArrayOutputStream` descritta a pag. 30 è utile **solo** se vuoi testare una `View`; per il corso è opzionale e non prioritario, perché le View sono lo strato più fragile.)

---

## 9. Infrastruttura: come eseguire JUnit senza Maven/Gradle

**Principio.** Il progetto compila a mano (`run.sh` con `javac @sources.txt`). Per i test serve aggiungere JUnit al classpath. Useremo **JUnit 5 (Jupiter) con la Console Launcher standalone**, un singolo JAR che non richiede build system.

Struttura cartelle proposta (mantiene `test/` separato da `src/`, come da sbobina: «codice di test ≠ codice di produzione»):

```
EasyEvent/
  src/      (produzione, invariato)
  test/     (nuovo: stesso package layout)
  lib/      (nuovo: junit-platform-console-standalone-1.10.x.jar)
```

Script `run-tests.sh` (da aggiungere alla root):

```bash
#!/bin/bash
set -e
JUNIT_JAR="lib/junit-platform-console-standalone-1.10.2.jar"
mkdir -p out-test
# 1. compila produzione + test insieme
find src test -name "*.java" > sources-test.txt
javac -encoding UTF-8 -cp "$JUNIT_JAR" -d out-test @sources-test.txt
# 2. esegui tutti i test
java -jar "$JUNIT_JAR" execute \
     -cp out-test \
     --scan-classpath \
     --details=tree
```

Note:
- I test usano `-ea` implicito? No: gli `assert`/`repOk()` di produzione si attivano con `-ea`. Per esercitare anche le asserzioni di invariante durante i test, aggiungi `-ea` se lanci via `java` diretto; con la console launcher passa `--config junit.jupiter...` non serve, ma puoi eseguire una variante con `java -ea -jar ...` per stressare gli invarianti.
- Scarica il JAR da Maven Central (`junit-platform-console-standalone`). In VS Code, in alternativa, l'estensione *Test Runner for Java* rileva JUnit 5 automaticamente se il JAR è in `lib/` e referenziato in `.vscode/settings.json` (`java.project.referencedLibraries: ["lib/**/*.jar"]`).

Dipendenze JUnit 5 minime usate nei test:
```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;       // per i test di integrazione su file
import static org.junit.jupiter.api.Assertions.*;
```

Convenzioni adottate (coerenti con la sbobina, pag. 12, 18):
- Classe di test = nome della CUT + suffisso `Test` (`PropostaTest`, `AppDataTest`).
- Nomi metodi nello stile `Metodo_Caso_RisultatoAtteso` (es. `transizione_BozzaAConfermata_Vietata`). Stile uniforme in tutto il progetto.
- Struttura **AAA** dentro ogni metodo, con commenti `// Arrange / // Act / // Assert` o spaziatura.

---

## 10. Ordine di esecuzione consigliato (priorità)

Dalla base della piramide verso l'alto, e dal più critico per il refactoring al meno:

1. **`PropostaTest`** (sez. 1) — è la classe che riceverà il pattern State; i test sono la rete di sicurezza obbligatoria *prima* di toccarla.
2. **`AppDataTest`** (sez. 2) — regole di business spostate qui dal refactoring B; dimostra che il refactoring ha raggiunto l'obiettivo.
3. **Value Object + `Campo`/`Categoria`** (sez. 3-4) — rapidi, alta copertura a basso costo, kata-like.
4. **`Configuratore`/`Fruitore`/`Notifica`** (sez. 5).
5. **`BatchRisultato`** (unit, sez. 7b) e **`PersistenceManager`** (integrazione, sez. 6) — quest'ultimo protegge la serializzazione contro il refactoring State.
6. **`BatchImporter`** (integrazione, sez. 7a).
7. **Smoke E2E** (sez. 8) — uno solo, alla fine.

Stima grossolana: ~120-150 metodi di test totali, di cui ~80% unit (base piramide) e ~20% integrazione. Esegui la base a ogni commit; i test di integrazione prima di ogni push.

---

## Riepilogo file di test da creare

| File | Livello | Classi coperte |
|---|---|---|
| `test/easyevent/proposta/PropostaTest.java` | unit | Proposta (stati, validazione, aderenti, ritiro) |
| `test/easyevent/core/AppDataTest.java` | unit | AppData (regole sessione, transizioni, notifiche) |
| `test/easyevent/categoria/CampoTest.java` | unit | Campo, CampoBase, CampoComune, CampoSpecifico |
| `test/easyevent/categoria/CategoriaTest.java` | unit | Categoria |
| `test/easyevent/model/UsernameTest.java` | unit | Username |
| `test/easyevent/proposta/IdPropostaTest.java` | unit | IdProposta |
| `test/easyevent/notifica/IdNotificaTest.java` | unit | IdNotifica |
| `test/easyevent/model/ConfiguratoreTest.java` | unit | Configuratore |
| `test/easyevent/model/FruitoreTest.java` | unit | Fruitore |
| `test/easyevent/notifica/NotificaTest.java` | unit | Notifica |
| `test/easyevent/batch/BatchRisultatoTest.java` | unit | BatchRisultato |
| `test/easyevent/persistence/PersistenceManagerTest.java` | integrazione | PersistenceManager |
| `test/easyevent/batch/BatchImporterTest.java` | integrazione | BatchImporter |
| `test/easyevent/smoke/FlussoBaseSmokeTest.java` | e2e/smoke | flusso completo in memoria + persistence |
