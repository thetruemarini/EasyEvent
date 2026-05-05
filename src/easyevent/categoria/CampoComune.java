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

    @Override
    public boolean isData() {
        String lower = getNome().toLowerCase();
        return lower.contains("data") || lower.equals("termine ultimo di iscrizione");
    }

    @Override
    public boolean isOra() {
        return getNome().equalsIgnoreCase("ora");
    }

    @Override
    public boolean isInEvidenza() {
        return CampoBase.isInEvidenzaHelper(getNome());
    }
}
