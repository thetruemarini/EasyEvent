package easyevent.notifica;

import java.time.LocalDate;
/**
 * Rappresenta una notifica destinata a un fruitore.
 *
 * Invariante di classe:
 *   - id != null (l'IdNotifica garantisce valore >= 0)
 *   - idProposta >= 0
 *   - testo != null && !testo.isBlank()
 *   - dataCreazione != null
 */
public class Notifica {

    private final IdNotifica id;
    private final TipoNotifica tipo;        // COSA è successo
    private final int idProposta;
    private final String titoloProposta;    // dati grezzi, non frasi
    private final String dataEvento;        // stringa già parsata (es. "10/07/2026")
    private final String oraEvento;
    private final String luogoEvento;
    private final String quotaIndividuale;
    private final LocalDate dataCreazione;

    public Notifica(IdNotifica id, TipoNotifica tipo, int idProposta, 
                    String titoloProposta, String dataEvento, 
                    String oraEvento, String luogoEvento, 
                    String quotaIndividuale, LocalDate dataCreazione) {
        if (id == null) {
            throw new IllegalArgumentException("L'id della notifica non puo' essere null.");
        }
        if (tipo == null) {
            throw new IllegalArgumentException("Il tipo della notifica non puo' essere null.");
        }
        if (dataCreazione == null) {
            throw new IllegalArgumentException("La data di creazione non puo' essere null.");
        }
        this.id = id;
        this.tipo = tipo;
        this.idProposta = idProposta;
        this.titoloProposta = titoloProposta != null ? titoloProposta : "";
        this.dataEvento = dataEvento != null ? dataEvento : "";
        this.oraEvento = oraEvento != null ? oraEvento : "";
        this.luogoEvento = luogoEvento != null ? luogoEvento : "";
        this.quotaIndividuale = quotaIndividuale != null ? quotaIndividuale : "";
        this.dataCreazione = dataCreazione;
        assert repOk() : "Invariante violato dopo costruzione Notifica";
    }

    public IdNotifica getId() {
        return id;
    }

    public TipoNotifica getTipo() {
        return tipo;
    }

    public int getIdProposta()          { return idProposta; }
    public String getTitoloProposta()   { return titoloProposta; }
    public String getDataEvento()       { return dataEvento; }
    public String getOraEvento()        { return oraEvento; }
    public String getLuogoEvento()      { return luogoEvento; }
    public String getQuotaIndividuale() { return quotaIndividuale; }
    public LocalDate getDataCreazione() { return dataCreazione; }

    public boolean repOk() {
        return id != null
            && tipo != null
            && titoloProposta != null
            && dataEvento != null
            && oraEvento != null
            && luogoEvento != null
            && quotaIndividuale != null
            && dataCreazione != null;
    }

    /**
     * Solo per debug e logging infrastrutturale.
     * La View usa i getter e costruisce il testo per l'utente.
     */
    @Override
    public String toString() {
        return "Notifica{id=" + id
             + ", tipo=" + tipo
             + ", proposta=" + idProposta
             + ", data=" + dataCreazione + "}";
    }
}
