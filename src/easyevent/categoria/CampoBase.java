package easyevent.categoria;

import easyevent.exception.ModificaNonConsentitaException;

/**
 * Campo BASE: immutabile per definizione. setObbligatorio lancia sempre
 * eccezione — senza bisogno di controllare il tipo.
 */
public class CampoBase extends Campo {

    public CampoBase(String nome) {
        super(nome, true); // i campi base sono sempre obbligatori
    }

    @Override
    public void setObbligatorio(boolean obbligatorio) {
        throw new ModificaNonConsentitaException(
                ModificaNonConsentitaException.TipoModifica.CAMPO_BASE_IMMUTABILE,
                getNome()
        );
    }

    @Override
    public TipoCampo getTipo() {
        return TipoCampo.BASE;
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
        return isInEvidenzaHelper(getNome());
    }

    static boolean isInEvidenzaHelper(String nome) {
        String[] inEvidenza = {
            "data inizio", "ora", "luogo", "quota individuale",
            "data conclusiva", "durata", "note", "compreso nella quota"
        };
        for (String s : inEvidenza) {
            if (s.equalsIgnoreCase(nome)) {
                return true;
            }
        }
        return false;
    }
}
