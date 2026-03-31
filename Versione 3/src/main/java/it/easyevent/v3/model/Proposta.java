package it.easyevent.v3.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * Rappresenta una proposta di iniziativa (Versione 3).
 *
 * Ciclo di vita in V2/V3:
 *   BOZZA -> (campi + vincoli OK) -> VALIDA -> (pubblicazione) -> APERTA
 *   APERTA -> (termine scaduto, aderenti >= num.partecipanti) -> CONFERMATA
 *   APERTA -> (termine scaduto, aderenti <  num.partecipanti) -> ANNULLATA
 *   CONFERMATA -> (giorno dopo dataConclusiva) -> CONCLUSA
 *
 * Transizioni aggiunte in V4:
 *   APERTA     -> RITIRATA  (ritiro da parte del configuratore)
 *   CONFERMATA -> RITIRATA  (ritiro da parte del configuratore)
 *
 * Novita' rispetto a V2:
 *   - aderenti: lista degli username dei fruitori iscritti
 *   - storicoStati: cronologia dei passaggi di stato con date
 *   - aggiungiAderente / rimuoviAderente (V4 per rimozione)
 *   - transitaStato: cambia stato e registra nel cronologico
 *   - helper: getTermineIscrizione, getDataConclusiva, getNumeroMaxPartecipanti
 *
 * Invariante di classe:
 *   - id >= 0
 *   - nomeCategoria != null && !nomeCategoria.isBlank()
 *   - usernameCreatore != null && !usernameCreatore.isBlank()
 *   - campiSnapshot != null
 *   - valori != null, contiene una chiave per ogni campo nello snapshot
 *   - stato != null
 *   - dataPubblicazione != null <-> stato in {APERTA, CONFERMATA, ANNULLATA, CONCLUSA, RITIRATA}
 *   - aderenti != null
 *   - storicoStati != null
 */
public class Proposta {

    /**
     * Formato data usato nell'applicazione: gg/mm/aaaa.
     */
    public static final DateTimeFormatter DATE_FORMAT
            = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Nomi dei campi base coinvolti nei vincoli di data
    public static final String CAMPO_TERMINE_ISCRIZIONE = "Termine ultimo di iscrizione";
    public static final String CAMPO_DATA = "Data";
    public static final String CAMPO_DATA_CONCLUSIVA = "Data conclusiva";
    public static final String CAMPO_NUM_PARTECIPANTI = "Numero di partecipanti";
    private static final String CAMPO_ORA = "Ora";

    // ---- Dati identificativi ----
    private final int id;
    private final String nomeCategoria;
    private final String usernameCreatore;

    // ---- Snapshot ordinato dei campi al momento della creazione ----
    private final LinkedHashMap<String, Boolean> campiSnapshot;

    // ---- Valori correnti dei campi ----
    private final Map<String, String> valori;

    // ---- Stato e metadati di pubblicazione ----
    private StatoProposta stato;
    private LocalDate dataPubblicazione;

    // ---- V3: aderenti e storico stati ----
    private final List<String> aderenti;
    private final List<CambioStato> storicoStati;

    // ================================================================
    // INNER CLASS: CambioStato
    // ================================================================
    /**
     * Rappresenta un singolo passaggio di stato con la relativa data.
     */
    public static class CambioStato {

        public final StatoProposta stato;
        public final LocalDate data;

        public CambioStato(StatoProposta stato, LocalDate data) {
            if (stato == null || data == null) {
                throw new IllegalArgumentException("Stato e data non possono essere null.");
            }
            this.stato = stato;
            this.data = data;
        }

        @Override
        public String toString() {
            return stato.name() + " (" + data.format(DATE_FORMAT) + ")";
        }
    }

    // ================================================================
    // COSTRUTTORI
    // ================================================================
    /**
     * Costruttore per proposte create interattivamente in sessione (V2).
     */
    public Proposta(int id, String nomeCategoria, String usernameCreatore,
            LinkedHashMap<String, Boolean> campiOrdinati) {
        if (id < 0) {
            throw new IllegalArgumentException("L'id della proposta non puo' essere negativo.");
        }
        if (nomeCategoria == null || nomeCategoria.isBlank()) {
            throw new IllegalArgumentException("Il nome della categoria non puo' essere null o vuoto.");
        }
        if (usernameCreatore == null || usernameCreatore.isBlank()) {
            throw new IllegalArgumentException("Lo username del creatore non puo' essere null o vuoto.");
        }
        if (campiOrdinati == null) {
            throw new IllegalArgumentException("La mappa dei campi non puo' essere null.");
        }

        this.id = id;
        this.nomeCategoria = nomeCategoria;
        this.usernameCreatore = usernameCreatore;
        this.campiSnapshot = new LinkedHashMap<>(campiOrdinati);
        this.valori = new LinkedHashMap<>();
        this.stato = StatoProposta.BOZZA;
        this.dataPubblicazione = null;
        this.aderenti = new ArrayList<>();
        this.storicoStati = new ArrayList<>();

        for (String nomeCampo : campiOrdinati.keySet()) {
            valori.put(nomeCampo, "");
        }

        assert repOk() : "Invariante violato dopo costruzione Proposta";
    }

    /**
     * Costruttore per la deserializzazione (V3). Applica le stesse
     * precondizioni del costruttore interattivo per difendersi da JSON corrotti
     * che potrebbero produrre NullPointerException a runtime.
     */
    public Proposta(int id, String nomeCategoria, String usernameCreatore,
            LinkedHashMap<String, Boolean> campiOrdinati,
            Map<String, String> valori, StatoProposta stato,
            LocalDate dataPubblicazione,
            List<String> aderenti,
            List<CambioStato> storicoStati) {
        if (id < 0) {
            throw new IllegalArgumentException("L'id della proposta non puo' essere negativo.");
        }
        if (nomeCategoria == null || nomeCategoria.isBlank()) {
            throw new IllegalArgumentException("Il nome della categoria non puo' essere null o vuoto.");
        }
        if (usernameCreatore == null || usernameCreatore.isBlank()) {
            throw new IllegalArgumentException("Lo username del creatore non puo' essere null o vuoto.");
        }
        if (campiOrdinati == null) {
            throw new IllegalArgumentException("La mappa dei campi non puo' essere null.");
        }
        if (valori == null) {
            throw new IllegalArgumentException("La mappa dei valori non puo' essere null.");
        }
        if (stato == null) {
            throw new IllegalArgumentException("Lo stato non puo' essere null.");
        }
        this.id = id;
        this.nomeCategoria = nomeCategoria;
        this.usernameCreatore = usernameCreatore;
        this.campiSnapshot = new LinkedHashMap<>(campiOrdinati);
        this.valori = new LinkedHashMap<>(valori);
        this.stato = stato;
        this.dataPubblicazione = dataPubblicazione;
        this.aderenti = aderenti != null ? new ArrayList<>(aderenti) : new ArrayList<>();
        this.storicoStati = storicoStati != null ? new ArrayList<>(storicoStati) : new ArrayList<>();

        assert repOk() : "Invariante violato dopo costruzione deserializzazione Proposta";
    }

    /**
     * Costruttore di compatibilita' con V2 (senza aderenti e storico).
     */
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
    /**
     * Imposta il valore di un campo della proposta.
     */
    public void setValore(String nomeCampo, String valore) {
        if (!campiSnapshot.containsKey(nomeCampo)) {
            throw new IllegalArgumentException("Campo non presente nella proposta: '" + nomeCampo + "'");
        }
        if (stato == StatoProposta.APERTA || stato == StatoProposta.CONFERMATA
                || stato == StatoProposta.ANNULLATA || stato == StatoProposta.CONCLUSA
                || stato == StatoProposta.RITIRATA) {
            throw new IllegalStateException("Non e' possibile modificare una proposta gia' pubblicata.");
        }
        valori.put(nomeCampo, (valore == null) ? "" : valore.trim());
    }

    public String getValore(String nomeCampo) {
        return valori.getOrDefault(nomeCampo, "");
    }

    // ================================================================
    // VALIDAZIONE E STATO (V2)
    // ================================================================
    public void aggiornaStato(LocalDate dataOggi) {
        // Non ricalcolare lo stato per proposte già pubblicate o ritirate (V4).
        // RITIRATA inclusa: una proposta ritirata non deve mai tornare a BOZZA/VALIDA.
        if (stato == StatoProposta.APERTA || stato == StatoProposta.CONFERMATA
                || stato == StatoProposta.ANNULLATA || stato == StatoProposta.CONCLUSA
                || stato == StatoProposta.RITIRATA) {
            return;
        }
        stato = validazioneErrori(dataOggi).isEmpty()
                ? StatoProposta.VALIDA
                : StatoProposta.BOZZA;
    }

    public List<String> validazioneErrori(LocalDate dataOggi) {
        List<String> errori = new ArrayList<>();

        for (Map.Entry<String, Boolean> entry : campiSnapshot.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                if (valori.getOrDefault(entry.getKey(), "").isBlank()) {
                    errori.add("Campo obbligatorio non compilato: '" + entry.getKey() + "'");
                }
            }
        }

        String strTermine = getValore(CAMPO_TERMINE_ISCRIZIONE);
        String strData = getValore(CAMPO_DATA);
        String strDataConc = getValore(CAMPO_DATA_CONCLUSIVA);

        LocalDate termine = parseDateSafe(strTermine);
        LocalDate data = parseDateSafe(strData);
        LocalDate dataConc = parseDateSafe(strDataConc);

        if (!strTermine.isBlank() && termine == null) {
            errori.add("'" + CAMPO_TERMINE_ISCRIZIONE + "': formato data non valido (usare gg/mm/aaaa).");
        }
        if (!strData.isBlank() && data == null) {
            errori.add("'" + CAMPO_DATA + "': formato data non valido (usare gg/mm/aaaa).");
        }
        if (!strDataConc.isBlank() && dataConc == null) {
            errori.add("'" + CAMPO_DATA_CONCLUSIVA + "': formato data non valido (usare gg/mm/aaaa).");
        }

        if (termine != null && !termine.isAfter(dataOggi)) {
            errori.add("'" + CAMPO_TERMINE_ISCRIZIONE + "' deve essere successivo alla data odierna ("
                    + dataOggi.format(DATE_FORMAT) + ").");
        }

        if (termine != null && data != null) {
            LocalDate minimaData = termine.plusDays(2);
            if (data.isBefore(minimaData)) {
                errori.add("'" + CAMPO_DATA + "' deve essere almeno 2 giorni dopo '"
                        + CAMPO_TERMINE_ISCRIZIONE + "' (minimo: " + minimaData.format(DATE_FORMAT) + ").");
            }
        }

        if (data != null && dataConc != null && dataConc.isBefore(data)) {
            errori.add("'" + CAMPO_DATA_CONCLUSIVA + "' non puo' essere precedente a '" + CAMPO_DATA + "'.");
        }

        String strOra = getValore(CAMPO_ORA);
        if (!strOra.isBlank() && !isFormatoOraValido(strOra)) {
            errori.add("'" + CAMPO_ORA
                    + "': formato non valido (usare HH:MM, es. 09:30 oppure 14:00).");
        }

        // Verifica che "Numero di partecipanti" sia un intero strettamente positivo.
        // Il controllo "campo obbligatorio non blank" sopra garantisce solo che il valore
        // esista, non che sia valido. Con "0" la proposta diventa pubblicabile, blocca
        // tutte le iscrizioni (aderenti.size() >= 0 sempre vero) e si auto-conferma
        // a scadenza con 0 iscritti; con negativi il comportamento e' altrettanto errato.
        String strNumPart = getValore(CAMPO_NUM_PARTECIPANTI);
        if (!strNumPart.isBlank()) {
            try {
                int n = Integer.parseInt(strNumPart.trim());
                if (n <= 0) {
                    errori.add("'" + CAMPO_NUM_PARTECIPANTI
                            + "' deve essere un numero intero positivo (valore attuale: " + n + ").");
                }
            } catch (NumberFormatException e) {
                errori.add("'" + CAMPO_NUM_PARTECIPANTI
                        + "': valore non numerico (inserire un numero intero positivo).");
            }
        }

        return errori;
    }

    private static boolean isFormatoOraValido(String ora) {
        // Deve contenere esattamente un ':'
        int sep = ora.indexOf(':');
        if (sep < 0 || sep != ora.lastIndexOf(':')) {
            return false;
        }

        String parteOre = ora.substring(0, sep).trim();
        String parteMinuti = ora.substring(sep + 1).trim();

        // Entrambe le parti devono essere non vuote e composte solo da cifre
        if (parteOre.isEmpty() || parteMinuti.isEmpty()) {
            return false;
        }
        for (char c : parteOre.toCharArray()) {
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        for (char c : parteMinuti.toCharArray()) {
            if (!Character.isDigit(c)) {
                return false;
            }
        }

        // Lunghezza massima 2 cifre per parte (evita overflow e formati tipo "009")
        if (parteOre.length() > 2 || parteMinuti.length() > 2) {
            return false;
        }

        try {
            int h = Integer.parseInt(parteOre);
            int m = Integer.parseInt(parteMinuti);
            return h >= 0 && h <= 23 && m >= 0 && m <= 59;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Pubblica la proposta in bacheca. Precondizione: stato == VALIDA.
     */
    public void pubblicaInBacheca(LocalDate dataPubblicazione) {
        if (stato != StatoProposta.VALIDA) {
            throw new IllegalStateException("Solo le proposte VALIDE possono essere pubblicate in bacheca.");
        }
        if (dataPubblicazione == null) {
            throw new IllegalArgumentException("La data di pubblicazione non puo' essere null.");
        }
        this.stato = StatoProposta.APERTA;
        this.dataPubblicazione = dataPubblicazione;
        this.storicoStati.add(new CambioStato(StatoProposta.APERTA, dataPubblicazione));

        assert this.stato == StatoProposta.APERTA : "Postcondizione violata: stato non APERTA";
        assert repOk() : "Invariante violato dopo pubblicaInBacheca";
    }

    /**
     * Riporta la proposta allo stato VALIDA annullando una pubblicazione
     * fallita. Da usare ESCLUSIVAMENTE come rollback quando pubblicaInBacheca()
     * ha avuto successo ma il salvataggio su disco ha fallito.
     *
     * Precondizione: stato == APERTA e storicoStati non vuoto.
     */
    public void revertToValida() {
        if (stato != StatoProposta.APERTA) {
            throw new IllegalStateException("revertToValida puo' essere chiamato solo su proposte APERTE.");
        }
        // Rimuovi l'ultimo cambio di stato (quello APERTA aggiunto da pubblicaInBacheca)
        if (!storicoStati.isEmpty()) {
            storicoStati.remove(storicoStati.size() - 1);
        }
        this.stato = StatoProposta.VALIDA;
        this.dataPubblicazione = null;

        assert repOk() : "Invariante violato dopo revertToValida";
    }

    // ================================================================
    // TRANSIZIONI DI STATO (V3)
    // ================================================================
    /**
     * Esegue un passaggio di stato verso CONFERMATA, ANNULLATA, CONCLUSA o
     * RITIRATA. Registra il cambio nello storico.
     *
     * Transizioni valide in V3: APERTA -> CONFERMATA APERTA -> ANNULLATA
     * CONFERMATA -> CONCLUSA
     *
     * Transizioni aggiunte in V4: APERTA -> RITIRATA (ritiro da parte del
     * configuratore) CONFERMATA -> RITIRATA (ritiro da parte del configuratore)
     *
     * @param nuovoStato nuovo stato della proposta
     * @param data data del passaggio di stato
     * @throws IllegalStateException se la transizione non e' valida
     * @throws IllegalArgumentException se nuovoStato o data sono null
     */
    public void transitaStato(StatoProposta nuovoStato, LocalDate data) {
        if (nuovoStato == null || data == null) {
            throw new IllegalArgumentException("Stato e data non possono essere null.");
        }

        boolean valida
                = (stato == StatoProposta.APERTA && nuovoStato == StatoProposta.CONFERMATA)
                || (stato == StatoProposta.APERTA && nuovoStato == StatoProposta.ANNULLATA)
                || (stato == StatoProposta.CONFERMATA && nuovoStato == StatoProposta.CONCLUSA)
                || // V4: il configuratore puo' ritirare una proposta aperta o confermata
                (stato == StatoProposta.APERTA && nuovoStato == StatoProposta.RITIRATA)
                || (stato == StatoProposta.CONFERMATA && nuovoStato == StatoProposta.RITIRATA);

        if (!valida) {
            throw new IllegalStateException(
                    "Transizione non valida: " + stato + " -> " + nuovoStato);
        }

        this.stato = nuovoStato;
        this.storicoStati.add(new CambioStato(nuovoStato, data));

        assert repOk() : "Invariante violato dopo transitaStato";
    }

    // ================================================================
    // GESTIONE ADERENTI (V3)
    // ================================================================
    /**
     * Iscrive un fruitore alla proposta.
     *
     * @param usernameF username del fruitore, non null/blank
     * @param oggi data corrente (per verifica termine iscrizione)
     * @return stringa vuota se l'iscrizione e' avvenuta, messaggio di errore
     * altrimenti
     */
    public String aggiungiAderente(String usernameF, LocalDate oggi) {
        if (usernameF == null || usernameF.isBlank()) {
            return "Username non valido.";
        }
        if (stato != StatoProposta.APERTA) {
            return "La proposta non e' in stato APERTA.";
        }
        if (!isIscrizioneAperta(oggi)) {
            return "Il termine ultimo di iscrizione e' scaduto.";
        }
        if (isAderito(usernameF)) {
            return "Sei gia' iscritto a questa proposta.";
        }
        int numMax = getNumeroMaxPartecipanti();
        if (numMax < 0) {
            return "Numero di partecipanti non valido: campo corrotto o non compilato.";
        }
        if (aderenti.size() >= numMax) {
            return "La proposta ha raggiunto il numero massimo di partecipanti.";
        }

        aderenti.add(usernameF);

        assert repOk() : "Invariante violato dopo aggiungiAderente";
        return "";
    }

    /**
     * Rimuove l'iscrizione di un fruitore dalla proposta (V4+).
     *
     * @param usernameF username del fruitore
     * @param oggi data corrente
     * @return stringa vuota se rimosso, messaggio di errore altrimenti
     */
    public String rimuoviAderente(String usernameF, LocalDate oggi) {
        if (stato != StatoProposta.APERTA) {
            return "Non e' possibile disdire un'iscrizione a una proposta non aperta.";
        }
        if (!isIscrizioneAperta(oggi)) {
            return "Il termine ultimo di iscrizione e' scaduto.";
        }
        if (!isAderito(usernameF)) {
            return "Non risulti iscritto a questa proposta.";
        }
        aderenti.removeIf(u -> u.equalsIgnoreCase(usernameF));
        return "";
    }

    // ================================================================
    // HELPER DI DOMINIO (V3)
    // ================================================================
    /**
     * @return true se le iscrizioni sono ancora aperte (oggi <= termine)
     */
    public boolean isIscrizioneAperta(LocalDate oggi) {
        LocalDate termine = getTermineIscrizione();
        if (termine == null) {
            return false;
        }
        return !oggi.isAfter(termine);
    }

    /**
     * @return il "Termine ultimo di iscrizione" come LocalDate, o null se non
     * compilato/invalido
     */
    public LocalDate getTermineIscrizione() {
        return parseDateSafe(getValore(CAMPO_TERMINE_ISCRIZIONE));
    }

    /**
     * @return la "Data conclusiva" come LocalDate, o null se non
     * compilata/invalida
     */
    public LocalDate getDataConclusiva() {
        return parseDateSafe(getValore(CAMPO_DATA_CONCLUSIVA));
    }

    /**
     * @return il numero massimo di partecipanti, oppure -1 se il campo non e'
     * un intero valido o non e' compilato. Nota: una proposta con questo campo
     * non valorizzato non dovrebbe mai essere pubblicata (e' obbligatorio e la
     * validazione lo impedirebbe). Il valore -1 viene usato come sentinella di
     * errore: aggiornaTransizioni() in AppData lo tratta come proposta non
     * confermabile (transita in ANNULLATA), rendendo visibile il dato corrotto
     * senza silenziarlo.
     */
    public int getNumeroMaxPartecipanti() {
        try {
            return Integer.parseInt(getValore(CAMPO_NUM_PARTECIPANTI).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * @return true se il fruitore con tale username e' gia' iscritto
     */
    public boolean isAderito(String usernameF) {
        if (usernameF == null) {
            return false;
        }
        return aderenti.stream().anyMatch(u -> u.equalsIgnoreCase(usernameF));
    }

    // ================================================================
    // GETTERS
    // ================================================================
    public int getId() {
        return id;
    }

    public String getNomeCategoria() {
        return nomeCategoria;
    }

    public String getUsernameCreatore() {
        return usernameCreatore;
    }

    public StatoProposta getStato() {
        return stato;
    }

    public LocalDate getDataPubblicazione() {
        return dataPubblicazione;
    }

    public LinkedHashMap<String, Boolean> getCampiSnapshot() {
        return new LinkedHashMap<>(campiSnapshot);
    }

    public Map<String, String> getValori() {
        return Collections.unmodifiableMap(valori);
    }

    public List<String> getAderenti() {
        return Collections.unmodifiableList(aderenti);
    }

    public List<CambioStato> getStoricoStati() {
        return Collections.unmodifiableList(storicoStati);
    }

    // ================================================================
    // INVARIANTE
    // ================================================================
    public boolean repOk() {
        if (id < 0) {
            return false;
        }
        if (nomeCategoria == null || nomeCategoria.isBlank()) {
            return false;
        }
        if (usernameCreatore == null || usernameCreatore.isBlank()) {
            return false;
        }
        if (campiSnapshot == null || valori == null || stato == null) {
            return false;
        }
        if (aderenti == null || storicoStati == null) {
            return false;
        }
        // Tutti gli stati che implicano la presenza di dataPubblicazione.
        // RITIRATA e' inclusa: una proposta ritirata era gia' APERTA o CONFERMATA
        // e quindi ha sempre una dataPubblicazione valorizzata.
        boolean pubblicata = (stato == StatoProposta.APERTA
                || stato == StatoProposta.CONFERMATA
                || stato == StatoProposta.ANNULLATA
                || stato == StatoProposta.CONCLUSA
                || stato == StatoProposta.RITIRATA);
        if (pubblicata && dataPubblicazione == null) {
            return false;
        }
        if (!pubblicata && dataPubblicazione != null) {
            return false;
        }
        return true;
    }

    // ================================================================
    // UTILITY
    // ================================================================
    public static LocalDate parseDateSafe(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
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
        return String.format("[ID %d] cat:%s \"%s\" (%s)",
                id, nomeCategoria, display, stato);
    }
}
