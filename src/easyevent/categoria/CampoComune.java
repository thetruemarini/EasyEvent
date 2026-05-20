package easyevent.categoria;

/**
 * Campo COMUNE: condiviso tra tutte le categorie, modificabile.
 */
public class CampoComune extends Campo {

    public CampoComune(String nome, boolean obbligatorio) {
        super(nome, obbligatorio);
    }

    @Override
    public void setObbligatorio(boolean obbligatorio) {
        this.obbligatorio = obbligatorio; // consentito
    }

    @Override
    public TipoCampo getTipo() {
        return TipoCampo.COMUNE;
    }
}
