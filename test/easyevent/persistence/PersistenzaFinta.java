package easyevent.persistence;

import easyevent.core.AppData;
import easyevent.exception.PersistenzaException;

/**
 * Doppio di prova della persistenza: non tocca il file system, tiene solo il
 * conto delle chiamate ricevute e, su richiesta, fallisce.
 *
 * Serve ai test dei Controller, che devono verificare l'orchestrazione (lo
 * stato viene salvato? il rollback riporta indietro le modifiche?) senza far
 * dipendere l'esito da un file vero. Il fallimento e' dichiarato dal test,
 * non provocato di riflesso da un percorso illegale: e' la specifica del caso,
 * non un effetto collaterale del sistema operativo.
 *
 * La persistenza vera resta sotto test in PersistenceManagerTest, dove il file
 * su disco e' il soggetto della verifica e non un dettaglio di contorno.
 */
public class PersistenzaFinta implements Persistenza {

    /** Quando e' true, ogni salvataggio e ogni caricamento falliscono. */
    private boolean guasta;

    private int salvataggi;
    private int caricamenti;

    /** Doppio funzionante: salva e carica senza mai fallire. */
    public PersistenzaFinta() {
        this(false);
    }

    /**
     * @param guasta true per un doppio che fallisce ogni operazione, come farebbe
     * un disco pieno o un file non scrivibile.
     */
    public PersistenzaFinta(boolean guasta) {
        this.guasta = guasta;
    }

    /** Doppio che fallisce ogni salvataggio e ogni caricamento. */
    public static PersistenzaFinta guasta() {
        return new PersistenzaFinta(true);
    }

    @Override
    public void salvaSicuro(AppData data) {
        if (guasta) {
            throw new PersistenzaException(
                    PersistenzaException.TipoErrore.ERRORE_SCRITTURA, "(persistenza finta)");
        }
        salvataggi++;
    }

    @Override
    public boolean caricaSicuro(AppData data) {
        if (guasta) {
            throw new PersistenzaException(
                    PersistenzaException.TipoErrore.ERRORE_LETTURA, "(persistenza finta)");
        }
        caricamenti++;
        return false;
    }

    /** Rende guasto o funzionante il doppio a meta' scenario. */
    public void setGuasta(boolean guasta) {
        this.guasta = guasta;
    }

    /** @return quanti salvataggi sono andati a buon fine. */
    public int getSalvataggi() {
        return salvataggi;
    }

    /** @return quanti caricamenti sono andati a buon fine. */
    public int getCaricamenti() {
        return caricamenti;
    }
}
