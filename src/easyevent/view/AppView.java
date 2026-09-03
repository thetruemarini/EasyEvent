package easyevent.view;

import java.util.Scanner;

import easyevent.controller.ConfiguratoreController;
import easyevent.controller.FruitoreController;
import easyevent.exception.PersistenzaException;

/**
 * View principale dell'applicazione: gestisce la selezione del ruolo
 * (Configuratore o Fruitore) e delega alle rispettive View.
 *
 * Appartiene al layer di presentazione, non al punto di avvio.
 */
public class AppView {

    private final ConfiguratoreController confController;
    private final FruitoreController fruitController;
    private final Scanner scanner;

    public AppView(ConfiguratoreController confController,
            FruitoreController fruitController,
            Scanner scanner) {
        if (confController == null) {
            throw new IllegalArgumentException("confController non puo' essere null.");
        }
        if (fruitController == null) {
            throw new IllegalArgumentException("fruitController non puo' essere null.");
        }
        if (scanner == null) {
            throw new IllegalArgumentException("scanner non puo' essere null.");
        }
        this.confController = confController;
        this.fruitController = fruitController;
        this.scanner = scanner;
    }

    /**
     * Mostra l'esito del caricamento iniziale dei dati.
     *
     * @param percorsoFile il file da cui si e' tentato di caricare
     * @param fileEsisteva true se il file era presente
     * @param proposteScartate quante proposte sono state scartate perche' il
     * record era malformato
     */
    public void mostraEsitoCaricamento(String percorsoFile, boolean fileEsisteva,
            int proposteScartate) {
        if (fileEsisteva) {
            System.out.println("[Sistema] Dati caricati da: " + percorsoFile);
        } else {
            System.out.println("[Sistema] Primo avvio: nessun dato precedente trovato.");
        }
        if (proposteScartate > 0) {
            System.out.println("[Sistema] Attenzione: " + proposteScartate
                    + " proposta/e scartata/e perche' il record su disco non e' valido.");
        }
    }

    /** Mostra un errore di persistenza avvenuto durante l'avvio. */
    public void mostraErroreAvvio(String contesto, PersistenzaException e) {
        System.out.println("[Sistema] " + contesto + ": "
                + messaggioPersistenza(e.getTipoErrore()) + ".");
    }

    /** Mostra quante transizioni automatiche di stato sono state applicate. */
    public void mostraTransizioni(int quante) {
        if (quante > 0) {
            System.out.println("[Sistema] Transizioni automatiche applicate: "
                    + quante + " proposta/e aggiornata/e.");
        }
    }

    /** Mostra il riepilogo dei dati presenti all'avvio. */
    public void mostraRiepilogoIniziale() {
        System.out.println("[Sistema] Proposte aperte in bacheca: "
                + confController.getBacheca().size());
        System.out.println("[Sistema] Proposte nell'archivio:     "
                + confController.getArchivio().size());
    }

    private String messaggioPersistenza(PersistenzaException.TipoErrore tipo) {
        return switch (tipo) {
            case FILE_NON_TROVATO ->
                "file non trovato";
            case NON_E_UN_FILE ->
                "il percorso indicato non e' un file";
            case ERRORE_LETTURA ->
                "errore nella lettura del file";
            case ERRORE_SCRITTURA ->
                "errore nel salvataggio";
        };
    }

    /**
     * Avvia il loop principale di selezione ruolo.
     */
    public void avvia() {
        while (true) {
            System.out.println("\n" + ViewUtils.SEP);
            System.out.println("  EasyEvent  ");
            System.out.println(ViewUtils.SEP);
            System.out.println("  Con quale ruolo vuoi accedere?");
            System.out.println("  1. Configuratore  (back-end + importazione batch)");
            System.out.println("  2. Fruitore       (front-end)");
            System.out.println("  0. Esci");
            System.out.println();
            System.out.print("  Scelta: ");
            String scelta = scanner.nextLine().trim();

            switch (scelta) {
                case "1" -> {
                    ConfiguratoreView confView = new ConfiguratoreView(confController, scanner);
                    if (!confView.avvia()) {
                        return;
                    }
                }
                case "2" -> {
                    FruitoreView fruitView = new FruitoreView(fruitController, scanner);
                    if (!fruitView.avvia()) {
                        return;
                    }
                }
                case "0" -> {
                    System.out.println("\n  Arrivederci.");
                    return;
                }
                default ->
                    System.out.println("\n  ERRORE: Scelta non valida.");
            }
        }
    }
}
