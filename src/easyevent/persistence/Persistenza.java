package easyevent.persistence;

import easyevent.core.AppData;
import easyevent.exception.PersistenzaException;

/**
 * Contratto di persistenza visto dai Controller: salvare l'intero stato
 * dell'applicazione e ricaricarlo.
 *
 * I Controller dipendono da questa astrazione e non dalla classe concreta che
 * scrive su file: la tecnologia di storage puo' cambiare, o essere sostituita
 * nei test da un doppio in memoria, senza toccarli.
 *
 * Entrambe le operazioni parlano il linguaggio del dominio: segnalano i guasti
 * con PersistenzaException, mai con eccezioni tecnologiche.
 */
public interface Persistenza {

    /**
     * Scrive sul supporto persistente l'intero stato dell'applicazione.
     *
     * @throws PersistenzaException se la scrittura non riesce.
     */
    void salvaSicuro(AppData data);

    /**
     * Ricarica dal supporto persistente l'intero stato dell'applicazione.
     *
     * @return true se lo stato e' stato letto, false se non c'era nulla da
     * leggere (primo avvio).
     * @throws PersistenzaException se la lettura non riesce.
     */
    boolean caricaSicuro(AppData data);
}
