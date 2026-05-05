package easyevent.categoria;

/**
 * Campo SPECIFICO: appartiene a una singola categoria, modificabile.
 */
public class CampoSpecifico extends Campo {

    public CampoSpecifico(String nome, boolean obbligatorio) {
        super(nome, obbligatorio);
    }

    @Override
    public void setObbligatorio(boolean obbligatorio) {
        this.obbligatorio = obbligatorio; // consentito
    }

    @Override
    public TipoCampo getTipo() {
        return TipoCampo.SPECIFICO;
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
