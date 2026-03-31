package it.easyevent.v5.batch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/**
 * Raccoglie il resoconto di un'importazione batch.
 *
 * Per ogni file elaborato, BatchRisultato registra:
 * - il numero di righe totali processate (esclusi commenti e righe vuote)
 * - le operazioni andate a buon fine (successi)
 * - i warning (righe ignorate per duplicati, comandi sconosciuti, ecc.)
 * - gli errori (righe con dati non validi, proposte non pubblicabili, ecc.)
 *
 * Invariante di classe:
 * - successi, warning, errori >= 0
 * - le liste successi, warning, errori != null
 * - righeTotali == successi.size() + warning.size() + errori.size()
 *   (approssimazione: righeTotali conta le righe significative, non le somme)
 */
public class BatchRisultato {

    private int righeTotali;
    private final List<String> successi;
    private final List<String> warnings;
    private final List<String> errori;

    public BatchRisultato() {
        this.righeTotali = 0;
        this.successi = new ArrayList<>();
        this.warnings = new ArrayList<>();
        this.errori = new ArrayList<>();
    }

    // ================================================================
    // AGGIUNTA RISULTATI
    // ================================================================
    /**
     * Registra una riga come elaborata (incrementa il contatore).
     */
    public void incrementaRighe() {
        this.righeTotali++;
    }

    /**
     * Aggiunge un messaggio di successo.
     *
     * @param messaggio descrizione dell'operazione completata, non null
     */
    public void aggiungiSuccesso(String messaggio) {
        if (messaggio == null) {
            messaggio = "(operazione completata)";
        }
        successi.add(messaggio);
    }

    /**
     * Aggiunge un warning (operazione saltata con motivazione).
     *
     * @param riga numero di riga nel file (1-based), per agevolare il debug
     * @param messaggio descrizione del warning, non null
     */
    public void aggiungiWarning(int riga, String messaggio) {
        if (messaggio == null) {
            messaggio = "(warning senza descrizione)";
        }
        warnings.add("  Riga " + riga + ": " + messaggio);
    }

    /**
     * Aggiunge un messaggio di errore.
     *
     * @param riga numero di riga nel file (1-based)
     * @param messaggio descrizione dell'errore, non null
     */
    public void aggiungiErrore(int riga, String messaggio) {
        if (messaggio == null) {
            messaggio = "(errore senza descrizione)";
        }
        errori.add("  Riga " + riga + ": " + messaggio);
    }

    // ================================================================
    // GETTERS
    // ================================================================
    public int getRigheTotali() {
        return righeTotali;
    }

    public int getNumSuccessi() {
        return successi.size();
    }

    public int getNumWarnings() {
        return warnings.size();
    }

    public int getNumErrori() {
        return errori.size();
    }

    public List<String> getSuccessi() {
        return Collections.unmodifiableList(successi);
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public List<String> getErrori() {
        return Collections.unmodifiableList(errori);
    }

    /**
     * @return true se non ci sono errori (warning ammessi)
     */
    public boolean isSenzaErrori() {
        return errori.isEmpty();
    }

    /**
     * @return true se non ci sono né errori né warning
     */
    public boolean isCompletamenteOk() {
        return errori.isEmpty() && warnings.isEmpty();
    }

    // ================================================================
    // FUSIONE (per importazioni multi-file)
    // ================================================================
    /**
     * Aggiunge i risultati di un altro BatchRisultato a questo. Usato quando si
     * importano più file in sequenza.
     *
     * @param altro il BatchRisultato da aggiungere, non null
     * @throws IllegalArgumentException se altro è null
     */
    public void aggiungi(BatchRisultato altro) {
        if (altro == null) {
            throw new IllegalArgumentException("BatchRisultato da aggiungere non puo' essere null.");
        }
        this.righeTotali += altro.righeTotali;
        this.successi.addAll(altro.successi);
        this.warnings.addAll(altro.warnings);
        this.errori.addAll(altro.errori);
    }

    // ================================================================
    // RAPPRESENTAZIONE TESTUALE
    // ================================================================
    /**
     * Genera un resoconto formattato per la visualizzazione in console.
     *
     * @return stringa multi-riga con il resoconto completo
     */
    public String toResocontoTestuale() {
        StringBuilder sb = new StringBuilder();
        sb.append("  Righe elaborate: ").append(righeTotali).append("\n");
        sb.append("  Successi:        ").append(successi.size()).append("\n");
        sb.append("  Warning:         ").append(warnings.size()).append("\n");
        sb.append("  Errori:          ").append(errori.size()).append("\n");

        if (!successi.isEmpty()) {
            sb.append("\n  --- OPERAZIONI COMPLETATE ---\n");
            successi.forEach(s -> sb.append("  ✓ ").append(s).append("\n"));
        }

        if (!warnings.isEmpty()) {
            sb.append("\n  --- WARNING (operazioni saltate) ---\n");
            warnings.forEach(w -> sb.append("  ⚠ ").append(w).append("\n"));
        }

        if (!errori.isEmpty()) {
            sb.append("\n  --- ERRORI ---\n");
            errori.forEach(e -> sb.append("  ✗ ").append(e).append("\n"));
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return "BatchRisultato{righe=" + righeTotali
                + ", successi=" + successi.size()
                + ", warnings=" + warnings.size()
                + ", errori=" + errori.size() + "}";
    }
}
