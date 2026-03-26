package it.easyevent.v4.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rappresenta una proposta di iniziativa (Versione 4).
 *
 * Ciclo di vita:
 *   BOZZA -> VALIDA -> (pubblicazione) -> APERTA
 *   APERTA -> CONFERMATA  (termine scaduto, aderenti >= numPartecipanti)
 *   APERTA -> ANNULLATA   (termine scaduto, aderenti < numPartecipanti)
 *   CONFERMATA -> CONCLUSA (giorno dopo dataConclusiva)
 *
 *   Novita' V4 (configuratore):
 *   APERTA     -> RITIRATA  (fino alle 23:59 del giorno precedente "Data")
 *   CONFERMATA -> RITIRATA  (fino alle 23:59 del giorno precedente "Data")
 *
 *   Novita' V4 (fruitore):
 *   Possibilita' di disdire l'iscrizione (rimuoviAderente) fino al termine.
 *
 * Invariante di classe:
 *   - id >= 0
 *   - nomeCategoria, usernameCreatore != null e non blank
 *   - campiSnapshot, valori, stato, aderenti, storicoStati != null
 *   - dataPubblicazione != null <-> stato in {APERTA, CONFERMATA, ANNULLATA, CONCLUSA, RITIRATA}
 */
public class Proposta {

    public static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static final String CAMPO_TERMINE_ISCRIZIONE = "Termine ultimo di iscrizione";
    public static final String CAMPO_DATA               = "Data inizio";
    public static final String CAMPO_DATA_CONCLUSIVA    = "Data conclusiva";
    public static final String CAMPO_NUM_PARTECIPANTI   = "Numero di partecipanti";

    private final int    id;
    private final String nomeCategoria;
    private final String usernameCreatore;

    private final LinkedHashMap<String, Boolean> campiSnapshot;
    private final Map<String, String>            valori;

    private StatoProposta stato;
    private LocalDate     dataPubblicazione;

    private final List<String>      aderenti;
    private final List<CambioStato> storicoStati;

    // ================================================================
    // INNER CLASS: CambioStato
    // ================================================================

    public static class CambioStato {
        public final StatoProposta stato;
        public final LocalDate     data;

        public CambioStato(StatoProposta stato, LocalDate data) {
            if (stato == null || data == null)
                throw new IllegalArgumentException("Stato e data non possono essere null.");
            this.stato = stato;
            this.data  = data;
        }

        @Override
        public String toString() {
            return stato.name() + " (" + data.format(DATE_FORMAT) + ")";
        }
    }

    // ================================================================
    // COSTRUTTORI
    // ================================================================

    /** Costruttore per proposte create interattivamente in sessione. */
    public Proposta(int id, String nomeCategoria, String usernameCreatore,
                    LinkedHashMap<String, Boolean> campiOrdinati) {
        if (id < 0)
            throw new IllegalArgumentException("L'id della proposta non puo' essere negativo.");
        if (nomeCategoria == null || nomeCategoria.isBlank())
            throw new IllegalArgumentException("Il nome della categoria non puo' essere null o vuoto.");
        if (usernameCreatore == null || usernameCreatore.isBlank())
            throw new IllegalArgumentException("Lo username del creatore non puo' essere null o vuoto.");
        if (campiOrdinati == null)
            throw new IllegalArgumentException("La mappa dei campi non puo' essere null.");

        this.id               = id;
        this.nomeCategoria    = nomeCategoria;
        this.usernameCreatore = usernameCreatore;
        this.campiSnapshot    = new LinkedHashMap<>(campiOrdinati);
        this.valori           = new LinkedHashMap<>();
        this.stato            = StatoProposta.BOZZA;
        this.dataPubblicazione = null;
        this.aderenti         = new ArrayList<>();
        this.storicoStati     = new ArrayList<>();

        for (String nomeCampo : campiOrdinati.keySet())
            valori.put(nomeCampo, "");

        assert repOk() : "Invariante violato dopo costruzione Proposta";
    }

    /** Costruttore per la deserializzazione (V3/V4). */
    public Proposta(int id, String nomeCategoria, String usernameCreatore,
                    LinkedHashMap<String, Boolean> campiOrdinati,
                    Map<String, String> valori, StatoProposta stato,
                    LocalDate dataPubblicazione,
                    List<String> aderenti,
                    List<CambioStato> storicoStati) {
        if (id < 0)
            throw new IllegalArgumentException("L'id della proposta non puo' essere negativo.");
        if (nomeCategoria == null || nomeCategoria.isBlank())
            throw new IllegalArgumentException("Il nome della categoria non puo' essere null o vuoto.");
        if (usernameCreatore == null || usernameCreatore.isBlank())
            throw new IllegalArgumentException("Lo username del creatore non puo' essere null o vuoto.");
        if (campiOrdinati == null)
            throw new IllegalArgumentException("La mappa dei campi non puo' essere null.");
        if (valori == null)
            throw new IllegalArgumentException("La mappa dei valori non puo' essere null.");
        if (stato == null)
            throw new IllegalArgumentException("Lo stato non puo' essere null.");

        this.id                = id;
        this.nomeCategoria     = nomeCategoria;
        this.usernameCreatore  = usernameCreatore;
        this.campiSnapshot     = new LinkedHashMap<>(campiOrdinati);
        this.valori            = new LinkedHashMap<>(valori);
        this.stato             = stato;
        this.dataPubblicazione = dataPubblicazione;
        this.aderenti          = aderenti     != null ? new ArrayList<>(aderenti)     : new ArrayList<>();
        this.storicoStati      = storicoStati != null ? new ArrayList<>(storicoStati) : new ArrayList<>();

        assert repOk() : "Invariante violato dopo costruzione deserializzazione Proposta";
    }

    /** Costruttore di compatibilita' V2 (senza aderenti e storico). */
    public Proposta(int id, String nomeCategoria, String usernameCreatore,
                    LinkedHashMap<String, Boolean> campiOrdinati,
                    Map<String, String> valori, StatoProposta stato,
                    LocalDate dataPubblicazione) {
        this(id, nomeCategoria, usernameCreatore, campiOrdinati, valori, stato,
             dataPubblicazione, new ArrayList<>(), new ArrayList<>());
    }

    // ================================================================
    // GESTIONE VALORI
    // ================================================================

    public void setValore(String nomeCampo, String valore) {
        if (!campiSnapshot.containsKey(nomeCampo))
            throw new IllegalArgumentException("Campo non presente nella proposta: '" + nomeCampo + "'");
        if (stato == StatoProposta.APERTA || stato == StatoProposta.CONFERMATA
                || stato == StatoProposta.ANNULLATA || stato == StatoProposta.CONCLUSA
                || stato == StatoProposta.RITIRATA)
            throw new IllegalStateException("Non e' possibile modificare una proposta gia' pubblicata.");
        valori.put(nomeCampo, (valore == null) ? "" : valore.trim());
    }

    public String getValore(String nomeCampo) {
        return valori.getOrDefault(nomeCampo, "");
    }

    // ================================================================
    // VALIDAZIONE E STATO
    // ================================================================

    public void aggiornaStato(LocalDate dataOggi) {
        if (stato == StatoProposta.APERTA || stato == StatoProposta.CONFERMATA
                || stato == StatoProposta.ANNULLATA || stato == StatoProposta.CONCLUSA
                || stato == StatoProposta.RITIRATA) return;
        stato = validazioneErrori(dataOggi).isEmpty()
                ? StatoProposta.VALIDA
                : StatoProposta.BOZZA;
    }

    public List<String> validazioneErrori(LocalDate dataOggi) {
        List<String> errori = new ArrayList<>();

        for (Map.Entry<String, Boolean> entry : campiSnapshot.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                if (valori.getOrDefault(entry.getKey(), "").isBlank())
                    errori.add("Campo obbligatorio non compilato: '" + entry.getKey() + "'");
            }
        }

        String strTermine  = getValore(CAMPO_TERMINE_ISCRIZIONE);
        String strData     = getValore(CAMPO_DATA);
        String strDataConc = getValore(CAMPO_DATA_CONCLUSIVA);

        LocalDate termine  = parseDateSafe(strTermine);
        LocalDate data     = parseDateSafe(strData);
        LocalDate dataConc = parseDateSafe(strDataConc);

        if (!strTermine.isBlank() && termine == null)
            errori.add("'" + CAMPO_TERMINE_ISCRIZIONE + "': formato data non valido (usare gg/mm/aaaa).");
        if (!strData.isBlank() && data == null)
            errori.add("'" + CAMPO_DATA + "': formato data non valido (usare gg/mm/aaaa).");
        if (!strDataConc.isBlank() && dataConc == null)
            errori.add("'" + CAMPO_DATA_CONCLUSIVA + "': formato data non valido (usare gg/mm/aaaa).");

        if (termine != null && !termine.isAfter(dataOggi))
            errori.add("'" + CAMPO_TERMINE_ISCRIZIONE + "' deve essere successivo alla data odierna ("
                    + dataOggi.format(DATE_FORMAT) + ").");

        if (termine != null && data != null) {
            LocalDate minimaData = termine.plusDays(2);
            if (data.isBefore(minimaData))
                errori.add("'" + CAMPO_DATA + "' deve essere almeno 2 giorni dopo '"
                        + CAMPO_TERMINE_ISCRIZIONE + "' (minimo: " + minimaData.format(DATE_FORMAT) + ").");
        }

        if (data != null && dataConc != null && dataConc.isBefore(data))
            errori.add("'" + CAMPO_DATA_CONCLUSIVA + "' non puo' essere precedente a '" + CAMPO_DATA + "'.");

        String strNumPart = getValore(CAMPO_NUM_PARTECIPANTI);
        if (!strNumPart.isBlank()) {
            try {
                int n = Integer.parseInt(strNumPart.trim());
                if (n <= 0)
                    errori.add("'" + CAMPO_NUM_PARTECIPANTI + "' deve essere un numero intero positivo.");
            } catch (NumberFormatException e) {
                errori.add("'" + CAMPO_NUM_PARTECIPANTI + "': valore non numerico.");
            }
        }

        return errori;
    }

    public void pubblicaInBacheca(LocalDate dataPubblicazione) {
        if (stato != StatoProposta.VALIDA)
            throw new IllegalStateException("Solo le proposte VALIDE possono essere pubblicate in bacheca.");
        if (dataPubblicazione == null)
            throw new IllegalArgumentException("La data di pubblicazione non puo' essere null.");
        this.stato             = StatoProposta.APERTA;
        this.dataPubblicazione = dataPubblicazione;
        this.storicoStati.add(new CambioStato(StatoProposta.APERTA, dataPubblicazione));
        assert repOk() : "Invariante violato dopo pubblicaInBacheca";
    }

    public void revertToValida() {
        if (stato != StatoProposta.APERTA)
            throw new IllegalStateException("revertToValida puo' essere chiamato solo su proposte APERTE.");
        if (!storicoStati.isEmpty())
            storicoStati.remove(storicoStati.size() - 1);
        this.stato             = StatoProposta.VALIDA;
        this.dataPubblicazione = null;
        assert repOk() : "Invariante violato dopo revertToValida";
    }

    // ================================================================
    // TRANSIZIONI DI STATO (V3/V4)
    // ================================================================

    /**
     * Esegue un passaggio di stato verso CONFERMATA, ANNULLATA, CONCLUSA o RITIRATA.
     *
     * Transizioni valide:
     *   APERTA     -> CONFERMATA (automatica, V3)
     *   APERTA     -> ANNULLATA  (automatica, V3)
     *   CONFERMATA -> CONCLUSA   (automatica, V3)
     *   APERTA     -> RITIRATA   (manuale configuratore, V4)
     *   CONFERMATA -> RITIRATA   (manuale configuratore, V4)
     */
    public void transitaStato(StatoProposta nuovoStato, LocalDate data) {
        if (nuovoStato == null || data == null)
            throw new IllegalArgumentException("Stato e data non possono essere null.");

        boolean valida =
            (stato == StatoProposta.APERTA     && nuovoStato == StatoProposta.CONFERMATA) ||
            (stato == StatoProposta.APERTA     && nuovoStato == StatoProposta.ANNULLATA)  ||
            (stato == StatoProposta.CONFERMATA && nuovoStato == StatoProposta.CONCLUSA)   ||
            (stato == StatoProposta.APERTA     && nuovoStato == StatoProposta.RITIRATA)   ||
            (stato == StatoProposta.CONFERMATA && nuovoStato == StatoProposta.RITIRATA);

        if (!valida)
            throw new IllegalStateException("Transizione non valida: " + stato + " -> " + nuovoStato);

        this.stato = nuovoStato;
        this.storicoStati.add(new CambioStato(nuovoStato, data));
        assert repOk() : "Invariante violato dopo transitaStato";
    }

    // ================================================================
    // GESTIONE ADERENTI (V3/V4)
    // ================================================================

    /**
     * Iscrive un fruitore alla proposta.
     *
     * @param usernameF username del fruitore
     * @param oggi      data corrente
     * @return stringa vuota se successo, messaggio di errore altrimenti
     *
     * Precondizioni:
     * - stato == APERTA
     * - isIscrizioneAperta(oggi) == true
     * - fruitore non gia' iscritto
     * - aderenti.size() < numMaxPartecipanti
     */
    public String aggiungiAderente(String usernameF, LocalDate oggi) {
        if (usernameF == null || usernameF.isBlank())
            return "Username non valido.";
        if (stato != StatoProposta.APERTA)
            return "La proposta non e' in stato APERTA.";
        if (!isIscrizioneAperta(oggi))
            return "Il termine ultimo di iscrizione e' scaduto.";
        if (isAderito(usernameF))
            return "Sei gia' iscritto a questa proposta.";
        int numMax = getNumeroMaxPartecipanti();
        if (numMax < 0)
            return "Numero di partecipanti non valido: campo corrotto o non compilato.";
        if (aderenti.size() >= numMax)
            return "La proposta ha raggiunto il numero massimo di partecipanti.";

        aderenti.add(usernameF);
        assert repOk() : "Invariante violato dopo aggiungiAderente";
        return "";
    }

    /**
     * Rimuove l'iscrizione di un fruitore dalla proposta.
     * [NUOVO V4] Un fruitore puo' disdire la propria iscrizione fino al termine.
     *
     * Postcondizione: se la rimozione ha successo, il fruitore NON e' piu' nell'elenco
     *                 degli aderenti, ma potra' re-iscriversi (rispettando il termine).
     *
     * @param usernameF username del fruitore
     * @param oggi      data corrente
     * @return stringa vuota se rimosso, messaggio di errore altrimenti
     */
    public String rimuoviAderente(String usernameF, LocalDate oggi) {
        if (stato != StatoProposta.APERTA)
            return "Non e' possibile disdire un'iscrizione a una proposta non aperta.";
        if (!isIscrizioneAperta(oggi))
            return "Il termine ultimo di iscrizione e' scaduto: non e' piu' possibile disdire.";
        if (!isAderito(usernameF))
            return "Non risulti iscritto a questa proposta.";
        aderenti.removeIf(u -> u.equalsIgnoreCase(usernameF));
        assert repOk() : "Invariante violato dopo rimuoviAderente";
        return "";
    }

    // ================================================================
    // HELPER V4: ritiro proposta
    // ================================================================

    /**
     * Verifica se il configuratore puo' ritirare la proposta nella data indicata.
     *
     * Il ritiro e' consentito (spec. V4) fino alle 23:59 del giorno PRECEDENTE
     * alla data "Data" dell'iniziativa. L'applicazione non gestisce l'ora, quindi:
     * la condizione diventa: oggi < getData() (strettamente minore).
     *
     * Precondizione: la proposta deve essere in stato APERTA o CONFERMATA.
     *
     * @param oggi data corrente
     * @return stringa vuota se il ritiro e' consentito, messaggio di errore altrimenti
     */
    public String verificaRitiroConsentito(LocalDate oggi) {
        if (stato != StatoProposta.APERTA && stato != StatoProposta.CONFERMATA)
            return "Il ritiro e' consentito solo per proposte APERTE o CONFERMATE (stato attuale: " + stato + ").";
        LocalDate dataInizio = parseDateSafe(getValore(CAMPO_DATA));
        if (dataInizio == null)
            return "Il campo '" + CAMPO_DATA + "' non e' valorizzato o ha formato non valido.";
        // oggi deve essere strettamente prima di dataInizio
        if (!oggi.isBefore(dataInizio))
            return "Non e' piu' possibile ritirare la proposta: la data dell'evento e' gia' oggi o passata.";
        return "";
    }

    // ================================================================
    // HELPER DI DOMINIO (V3/V4)
    // ================================================================

    public boolean isIscrizioneAperta(LocalDate oggi) {
        LocalDate termine = getTermineIscrizione();
        if (termine == null) return false;
        return !oggi.isAfter(termine);
    }

    public LocalDate getTermineIscrizione() {
        return parseDateSafe(getValore(CAMPO_TERMINE_ISCRIZIONE));
    }

    public LocalDate getDataConclusiva() {
        return parseDateSafe(getValore(CAMPO_DATA_CONCLUSIVA));
    }

    /**
     * @return numero max partecipanti, o -1 se campo non valido/non compilato
     */
    public int getNumeroMaxPartecipanti() {
        try { return Integer.parseInt(getValore(CAMPO_NUM_PARTECIPANTI).trim()); }
        catch (NumberFormatException e) { return -1; }
    }

    public boolean isAderito(String usernameF) {
        if (usernameF == null) return false;
        return aderenti.stream().anyMatch(u -> u.equalsIgnoreCase(usernameF));
    }

    // ================================================================
    // GETTERS
    // ================================================================

    public int                            getId()                { return id; }
    public String                         getNomeCategoria()     { return nomeCategoria; }
    public String                         getUsernameCreatore()  { return usernameCreatore; }
    public StatoProposta                  getStato()             { return stato; }
    public LocalDate                      getDataPubblicazione() { return dataPubblicazione; }
    public LinkedHashMap<String, Boolean> getCampiSnapshot()     { return new LinkedHashMap<>(campiSnapshot); }
    public Map<String, String>            getValori()            { return Collections.unmodifiableMap(valori); }
    public List<String>                   getAderenti()          { return Collections.unmodifiableList(aderenti); }
    public List<CambioStato>              getStoricoStati()      { return Collections.unmodifiableList(storicoStati); }

    // ================================================================
    // INVARIANTE
    // ================================================================

    public boolean repOk() {
        if (id < 0) return false;
        if (nomeCategoria == null || nomeCategoria.isBlank()) return false;
        if (usernameCreatore == null || usernameCreatore.isBlank()) return false;
        if (campiSnapshot == null || valori == null || stato == null) return false;
        if (aderenti == null || storicoStati == null) return false;
        boolean pubblicata = (stato == StatoProposta.APERTA
                           || stato == StatoProposta.CONFERMATA
                           || stato == StatoProposta.ANNULLATA
                           || stato == StatoProposta.CONCLUSA
                           || stato == StatoProposta.RITIRATA);
        if (pubblicata && dataPubblicazione == null) return false;
        if (!pubblicata && dataPubblicazione != null) return false;
        return true;
    }

    // ================================================================
    // UTILITY
    // ================================================================

    public static LocalDate parseDateSafe(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s.trim(), DATE_FORMAT); }
        catch (DateTimeParseException e) { return null; }
    }

    @Override
    public String toString() {
        String titolo = getValore("Titolo");
        String display = titolo.isBlank() ? "(senza titolo)" : titolo;
        return String.format("[ID %d] cat:%s \"%s\" (%s)", id, nomeCategoria, display, stato);
    }
}