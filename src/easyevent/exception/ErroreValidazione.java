package easyevent.exception;

/**
 * Rappresenta un singolo errore di validazione di una Proposta.
 *
 * Porta dati strutturati (tipo + parametri grezzi). La View costruisce il
 * messaggio italiano dai dati.
 */
public class ErroreValidazione {

    public enum Tipo {
        CAMPO_OBBLIGATORIO_VUOTO, // dettaglio = nome del campo
        DATA_FORMATO_NON_VALIDO, // dettaglio = nome del campo data
        ORA_FORMATO_NON_VALIDO, // dettaglio = (null)
        TERMINE_NON_FUTURO, // dettaglio = data odierna (String)
        DATA_INIZIO_TROPPO_VICINA, // dettaglio = data minima ammessa (String)
        DATA_CONCLUSIVA_PRECEDENTE, // dettaglio = (null)
        NUM_PARTECIPANTI_NON_POSITIVO, // dettaglio = (null)
        NUM_PARTECIPANTI_NON_NUMERICO    // dettaglio = (null)
    }

    private final Tipo tipo;
    private final String nomeCampo;   // campo coinvolto, può essere null
    private final String dettaglio;   // dato grezzo aggiuntivo, può essere null

    public ErroreValidazione(Tipo tipo, String nomeCampo, String dettaglio) {
        if (tipo == null) {
            throw new IllegalArgumentException("tipo non può essere null");
        }
        this.tipo = tipo;
        this.nomeCampo = nomeCampo;
        this.dettaglio = dettaglio;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public String getNomeCampo() {
        return nomeCampo;
    }

    public String getDettaglio() {
        return dettaglio;
    }
}
