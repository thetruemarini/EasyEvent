package easyevent.proposta;

import easyevent.exception.IscrizioneException;
import easyevent.exception.ModificaNonConsentitaException;
import easyevent.exception.RitiroNonConsensitoException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rappresenta una proposta di iniziativa (Versione 5).
 *
 * Ciclo di vita (invariato da V4): BOZZA -> VALIDA -> APERTA (pubblicazione
 * interattiva o batch) APERTA -> CONFERMATA / ANNULLATA (automatico, alla
 * scadenza) CONFERMATA -> CONCLUSA (automatico, dopo dataConclusiva) APERTA /
 * CONFERMATA -> RITIRATA (configuratore, V4)
 *
 * Invariante di classe: - id >= 0 - nomeCategoria, usernameCreatore != null e
 * non blank - campiSnapshot, valori, stato, aderenti, storicoStati != null -
 * dataPubblicazione != null <-> stato in {APERTA, CONFERMATA, ANNULLATA,
 * CONCLUSA, RITIRATA}
 */
public class Proposta {

    public static final DateTimeFormatter DATE_FORMAT
            = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static final String CAMPO_TERMINE_ISCRIZIONE = "Termine ultimo di iscrizione";
    public static final String CAMPO_DATA = "Data inizio";
    public static final String CAMPO_DATA_CONCLUSIVA = "Data conclusiva";
    public static final String CAMPO_NUM_PARTECIPANTI = "Numero di partecipanti";
    private static final String CAMPO_ORA = "Ora";

    private final int id;
    private final String nomeCategoria;
    private final String usernameCreatore;

    private final LinkedHashMap<String, Boolean> campiSnapshot;
    private final Map<String, String> valori;

    private StatoProposta stato;
    private LocalDate dataPubblicazione;

    private final List<String> aderenti;
    private final List<CambioStato> storicoStati;

    // ================================================================
    // INNER CLASS: CambioStato
    // ================================================================
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
     * Costruttore di compatibilita' V2 (senza aderenti e storico).
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
    public void setValore(String nomeCampo, String valore) {
        if (!campiSnapshot.containsKey(nomeCampo)) {
            throw new ModificaNonConsentitaException(
                    ModificaNonConsentitaException.TipoModifica.CAMPO_NON_PRESENTE,
                    nomeCampo
            );
        }
        if (stato == StatoProposta.APERTA || stato == StatoProposta.CONFERMATA
                || stato == StatoProposta.ANNULLATA || stato == StatoProposta.CONCLUSA
                || stato == StatoProposta.RITIRATA) {
            throw new ModificaNonConsentitaException(
                    ModificaNonConsentitaException.TipoModifica.PROPOSTA_GIA_PUBBLICATA,
                    stato.name()
            );
        }
        valori.put(nomeCampo, (valore == null) ? "" : valore.trim());
    }

    public String getValore(String nomeCampo) {
        return valori.getOrDefault(nomeCampo, "");
    }

    // ================================================================
    // INTERROGAZIONE STRUTTURA CAMPI (Risoluzione Legge di Demetra)
    // ================================================================
    /**
     * Verifica se la proposta utilizza un determinato campo. Nasconde
     * l'implementazione interna (LinkedHashMap) all'esterno.
     */
    public boolean usaCampo(String nomeCampo) {
        if (nomeCampo == null) {
            return false;
        }
        return campiSnapshot.containsKey(nomeCampo);
    }

    /**
     * Verifica se un campo utilizzato dalla proposta è obbligatorio.
     */
    public boolean isCampoObbligatorio(String nomeCampo) {
        return campiSnapshot.getOrDefault(nomeCampo, false);
    }

    /**
     * Restituisce solo i nomi dei campi, proteggendo la mappa interna.
     */
    public List<String> getNomiCampi() {
        return new ArrayList<>(campiSnapshot.keySet());
    }

    // ================================================================
    // VALIDAZIONE E STATO
    // ================================================================
    public void aggiornaStato(LocalDate dataOggi) {
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

        String strNumPart = getValore(CAMPO_NUM_PARTECIPANTI);
        if (!strNumPart.isBlank()) {
            try {
                int n = Integer.parseInt(strNumPart.trim());
                if (n <= 0) {
                    errori.add("'" + CAMPO_NUM_PARTECIPANTI + "' deve essere un numero intero positivo.");
                }
            } catch (NumberFormatException e) {
                errori.add("'" + CAMPO_NUM_PARTECIPANTI + "': valore non numerico.");
            }
        }
        String strOra = getValore(CAMPO_ORA);
        if (!strOra.isBlank() && !isFormatoOraValido(strOra)) {
            errori.add("'" + CAMPO_ORA
                    + "': formato non valido (usare HH:MM, es. 09:30 oppure 14:00).");
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
        assert repOk() : "Invariante violato dopo pubblicaInBacheca";
    }

    public void revertToValida() {
        if (stato != StatoProposta.APERTA) {
            throw new IllegalStateException("revertToValida puo' essere chiamato solo su proposte APERTE.");
        }
        if (!storicoStati.isEmpty()) {
            storicoStati.remove(storicoStati.size() - 1);
        }
        this.stato = StatoProposta.VALIDA;
        this.dataPubblicazione = null;
        assert repOk() : "Invariante violato dopo revertToValida";
    }

    // ================================================================
    // TRANSIZIONI DI STATO
    // ================================================================
    public void transitaStato(StatoProposta nuovoStato, LocalDate data) {
        if (nuovoStato == null || data == null) {
            throw new IllegalArgumentException("Stato e data non possono essere null.");
        }

        boolean valida
                = (stato == StatoProposta.APERTA && nuovoStato == StatoProposta.CONFERMATA)
                || (stato == StatoProposta.APERTA && nuovoStato == StatoProposta.ANNULLATA)
                || (stato == StatoProposta.CONFERMATA && nuovoStato == StatoProposta.CONCLUSA)
                || (stato == StatoProposta.APERTA && nuovoStato == StatoProposta.RITIRATA)
                || (stato == StatoProposta.CONFERMATA && nuovoStato == StatoProposta.RITIRATA);

        if (!valida) {
            throw new IllegalStateException("Transizione non valida: " + stato + " -> " + nuovoStato);
        }

        this.stato = nuovoStato;
        this.storicoStati.add(new CambioStato(nuovoStato, data));
        assert repOk() : "Invariante violato dopo transitaStato";
    }

    // ================================================================
    // GESTIONE ADERENTI
    // ================================================================
    public void aggiungiAderente(String usernameF, LocalDate oggi) {
        if (usernameF == null || usernameF.isBlank()) {
            throw new IllegalArgumentException("usernameF non può essere null o vuoto");
        }
        if (stato != StatoProposta.APERTA) {
            throw new IscrizioneException(IscrizioneException.TipoErrore.PROPOSTA_NON_APERTA);
        }
        if (!isIscrizioneAperta(oggi)) {
            throw new IscrizioneException(IscrizioneException.TipoErrore.ISCRIZIONI_CHIUSE);
        }
        if (isAderito(usernameF)) {
            throw new IscrizioneException(IscrizioneException.TipoErrore.GIA_ISCRITTO);
        }
        int numMax = getNumeroMaxPartecipanti();
        if (numMax < 0) {
            throw new IscrizioneException(IscrizioneException.TipoErrore.NUM_PARTECIPANTI_NON_VALIDO);
        }
        if (aderenti.size() >= numMax) {
            throw new IscrizioneException(IscrizioneException.TipoErrore.POSTI_ESAURITI);
        }
        aderenti.add(usernameF);
        assert repOk() : "Invariante violato dopo aggiungiAderente";
    }

    public void rimuoviAderente(String usernameF, LocalDate oggi) {
        if (stato != StatoProposta.APERTA) {
            throw new IscrizioneException(IscrizioneException.TipoErrore.PROPOSTA_NON_APERTA);
        }
        if (!isIscrizioneAperta(oggi)) {
            throw new IscrizioneException(IscrizioneException.TipoErrore.ISCRIZIONI_CHIUSE);
        }
        if (!isAderito(usernameF)) {
            throw new IscrizioneException(IscrizioneException.TipoErrore.NON_ISCRITTO);
        }
        aderenti.removeIf(u -> u.equalsIgnoreCase(usernameF));
        assert repOk() : "Invariante violato dopo rimuoviAderente";
    }

    // ================================================================
    // HELPER V4: ritiro proposta
    // ================================================================
    public void verificaRitiroConsentito(LocalDate oggi) {
        if (stato != StatoProposta.APERTA && stato != StatoProposta.CONFERMATA) {
            throw new RitiroNonConsensitoException(
                    RitiroNonConsensitoException.TipoErrore.STATO_NON_RITIRABILE,
                    stato.name()
            );
        }
        LocalDate dataInizio = parseDateSafe(getValore(CAMPO_DATA));
        if (dataInizio == null) {
            throw new RitiroNonConsensitoException(
                    RitiroNonConsensitoException.TipoErrore.DATA_EVENTO_NON_VALORIZZATA,
                    null
            );
        }
        if (!oggi.isBefore(dataInizio)) {
            throw new RitiroNonConsensitoException(
                    RitiroNonConsensitoException.TipoErrore.DATA_EVENTO_PASSATA,
                    null
            );
        }
    }

    // ================================================================
    // HELPERS DI DOMINIO
    // ================================================================
    public boolean isIscrizioneAperta(LocalDate oggi) {
        LocalDate termine = getTermineIscrizione();
        if (termine == null) {
            return false;
        }
        return !oggi.isAfter(termine);
    }

    public LocalDate getTermineIscrizione() {
        return parseDateSafe(getValore(CAMPO_TERMINE_ISCRIZIONE));
    }

    public LocalDate getDataConclusiva() {
        return parseDateSafe(getValore(CAMPO_DATA_CONCLUSIVA));
    }

    public int getNumeroMaxPartecipanti() {
        try {
            return Integer.parseInt(getValore(CAMPO_NUM_PARTECIPANTI).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

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

    // solo per debug
    @Override
    public String toString() {
        return "Proposta{id=" + id
                + ", categoria='" + nomeCategoria + "'"
                + ", stato=" + stato + "}";
    }
}
