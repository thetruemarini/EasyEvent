package easyevent.exception;

import java.time.LocalDate;

/**
 * Rappresenta un singolo errore di validazione di una Proposta.
 *
 * Porta dati strutturati (tipo + parametri grezzi). La View costruisce il
 * messaggio italiano dai dati, e decide con quale formato mostrare le date.
 */
public class ErroreValidazione {

    public enum Tipo {
        CAMPO_OBBLIGATORIO_VUOTO,        // data = (null)
        DATA_FORMATO_NON_VALIDO,         // data = (null)
        ORA_FORMATO_NON_VALIDO,          // data = (null)
        TERMINE_NON_FUTURO,              // data = data odierna
        DATA_INIZIO_TROPPO_VICINA,       // data = data minima ammessa
        DATA_CONCLUSIVA_PRECEDENTE,      // data = (null)
        NUM_PARTECIPANTI_NON_POSITIVO,   // data = (null)
        NUM_PARTECIPANTI_NON_NUMERICO    // data = (null)
    }

    private final Tipo tipo;
    private final String nomeCampo;   // campo coinvolto, puo' essere null
    private final LocalDate data;     // data grezza pertinente all'errore, null se non applicabile

    public ErroreValidazione(Tipo tipo, String nomeCampo) {
        this(tipo, nomeCampo, null);
    }

    public ErroreValidazione(Tipo tipo, String nomeCampo, LocalDate data) {
        if (tipo == null) {
            throw new IllegalArgumentException("tipo non puo' essere null");
        }
        this.tipo = tipo;
        this.nomeCampo = nomeCampo;
        this.data = data;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public String getNomeCampo() {
        return nomeCampo;
    }

    /**
     * @return la data grezza collegata all'errore, o null se il tipo non ne
     * prevede una. Non e' formattata: il formato lo sceglie la View.
     */
    public LocalDate getData() {
        return data;
    }
}
