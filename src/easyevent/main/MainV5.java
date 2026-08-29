package easyevent.main;

import easyevent.controller.ConfiguratoreController;
import easyevent.controller.FruitoreController;
import easyevent.core.AppData;
import easyevent.exception.PersistenzaException;
import easyevent.persistence.PersistenceManager;
import easyevent.view.AppView;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Punto di ingresso principale dell'applicazione EasyEvent - Versione 5.
 *
 * Responsabilità del main (e solo queste): 1. Creare AppData (stato centrale)
 * 2. Creare PersistenceManager 3. Caricare i dati 4. Creare i Controller
 * (iniettando le dipendenze) 5. Creare la View principale e avviarla
 *
 * Nessuna logica di business, nessuna logica di presentazione vive qui: anche i
 * messaggi di avvio sono costruiti e stampati da AppView. Il main dipende da
 * tutti i layer per definizione: è il suo ruolo. Non appartiene a nessuna delle
 * caselle MVC — ha un package dedicato.
 */
public class MainV5 {

    private static final String DATA_FILE = "data/easyevent_data.json";
    private static final String DEFAULT_ADMIN_USER = "admin";
    private static final String DEFAULT_ADMIN_PASS = "admin123";

    public static void main(String[] args) {

        // 0. Forza l'output console in UTF-8 a prescindere dalla code page del
        //    terminale, così i simboli Unicode (✓, 📋, …) si visualizzano sempre.
        //    Configura lo stream, non ci scrive: la scrittura è della View.
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        // 1. Stato centrale — creato una sola volta, iniettato tramite costruttore
        AppData appData = new AppData();

        // 2. Persistenza
        PersistenceManager persistenceManager = new PersistenceManager(DATA_FILE);

        // 3. Caricamento dati: l'esito viene tenuto da parte e mostrato dalla
        //    View, che a questo punto non esiste ancora.
        boolean fileEsisteva = false;
        PersistenzaException erroreCaricamento = null;
        try {
            fileEsisteva = persistenceManager.caricaSicuro(appData);
        } catch (PersistenzaException e) {
            erroreCaricamento = e;
        }

        // 4. Controller (Dependency Injection)
        ConfiguratoreController confController
                = new ConfiguratoreController(appData, persistenceManager,
                        DEFAULT_ADMIN_USER, DEFAULT_ADMIN_PASS);

        FruitoreController fruitController
                = new FruitoreController(appData, persistenceManager);

        // 5. View principale — da qui in poi ogni riga stampata passa da lei
        Scanner scanner = new Scanner(System.in);
        AppView appView = new AppView(confController, fruitController, scanner);

        if (erroreCaricamento != null) {
            appView.mostraErroreAvvio("Impossibile caricare i dati", erroreCaricamento);
        } else {
            appView.mostraEsitoCaricamento(DATA_FILE, fileEsisteva,
                    persistenceManager.getProposteScartate());
        }

        try {
            confController.inizializzaCampiBase();
        } catch (PersistenzaException e) {
            appView.mostraErroreAvvio("Campi base creati ma non salvati", e);
        }

        try {
            appView.mostraTransizioni(confController.aggiornaTransizioni());
        } catch (PersistenzaException e) {
            appView.mostraErroreAvvio("Transizioni automatiche non salvate", e);
        }

        appView.mostraRiepilogoIniziale();

        appView.avvia();
        scanner.close();
    }
}
