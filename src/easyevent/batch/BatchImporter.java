package easyevent.batch;

import easyevent.model.AppData;
import easyevent.model.Campo;
import easyevent.model.Categoria;
import easyevent.model.Proposta;
import easyevent.model.StatoProposta;
import easyevent.model.exception.ElementoGiaEsistenteException;
import easyevent.model.exception.ModificaNonConsentitaException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Motore di importazione batch per EasyEvent – Versione 5.
 *
 * Legge un file di testo con sintassi proprietaria e applica le operazioni
 * descritte allo stato dell'applicazione (AppData) tramite il controller.
 *
 * Formato supportato (separatore: " | "):
 *
 * # Commento CAMPO_COMUNE | <nome> | <obbligatorio: si/no>
 * CATEGORIA | <nome> [| CAMPO_SPECIFICO | <nome> | <si/no>]* PROPOSTA |
 * <categoria> | <campo> = <valore> [| <campo> = <valore>]*
 *
 * Ogni riga viene elaborata indipendentemente: un errore su una riga non
 * interrompe l'elaborazione delle righe successive.
 *
 * Invariante di classe: - appData != null - usernameCreatore != null &&
 * !usernameCreatore.isBlank() - salvaCallback != null
 */
public class BatchImporter {

    /**
     * Separatore tra i token di ogni riga batch.
     */
    public static final String SEP = " | ";

    /**
     * Prefisso per i comandi di tipo campo comune.
     */
    public static final String CMD_CAMPO_COMUNE = "CAMPO_COMUNE";

    /**
     * Prefisso per i comandi di tipo categoria.
     */
    public static final String CMD_CATEGORIA = "CATEGORIA";

    /**
     * Prefisso per i comandi di tipo proposta.
     */
    public static final String CMD_PROPOSTA = "PROPOSTA";

    /**
     * Parola chiave per i campi specifici inline nelle righe CATEGORIA.
     */
    public static final String KW_CAMPO_SPECIFICO = "CAMPO_SPECIFICO";

    // ================================================================
    // INTERFACCIA FUNZIONALE per il callback di salvataggio
    // ================================================================
    /**
     * Callback invocato da BatchImporter per salvare lo stato su disco. Il
     * controller passa la propria implementazione di {@code salva()}.
     */
    @FunctionalInterface
    public interface SalvaCallback {

        /**
         * Salva lo stato corrente dell'applicazione.
         *
         * @throws IOException se il salvataggio fallisce
         */
        void salva() throws IOException;
    }

    // ================================================================
    // CAMPI
    // ================================================================
    private final AppData appData;
    private final String usernameCreatore;
    private final SalvaCallback salvaCallback;

    // ================================================================
    // COSTRUTTORE
    // ================================================================
    /**
     * @param appData stato dell'applicazione, non null
     * @param usernameCreatore username del configuratore loggato, non
     * null/blank
     * @param salvaCallback callback per il salvataggio su disco, non null
     * @throws IllegalArgumentException se qualche parametro non è valido
     */
    public BatchImporter(AppData appData, String usernameCreatore, SalvaCallback salvaCallback) {
        if (appData == null) {
            throw new IllegalArgumentException("AppData non puo' essere null.");
        }
        if (usernameCreatore == null || usernameCreatore.isBlank()) {
            throw new IllegalArgumentException("usernameCreatore non puo' essere null o vuoto.");
        }
        if (salvaCallback == null) {
            throw new IllegalArgumentException("salvaCallback non puo' essere null.");
        }
        this.appData = appData;
        this.usernameCreatore = usernameCreatore;
        this.salvaCallback = salvaCallback;
    }

    // ================================================================
    // METODI PUBBLICI
    // ================================================================
    /**
     * Importa un singolo file batch.
     *
     * @param percorsoFile path del file da importare (assoluto o relativo alla
     * CWD)
     * @return il resoconto dell'importazione
     * @throws IOException se il file non esiste o non è leggibile
     */
    public BatchRisultato importa(String percorsoFile) throws IOException {
        if (percorsoFile == null || percorsoFile.isBlank()) {
            throw new IllegalArgumentException("Il percorso del file non puo' essere null o vuoto.");
        }

        Path path = Paths.get(percorsoFile);
        if (!Files.exists(path)) {
            throw new IOException("File non trovato: " + percorsoFile);
        }
        if (!Files.isRegularFile(path)) {
            throw new IOException("Il percorso non indica un file regolare: " + percorsoFile);
        }

        List<String> righe = Files.readAllLines(path, StandardCharsets.UTF_8);
        return elaboraRighe(righe);
    }

    /**
     * Importa più file batch in sequenza e aggrega i risultati.
     *
     * @param percorsiFile lista di percorsi dei file, non null
     * @return resoconto aggregato di tutti i file
     */
    public BatchRisultato importaMultipli(List<String> percorsiFile) {
        if (percorsiFile == null) {
            throw new IllegalArgumentException("La lista dei percorsi non puo' essere null.");
        }

        BatchRisultato totale = new BatchRisultato();
        for (String percorso : percorsiFile) {
            try {
                BatchRisultato parziale = importa(percorso);
                totale.aggiungi(parziale);
            } catch (IOException e) {
                // Anche l'apertura del file è un errore che non blocca il resto
                totale.aggiungiErrore(0,
                        "Impossibile aprire il file '" + percorso + "': " + e.getMessage());
            }
        }
        return totale;
    }

    // ================================================================
    // ELABORAZIONE RIGHE
    // ================================================================
    /**
     * Elabora la lista di righe di un file batch e restituisce il resoconto.
     * Ogni riga è indipendente: errori su una riga non bloccano le successive.
     *
     * @param righe lista delle righe del file
     * @return resoconto dell'elaborazione
     */
    private BatchRisultato elaboraRighe(List<String> righe) {
        BatchRisultato risultato = new BatchRisultato();

        for (int i = 0; i < righe.size(); i++) {
            int numeroRiga = i + 1; // 1-based per i messaggi all'utente
            String riga = righe.get(i).trim();

            // Salta righe vuote e commenti
            if (riga.isEmpty() || riga.startsWith("#")) {
                continue;
            }

            risultato.incrementaRighe();

            // Suddivide la riga in token usando il separatore " | "
            String[] token = riga.split(" \\| ");
            if (token.length == 0) {
                risultato.aggiungiWarning(numeroRiga, "Riga vuota dopo il trim.");
                continue;
            }

            String comando = token[0].trim().toUpperCase();

            switch (comando) {
                case CMD_CAMPO_COMUNE ->
                    elaboraCampoComune(token, numeroRiga, risultato);
                case CMD_CATEGORIA ->
                    elaboraCategoria(token, numeroRiga, risultato);
                case CMD_PROPOSTA ->
                    elaboraProposta(token, numeroRiga, risultato);
                default ->
                    risultato.aggiungiWarning(numeroRiga,
                            "Comando non riconosciuto: '" + token[0].trim()
                            + "'. Comandi validi: CAMPO_COMUNE, CATEGORIA, PROPOSTA.");
            }
        }

        return risultato;
    }

    // ================================================================
    // ELABORAZIONE CAMPO COMUNE
    // ================================================================
    /**
     * Elabora una riga CAMPO_COMUNE.
     *
     * Formato: CAMPO_COMUNE | <nome> | <si/no>
     *
     * Precondizioni: - token[0] == "CAMPO_COMUNE" - token.length >= 3
     *
     * @param token array di token estratti dalla riga
     * @param numeroRiga numero di riga nel file (per i messaggi)
     * @param risultato accumulatore dei risultati
     */
    private void elaboraCampoComune(String[] token, int numeroRiga, BatchRisultato risultato) {
        if (token.length < 3) {
            risultato.aggiungiErrore(numeroRiga,
                    "CAMPO_COMUNE richiede almeno 3 token: CAMPO_COMUNE | <nome> | <si/no>. "
                    + "Trovati: " + token.length);
            return;
        }

        String nomeCampo = token[1].trim();
        String obbligStr = token[2].trim().toLowerCase();

        if (nomeCampo.isEmpty()) {
            risultato.aggiungiErrore(numeroRiga, "Il nome del campo comune non puo' essere vuoto.");
            return;
        }

        boolean obbligatorio = obbligStr.equals("si") || obbligStr.equals("sì")
                || obbligStr.equals("yes") || obbligStr.equals("true");

        // Verifica se già esiste come campo base o comune
        if (appData.esisteCampoBase(nomeCampo)) {
            risultato.aggiungiWarning(numeroRiga,
                    "Esiste già un campo BASE con nome '" + nomeCampo + "'. Riga saltata.");
            return;
        }
        if (appData.esisteCampoComune(nomeCampo)) {
            risultato.aggiungiWarning(numeroRiga,
                    "Campo comune '" + nomeCampo + "' già presente. Riga saltata.");
            return;
        }

        try {
            Campo campo = new Campo(nomeCampo, obbligatorio, Campo.TipoCampo.COMUNE);
            appData.aggiungiCampoComune(campo);
            salvaCallback.salva();
            risultato.aggiungiSuccesso(
                    "Campo comune aggiunto: '" + nomeCampo
                    + "' [" + (obbligatorio ? "obbligatorio" : "facoltativo") + "]");
        } catch (ElementoGiaEsistenteException e) {
            String tipoConflitto = switch (e.getTipoElemento()) {
                case CAMPO_BASE ->
                    "campo BASE";
                case CAMPO_COMUNE ->
                    "campo COMUNE";
                default ->
                    "campo";
            };
            risultato.aggiungiWarning(numeroRiga,
                    "Esiste già un " + tipoConflitto
                    + " con nome '" + e.getNomeElemento() + "'. Riga saltata.");
        } catch (IllegalArgumentException e) {
            // Cattura errori di programmazione (nome null/vuoto, tipo errato)
            // che restano come IllegalArgumentException nel Model.
            risultato.aggiungiErrore(numeroRiga, "Errore creazione campo comune: " + e.getMessage());
        } catch (IOException e) {
            appData.rimuoviCampoComune(nomeCampo);
            risultato.aggiungiErrore(numeroRiga,
                    "Campo aggiunto in memoria ma errore nel salvataggio (rollback eseguito): "
                    + e.getMessage());
        }
    }

    // ================================================================
    // ELABORAZIONE CATEGORIA
    // ================================================================
    /**
     * Elabora una riga CATEGORIA (con eventuali campi specifici inline).
     *
     * Formato: CATEGORIA | <nome>
     * CATEGORIA | <nome> | CAMPO_SPECIFICO | <nome_campo> | <si/no> [|
     * CAMPO_SPECIFICO | ...]
     *
     * I campi specifici vengono aggiunti solo se la categoria è nuova. Se la
     * categoria già esiste, i campi specifici sono ignorati (con warning).
     *
     * @param token array di token estratti dalla riga
     * @param numeroRiga numero di riga nel file
     * @param risultato accumulatore dei risultati
     */
    private void elaboraCategoria(String[] token, int numeroRiga, BatchRisultato risultato) {
        if (token.length < 2) {
            risultato.aggiungiErrore(numeroRiga,
                    "CATEGORIA richiede almeno 2 token: CATEGORIA | <nome>. "
                    + "Trovati: " + token.length);
            return;
        }

        String nomeCategoria = token[1].trim();
        if (nomeCategoria.isEmpty()) {
            risultato.aggiungiErrore(numeroRiga, "Il nome della categoria non puo' essere vuoto.");
            return;
        }

        // Analizza i campi specifici (se presenti)
        List<String[]> campiSpecificiDaAggiungere = new ArrayList<>();
        for (int i = 2; i < token.length; i++) {
            if (KW_CAMPO_SPECIFICO.equalsIgnoreCase(token[i].trim())) {
                // Ci aspettiamo due token successivi: nome e obbligatorio
                if (i + 2 >= token.length) {
                    risultato.aggiungiWarning(numeroRiga,
                            "CAMPO_SPECIFICO alla posizione " + (i + 1)
                            + " non ha nome e/o obbligatorietà. Ignorato.");
                    i++; // salta comunque
                    continue;
                }
                String nomeCS = token[i + 1].trim();
                String obbligCS = token[i + 2].trim().toLowerCase();
                campiSpecificiDaAggiungere.add(new String[]{nomeCS, obbligCS});
                i += 2; // salta nome e obbligatorio già letti
            } else {
                risultato.aggiungiWarning(numeroRiga,
                        "Token '" + token[i].trim()
                        + "' non riconosciuto nella definizione di categoria. "
                        + "Atteso: CAMPO_SPECIFICO. Ignorato.");
            }
        }

        // Verifica se la categoria esiste già
        if (appData.esisteCategoria(nomeCategoria)) {
            String msgExtra = campiSpecificiDaAggiungere.isEmpty()
                    ? ""
                    : " I campi specifici inline sono stati ignorati.";
            risultato.aggiungiWarning(numeroRiga,
                    "Categoria '" + nomeCategoria + "' già presente. Riga saltata." + msgExtra);
            return;
        }

        // Crea la categoria
        Categoria categoria = new Categoria(nomeCategoria);

        // Aggiunge i campi specifici alla categoria (prima del salvataggio)
        List<String> campiAggiuntiOk = new ArrayList<>();
        for (String[] cs : campiSpecificiDaAggiungere) {
            String nomeCS = cs[0];
            String obbligCS = cs[1];
            if (nomeCS.isEmpty()) {
                risultato.aggiungiWarning(numeroRiga,
                        "Nome del campo specifico vuoto: ignorato.");
                continue;
            }
            // Verifica conflitti con campi base e comuni
            if (appData.esisteCampoBase(nomeCS)) {
                risultato.aggiungiWarning(numeroRiga,
                        "Il campo specifico '" + nomeCS
                        + "' ha lo stesso nome di un campo BASE: ignorato.");
                continue;
            }
            if (appData.esisteCampoComune(nomeCS)) {
                risultato.aggiungiWarning(numeroRiga,
                        "Il campo specifico '" + nomeCS
                        + "' ha lo stesso nome di un campo COMUNE: ignorato.");
                continue;
            }
            boolean ob = obbligCS.equals("si") || obbligCS.equals("sì")
                    || obbligCS.equals("yes") || obbligCS.equals("true");
            try {
                categoria.aggiungiCampoSpecifico(
                        new Campo(nomeCS, ob, Campo.TipoCampo.SPECIFICO));
                campiAggiuntiOk.add(nomeCS);
            } catch (ElementoGiaEsistenteException e) {
                risultato.aggiungiWarning(numeroRiga,
                        "Campo specifico '" + e.getNomeElemento()
                        + "' già presente nella categoria. Ignorato.");
            } catch (IllegalArgumentException e) {
                risultato.aggiungiWarning(numeroRiga,
                        "Campo specifico '" + nomeCS + "' non aggiunto: " + e.getMessage());
            }
        }

        // Aggiunge la categoria ad AppData e salva
        try {
            appData.aggiungiCategoria(categoria);
            salvaCallback.salva();
            String dettaglio = campiAggiuntiOk.isEmpty()
                    ? "(senza campi specifici)"
                    : "campi specifici: " + String.join(", ", campiAggiuntiOk);
            risultato.aggiungiSuccesso(
                    "Categoria aggiunta: '" + nomeCategoria + "' — " + dettaglio);
        } catch (ElementoGiaEsistenteException e) {
            risultato.aggiungiWarning(numeroRiga,
                    "Categoria '" + e.getNomeElemento()
                    + "' già presente. Riga saltata.");
        } catch (IllegalArgumentException e) {
            risultato.aggiungiErrore(numeroRiga,
                    "Errore creazione categoria: " + e.getMessage());
        } catch (IOException e) {
            appData.rimuoviCategoria(nomeCategoria);
            risultato.aggiungiErrore(numeroRiga,
                    "Categoria aggiunta in memoria ma errore nel salvataggio (rollback eseguito): "
                    + e.getMessage());
        }
    }

    // ================================================================
    // ELABORAZIONE PROPOSTA
    // ================================================================
    /**
     * Elabora una riga PROPOSTA.
     *
     * Formato: PROPOSTA | <categoria> | <campo1> = <valore1> | <campo2> =
     * <valore2> | ...
     *
     * La proposta è validata con le stesse regole della modalità interattiva.
     * Se valida, viene pubblicata direttamente in bacheca (persistita). Se non
     * valida, viene segnalata come errore e non salvata.
     *
     * @param token array di token estratti dalla riga
     * @param numeroRiga numero di riga nel file
     * @param risultato accumulatore dei risultati
     */
    private void elaboraProposta(String[] token, int numeroRiga, BatchRisultato risultato) {
        if (token.length < 3) {
            risultato.aggiungiErrore(numeroRiga,
                    "PROPOSTA richiede almeno 3 token: PROPOSTA | <categoria> | <campo>=<valore>. "
                    + "Trovati: " + token.length);
            return;
        }

        String nomeCategoria = token[1].trim();
        if (nomeCategoria.isEmpty()) {
            risultato.aggiungiErrore(numeroRiga,
                    "Il nome della categoria nella proposta non puo' essere vuoto.");
            return;
        }
        if (!appData.esisteCategoria(nomeCategoria)) {
            risultato.aggiungiErrore(numeroRiga,
                    "Categoria '" + nomeCategoria + "' non trovata. "
                    + "Creare prima la categoria (con CATEGORIA) o in modo interattivo.");
            return;
        }

        // Costruisce lo snapshot dei campi (BASE → COMUNI → SPECIFICI)
        LinkedHashMap<String, Boolean> snapshot = new LinkedHashMap<>();
        for (Campo c : appData.getCampiBase()) {
            snapshot.put(c.getNome(), c.isObbligatorio());
        }
        for (Campo c : appData.getCampiComuni()) {
            snapshot.put(c.getNome(), c.isObbligatorio());
        }
        Categoria cat = appData.getCategoria(nomeCategoria);
        for (Campo c : cat.getCampiSpecifici()) {
            snapshot.put(c.getNome(), c.isObbligatorio());
        }

        // Crea la proposta con un ID univoco
        int id = appData.getNuovoIdProposta();
        Proposta proposta = new Proposta(id, nomeCategoria, usernameCreatore, snapshot);

        // Assegna i valori dei campi leggendo i token rimanenti (formato: campo = valore)
        List<String> campiNonRiconosciuti = new ArrayList<>();
        for (int i = 2; i < token.length; i++) {
            String tokenVal = token[i].trim();
            if (tokenVal.isEmpty()) {
                continue;
            }

            int uguale = tokenVal.indexOf('=');
            if (uguale < 0) {
                risultato.aggiungiWarning(numeroRiga,
                        "Token '" + tokenVal + "' non ha il formato 'campo = valore'. Ignorato.");
                continue;
            }

            String nomeCampo = tokenVal.substring(0, uguale).trim();
            String valore = tokenVal.substring(uguale + 1).trim();

            if (!snapshot.containsKey(nomeCampo)) {
                // Cerca case-insensitive
                String nomeCampoEffettivo = trovaChiaveCaseInsensitive(snapshot, nomeCampo);
                if (nomeCampoEffettivo == null) {
                    campiNonRiconosciuti.add(nomeCampo);
                    continue;
                }
                nomeCampo = nomeCampoEffettivo;
            }

            try {
                proposta.setValore(nomeCampo, valore);
            } catch (ModificaNonConsentitaException e) {
                String motivazione = switch (e.getTipoModifica()) {
                    case CAMPO_NON_PRESENTE ->
                        "il campo '" + e.getDettaglio() + "' non esiste nella proposta";
                    case PROPOSTA_GIA_PUBBLICATA ->
                        "la proposta è già pubblicata (stato: " + e.getDettaglio() + ")";
                    case CAMPO_BASE_IMMUTABILE ->
                        "il campo '" + e.getDettaglio() + "' è immutabile";
                };
                risultato.aggiungiWarning(numeroRiga,
                        "Impossibile impostare '" + nomeCampo + "': " + motivazione + ".");
            }
        }

        if (!campiNonRiconosciuti.isEmpty()) {
            risultato.aggiungiWarning(numeroRiga,
                    "Campi non riconosciuti per la categoria '"
                    + nomeCategoria + "' (ignorati): "
                    + String.join(", ", campiNonRiconosciuti));
        }

        // Ricalcola lo stato: BOZZA o VALIDA
        LocalDate oggi = LocalDate.now();
        proposta.aggiornaStato(oggi);

        if (proposta.getStato() != StatoProposta.VALIDA) {
            // La proposta non è pubblicabile: segnala gli errori di validazione
            List<String> erroriValidazione = proposta.validazioneErrori(oggi);
            // L'ID consumato va "restituito" resettando il contatore: non è possibile
            // in modo sicuro senza un metodo dedicato. Gli ID sono progrediti anche per proposte scartate
            // (il contatore non viene decrementato per garantire l'unicità degli ID
            // anche in caso di race condition su file batch multipli).
            risultato.aggiungiErrore(numeroRiga,
                    "Proposta non valida per la categoria '"
                    + nomeCategoria + "' (non pubblicata). Problemi: "
                    + String.join("; ", erroriValidazione));
            return;
        }

        // Pubblica la proposta in bacheca
        proposta.pubblicaInBacheca(oggi);
        appData.aggiungiPropostaAperta(proposta);

        try {
            salvaCallback.salva();
            String titolo = proposta.getValore("Titolo");
            risultato.aggiungiSuccesso(
                    "Proposta pubblicata in bacheca: [ID " + id + "] '"
                    + (titolo.isBlank() ? "(senza titolo)" : titolo)
                    + "' — categoria: " + nomeCategoria);
        } catch (IOException e) {
            // Rollback: rimuove la proposta dall'archivio e resetta lo stato
            appData.rimuoviPropostaDaArchivio(id);
            proposta.revertToValida();
            risultato.aggiungiErrore(numeroRiga,
                    "Proposta pronta ma errore nel salvataggio (rollback eseguito): "
                    + e.getMessage());
        }
    }

    // ================================================================
    // UTILITY
    // ================================================================
    /**
     * Cerca una chiave nella mappa in modo case-insensitive.
     *
     * @param mappa la mappa in cui cercare
     * @param chiave la chiave da trovare
     * @return la chiave nella forma originale presente nella mappa, o null se
     * non trovata
     */
    private static String trovaChiaveCaseInsensitive(
            LinkedHashMap<String, Boolean> mappa, String chiave) {
        for (String k : mappa.keySet()) {
            if (k.equalsIgnoreCase(chiave)) {
                return k;
            }
        }
        return null;
    }
}
