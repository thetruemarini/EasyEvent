package easyevent.batch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Raccoglie il resoconto di un'importazione batch.
 *
 * Porta dati strutturati (tipo, numero riga, messaggio tecnico): la View è
 * responsabile di costruire le frasi in italiano da mostrare all'utente.
 * BatchRisultato non produce testo formattato per l'utente.
 *
 * Invariante di classe: - righeTotali >= 0 - le liste voci != null
 */
public class BatchRisultato {

    /**
     * Tipo di voce del resoconto.
     */
    public enum TipoVoce {
        SUCCESSO, WARNING, ERRORE
    }

    /**
     * Una singola voce del resoconto: porta dati strutturati grezzi. La View
     * costruisce il testo da mostrare all'utente.
     */
    public static class Voce {

        private final TipoVoce tipo;
        private final int numeroRiga;   // 0 = non applicabile (es. errore di apertura file)
        private final String messaggio; // testo tecnico/di dominio, non frase UI

        public Voce(TipoVoce tipo, int numeroRiga, String messaggio) {
            if (tipo == null) {
                throw new IllegalArgumentException("tipo non puo' essere null");
            }
            this.tipo = tipo;
            this.numeroRiga = numeroRiga;
            this.messaggio = messaggio != null ? messaggio : "";
        }

        public TipoVoce getTipo() {
            return tipo;
        }

        public int getNumeroRiga() {
            return numeroRiga;
        }

        public String getMessaggio() {
            return messaggio;
        }
    }

    private int righeTotali;
    private final List<Voce> voci;

    public BatchRisultato() {
        this.righeTotali = 0;
        this.voci = new ArrayList<>();
    }

    // ================================================================
    // AGGIUNTA RISULTATI
    // ================================================================
    public void incrementaRighe() {
        this.righeTotali++;
    }

    public void aggiungiSuccesso(String messaggio) {
        voci.add(new Voce(TipoVoce.SUCCESSO, 0, messaggio));
    }

    public void aggiungiWarning(int riga, String messaggio) {
        voci.add(new Voce(TipoVoce.WARNING, riga, messaggio));
    }

    public void aggiungiErrore(int riga, String messaggio) {
        voci.add(new Voce(TipoVoce.ERRORE, riga, messaggio));
    }

    // ================================================================
    // QUERY
    // ================================================================
    public int getRigheTotali() {
        return righeTotali;
    }

    public List<Voce> getVoci() {
        return Collections.unmodifiableList(voci);
    }

    public int getNumSuccessi() {
        return (int) voci.stream().filter(v -> v.getTipo() == TipoVoce.SUCCESSO).count();
    }

    public int getNumWarnings() {
        return (int) voci.stream().filter(v -> v.getTipo() == TipoVoce.WARNING).count();
    }

    public int getNumErrori() {
        return (int) voci.stream().filter(v -> v.getTipo() == TipoVoce.ERRORE).count();
    }

    public boolean isSenzaErrori() {
        return voci.stream().noneMatch(v -> v.getTipo() == TipoVoce.ERRORE);
    }

    public boolean isCompletamenteOk() {
        return voci.stream().noneMatch(v
                -> v.getTipo() == TipoVoce.ERRORE || v.getTipo() == TipoVoce.WARNING);
    }

    // ================================================================
    // FUSIONE (per importazioni multi-file)
    // ================================================================
    public void aggiungi(BatchRisultato altro) {
        if (altro == null) {
            throw new IllegalArgumentException("BatchRisultato da aggiungere non puo' essere null.");
        }
        this.righeTotali += altro.righeTotali;
        this.voci.addAll(altro.voci);
    }

    // ================================================================
    // toString — solo per debug/log infrastrutturale, non per la UI
    // ================================================================
    @Override
    public String toString() {
        return "BatchRisultato{righe=" + righeTotali
                + ", successi=" + getNumSuccessi()
                + ", warnings=" + getNumWarnings()
                + ", errori=" + getNumErrori() + "}";
    }
}
