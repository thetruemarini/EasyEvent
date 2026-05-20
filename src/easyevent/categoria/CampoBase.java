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
}
