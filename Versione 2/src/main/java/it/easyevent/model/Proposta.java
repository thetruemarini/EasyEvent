package it.easyevent.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rappresenta una proposta di iniziativa (Versione 2).
 *
 * Ciclo di vita in V2:
 *   BOZZA → (campi compilati + vincoli soddisfatti) → VALIDA
 *                                                       ↓ (pubblicazione)
 *                                                     APERTA  (persistita)
 *
 * Al termine della sessione, proposte BOZZA e VALIDA vengono scartate;
 * solo le proposte APERTE sono salvate su file.
 *
 * Lo snapshot dei campi (nome → obbligatorio) viene acquisito al momento
 * della creazione e non risente di successive modifiche alla configurazione.
 *
 * Invariante di classe:
 *   - id >= 0
 *   - nomeCategoria != null && !nomeCategoria.isBlank()
 *   - usernameCreatore != null && !usernameCreatore.isBlank()
 *   - campiSnapshot != null (può essere vuoto ma non null)
 *   - valori != null, contiene una chiave per ogni campo nello snapshot
 *   - stato != null
 *   - dataPubblicazione != null ↔ stato == APERTA
 */
public class Proposta {

    /** Formato data usato nell'applicazione: gg/mm/aaaa. */
    public static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Nomi dei campi base coinvolti nei vincoli di data
    public static final String CAMPO_TERMINE_ISCRIZIONE = "Termine ultimo di iscrizione";
    public static final String CAMPO_DATA               = "Data";
    public static final String CAMPO_DATA_CONCLUSIVA    = "Data conclusiva";

    // ---- Dati identificativi ----
    private final int    id;
    private final String nomeCategoria;
    private final String usernameCreatore;

    // ---- Snapshot ordinato dei campi al momento della creazione ----
    // chiave = nome campo, valore = obbligatorio
    private final LinkedHashMap<String, Boolean> campiSnapshot;

    // ---- Valori correnti dei campi ----
    private final Map<String, String> valori;

    // ---- Stato e metadati di pubblicazione ----
    private StatoProposta stato;
    private LocalDate     dataPubblicazione; // null finché non pubblicata

    // ================================================================
    // COSTRUTTORI
    // ================================================================

    /**
     * Costruttore per proposte create interattivamente in sessione.
     *
     * @param id               identificativo progressivo (>= 0)
     * @param nomeCategoria    nome della categoria, non null/blank
     * @param usernameCreatore username del configuratore, non null/blank
     * @param campiOrdinati    snapshot dei campi (nome → obbligatorio), non null
     * @throws IllegalArgumentException se uno dei parametri non è valido
     */
    public Proposta(int id, String nomeCategoria, String usernameCreatore,
                    LinkedHashMap<String, Boolean> campiOrdinati) {
        // Precondizioni
        if (id < 0)
            throw new IllegalArgumentException("L'id della proposta non può essere negativo.");
        if (nomeCategoria == null || nomeCategoria.isBlank())
            throw new IllegalArgumentException("Il nome della categoria non può essere null o vuoto.");
        if (usernameCreatore == null || usernameCreatore.isBlank())
            throw new IllegalArgumentException("Lo username del creatore non può essere null o vuoto.");
        if (campiOrdinati == null)
            throw new IllegalArgumentException("La mappa dei campi non può essere null.");

        this.id               = id;
        this.nomeCategoria    = nomeCategoria;
        this.usernameCreatore = usernameCreatore;
        this.campiSnapshot    = new LinkedHashMap<>(campiOrdinati);
        this.valori           = new LinkedHashMap<>();
        this.stato            = StatoProposta.BOZZA;
        this.dataPubblicazione = null;

        // Inizializza tutti i valori a stringa vuota
        for (String nomeCampo : campiOrdinati.keySet()) {
            valori.put(nomeCampo, "");
        }

        // Postcondizione
        assert repOk() : "Invariante violato dopo costruzione Proposta";
    }

    /**
     * Costruttore per la deserializzazione (proposte APERTE caricate da file).
     *
     * @param id               id della proposta
     * @param nomeCategoria    categoria
     * @param usernameCreatore creatore
     * @param campiOrdinati    snapshot dei campi
     * @param valori           valori dei campi
     * @param stato            stato della proposta
     * @param dataPubblicazione data di pubblicazione (non null se APERTA)
     */
    public Proposta(int id, String nomeCategoria, String usernameCreatore,
                    LinkedHashMap<String, Boolean> campiOrdinati,
                    Map<String, String> valori, StatoProposta stato,
                    LocalDate dataPubblicazione) {
        this.id                = id;
        this.nomeCategoria     = nomeCategoria;
        this.usernameCreatore  = usernameCreatore;
        this.campiSnapshot     = new LinkedHashMap<>(campiOrdinati);
        this.valori            = new LinkedHashMap<>(valori);
        this.stato             = stato;
        this.dataPubblicazione = dataPubblicazione;
    }

    // ================================================================
    // GESTIONE VALORI
    // ================================================================

    /**
     * Imposta il valore di un campo della proposta.
     *
     * @param nomeCampo nome del campo (deve essere nello snapshot)
     * @param valore    valore da assegnare; null o blank svuota il campo
     * @throws IllegalArgumentException se nomeCampo non è presente nello snapshot
     * @throws IllegalStateException    se la proposta è già APERTA
     */
    public void setValore(String nomeCampo, String valore) {
        // Precondizioni
        if (!campiSnapshot.containsKey(nomeCampo)) {
            throw new IllegalArgumentException(
                    "Campo non presente nella proposta: '" + nomeCampo + "'");
        }
        if (stato == StatoProposta.APERTA) {
            throw new IllegalStateException(
                    "Non è possibile modificare una proposta già pubblicata in bacheca.");
        }
        valori.put(nomeCampo, (valore == null) ? "" : valore.trim());
    }

    /**
     * @param nomeCampo nome del campo
     * @return valore corrente; stringa vuota se non compilato o campo assente
     */
    public String getValore(String nomeCampo) {
        return valori.getOrDefault(nomeCampo, "");
    }

    // ================================================================
    // VALIDAZIONE E STATO
    // ================================================================

    /**
     * Ricalcola e aggiorna lo stato della proposta:
     * VALIDA se non ci sono errori di validazione, BOZZA altrimenti.
     * Non modifica lo stato se la proposta è già APERTA.
     *
     * @param dataOggi data corrente (usata per il vincolo "Termine iscrizione > oggi")
     */
    public void aggiornaStato(LocalDate dataOggi) {
        if (stato == StatoProposta.APERTA) return;
        stato = validazioneErrori(dataOggi).isEmpty()
                ? StatoProposta.VALIDA
                : StatoProposta.BOZZA;
    }

    /**
     * Calcola la lista degli errori di validazione senza modificare lo stato.
     *
     * @param dataOggi data corrente
     * @return lista di messaggi di errore; vuota se la proposta è valida
     */
    public List<String> validazioneErrori(LocalDate dataOggi) {
        List<String> errori = new ArrayList<>();

        // --- 1. Campi obbligatori non compilati ---
        for (Map.Entry<String, Boolean> entry : campiSnapshot.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                if (valori.getOrDefault(entry.getKey(), "").isBlank()) {
                    errori.add("Campo obbligatorio non compilato: '" + entry.getKey() + "'");
                }
            }
        }

        // --- 2. Vincoli di data ---
        String strTermine  = getValore(CAMPO_TERMINE_ISCRIZIONE);
        String strData     = getValore(CAMPO_DATA);
        String strDataConc = getValore(CAMPO_DATA_CONCLUSIVA);

        LocalDate termine  = parseDateSafe(strTermine);
        LocalDate data     = parseDateSafe(strData);
        LocalDate dataConc = parseDateSafe(strDataConc);

        if (!strTermine.isBlank() && termine == null) {
            errori.add("'" + CAMPO_TERMINE_ISCRIZIONE
                    + "': formato data non valido (usare gg/mm/aaaa).");
        }
        if (!strData.isBlank() && data == null) {
            errori.add("'" + CAMPO_DATA
                    + "': formato data non valido (usare gg/mm/aaaa).");
        }
        if (!strDataConc.isBlank() && dataConc == null) {
            errori.add("'" + CAMPO_DATA_CONCLUSIVA
                    + "': formato data non valido (usare gg/mm/aaaa).");
        }

        // "Termine ultimo di iscrizione" deve essere strettamente successivo a oggi
        if (termine != null && !termine.isAfter(dataOggi)) {
            errori.add("'" + CAMPO_TERMINE_ISCRIZIONE
                    + "' deve essere successivo alla data odierna ("
                    + dataOggi.format(DATE_FORMAT) + ").");
        }

        // "Data" deve essere almeno 2 giorni dopo "Termine ultimo di iscrizione"
        if (termine != null && data != null) {
            LocalDate minimaData = termine.plusDays(2);
            if (data.isBefore(minimaData)) {
                errori.add("'" + CAMPO_DATA + "' deve essere almeno 2 giorni dopo '"
                        + CAMPO_TERMINE_ISCRIZIONE + "' (minimo: "
                        + minimaData.format(DATE_FORMAT) + ").");
            }
        }

        // "Data conclusiva" non può essere precedente a "Data"
        if (data != null && dataConc != null && dataConc.isBefore(data)) {
            errori.add("'" + CAMPO_DATA_CONCLUSIVA + "' non può essere precedente a '"
                    + CAMPO_DATA + "'.");
        }

        return errori;
    }

    /**
     * Pubblica la proposta in bacheca.
     * Precondizione: stato == VALIDA.
     *
     * @param dataPubblicazione data di pubblicazione, non null
     * @throws IllegalStateException    se la proposta non è in stato VALIDA
     * @throws IllegalArgumentException se dataPubblicazione è null
     */
    public void pubblicaInBacheca(LocalDate dataPubblicazione) {
        // Precondizioni
        if (stato != StatoProposta.VALIDA) {
            throw new IllegalStateException(
                    "Solo le proposte VALIDE possono essere pubblicate in bacheca.");
        }
        if (dataPubblicazione == null) {
            throw new IllegalArgumentException("La data di pubblicazione non può essere null.");
        }
        this.stato             = StatoProposta.APERTA;
        this.dataPubblicazione = dataPubblicazione;

        // Postcondizioni
        assert this.stato == StatoProposta.APERTA : "Postcondizione violata: stato non APERTA";
        assert this.dataPubblicazione != null      : "Postcondizione violata: dataPubblicazione null";
        assert repOk() : "Invariante violato dopo pubblicaInBacheca";
    }

    // ================================================================
    // GETTERS
    // ================================================================

    public int getId()                                         { return id; }
    public String getNomeCategoria()                           { return nomeCategoria; }
    public String getUsernameCreatore()                        { return usernameCreatore; }
    public StatoProposta getStato()                            { return stato; }
    public LocalDate getDataPubblicazione()                    { return dataPubblicazione; }
    public LinkedHashMap<String, Boolean> getCampiSnapshot()   { return new LinkedHashMap<>(campiSnapshot); }
    public Map<String, String> getValori()                     { return Collections.unmodifiableMap(valori); }

    // ================================================================
    // INVARIANTE
    // ================================================================

    /**
     * Verifica l'invariante di classe.
     */
    public boolean repOk() {
        if (id < 0) return false;
        if (nomeCategoria == null || nomeCategoria.isBlank()) return false;
        if (usernameCreatore == null || usernameCreatore.isBlank()) return false;
        if (campiSnapshot == null || valori == null || stato == null) return false;
        if (stato == StatoProposta.APERTA && dataPubblicazione == null) return false;
        if (stato != StatoProposta.APERTA && dataPubblicazione != null) return false;
        return true;
    }

    // ================================================================
    // UTILITY
    // ================================================================

    private static LocalDate parseDateSafe(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim(), DATE_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        String titolo = getValore("Titolo");
        String display = titolo.isBlank() ? "(senza titolo)" : titolo;
        return String.format("[ID %d] cat:%s – \"%s\" (%s)",
                id, nomeCategoria, display, stato);
    }
}
