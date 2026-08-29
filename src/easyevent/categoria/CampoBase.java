package easyevent.categoria;

import easyevent.exception.ModificaNonConsentitaException;
import java.util.List;

/**
 * Campo BASE: immutabile per definizione. setObbligatorio lancia sempre
 * eccezione — senza bisogno di controllare il tipo.
 */
public class CampoBase extends Campo {

    /**
     * I nomi dei campi base previsti dal dominio, nell'ordine in cui vanno
     * presentati. Immutabile: e' una definizione del dominio, non una
     * configurazione modificabile a runtime.
     */
    public static final List<String> NOMI = List.of(
            "Titolo",
            "Numero di partecipanti",
            "Termine ultimo di iscrizione",
            "Luogo",
            "Data inizio",
            "Ora",
            "Quota individuale",
            "Data conclusiva"
    );

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
