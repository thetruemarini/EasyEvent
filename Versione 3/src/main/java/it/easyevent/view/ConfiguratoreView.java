package it.easyevent.view;

import it.easyevent.controller.ConfiguratoreController;
import it.easyevent.model.Campo;
import it.easyevent.model.Categoria;
import it.easyevent.model.Proposta;
import it.easyevent.model.StatoProposta;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * Interfaccia testuale (CLI) per il configuratore - Versione 3.
 *
 * Novita' rispetto alla V2: - Menu "Visualizza archivio proposte": mostra tutte
 * le proposte pubblicate con i loro stati (APERTA, CONFERMATA, ANNULLATA,
 * CONCLUSA) e storico. - Informazione sui passaggi di stato applicati
 * all'avvio.
 *
 * Invariante di classe: - controller != null - scanner != null
 */
public class ConfiguratoreView {

    private static final String SEP = "------------------------------------------------------------";
    private static final String SEP2 = "  ----------------------------------------------------------";

    private final ConfiguratoreController controller;
    private final Scanner scanner;

    public ConfiguratoreView(ConfiguratoreController controller) {
        this(controller, new Scanner(System.in));
    }

    /**
     * Costruttore che accetta uno Scanner condiviso (usato da MainV3 per
     * evitare conflitti con FruitoreView sullo stesso System.in).
     */
    public ConfiguratoreView(ConfiguratoreController controller, Scanner scanner) {
        if (controller == null) {
            throw new IllegalArgumentException("Controller non puo' essere null.");
        }
        if (scanner == null) {
            throw new IllegalArgumentException("Scanner non puo' essere null.");
        }
        this.controller = controller;
        this.scanner = scanner;
    }

    // ================================================================
    // ENTRY POINT
    // ================================================================
    public void avvia() {
        stampaBanner();
        // inizializzaCampiBase() e' gia' invocata da MainV3 prima di creare questa view;
        // non va ripetuta qui per evitare un doppio salvataggio al primo avvio.

        while (true) {
            if (!controller.isLoggato()) {
                if (!gestioneLogin()) {
                    System.out.println("\n  Arrivederci.");
                    break;
                }
                if (controller.richiedeCambioCredenziali()) {
                    System.out.println("\n*** Primo accesso: necessario impostare le credenziali personali. ***");
                    if (!gestionePrimoAccesso()) {
                        System.out.println("  Operazione annullata. Logout in corso.");
                        controller.logout();
                        continue;
                    }
                }
            }
            boolean continua = menuPrincipale();
            if (!continua) {
                int nScartate = controller.getProposteSessione().size();
                controller.logout();
                System.out.println("\n  Logout effettuato.");
                if (nScartate > 0) {
                    System.out.println("  NOTA: " + nScartate + " proposta/e non pubblicata/e sono state scartate.");
                }
                System.out.print("\n  Continuare con un altro account? (s/n): ");
                if (!scanner.nextLine().trim().equalsIgnoreCase("s")) {
                    System.out.println("\n  Arrivederci.");
                    break;
                }
            }
        }
        // Non chiudiamo lo scanner qui se e' condiviso con altri componenti
        // (MainV3 gestisce la chiusura dello scanner globale)
    }

    // ================================================================
    // LOGIN
    // ================================================================
    private boolean gestioneLogin() {
        System.out.println("\n" + SEP);
        System.out.println("  LOGIN CONFIGURATORE");
        System.out.println(SEP);
        System.out.println("  (digita 'esci' per uscire dall'applicazione)");
        System.out.println();
        for (int t = 0; t < 3; t++) {
            System.out.print("  Username: ");
            String username = scanner.nextLine().trim();
            if (username.equalsIgnoreCase("esci")) {
                return false;
            }
            System.out.print("  Password: ");
            String password = scanner.nextLine().trim();
            if (controller.login(username, password)) {
                System.out.println("\n  Benvenuto, " + username + "!");
                return true;
            }
            int rimasti = 2 - t;
            stampaErrore("Credenziali non valide."
                    + (rimasti > 0 ? " Tentativi rimasti: " + rimasti : ""));
            if (rimasti > 0) {
                System.out.println();
            }
        }
        System.out.println("\n  Accesso bloccato: numero massimo di tentativi raggiunto.");
        return false;
    }

    private boolean gestionePrimoAccesso() {
        System.out.println("\n" + SEP);
        System.out.println("  IMPOSTAZIONE CREDENZIALI PERSONALI");
        System.out.println(SEP);
        System.out.println("  (digita 'annulla' per interrompere e tornare al login)");
        System.out.println();
        System.out.print("  Nuovo username: ");
        String nu = scanner.nextLine().trim();
        if (nu.equalsIgnoreCase("annulla")) {
            return false;
        }
        // "esci" e' la parola riservata per uscire dalla schermata di login:
        // se fosse accettata come username, il configuratore non potrebbe mai
        // più accedere perché il login interpreterebbe "esci" come comando di uscita.
        if (nu.equalsIgnoreCase("esci")) {
            stampaErrore("Lo username 'esci' e' riservato e non puo' essere usato.");
            return false;
        }
        System.out.print("  Nuova password: ");
        String np = scanner.nextLine().trim();
        if (np.equalsIgnoreCase("annulla")) {
            return false;
        }
        System.out.print("  Conferma password: ");
        String conf = scanner.nextLine().trim();
        if (!np.equals(conf)) {
            stampaErrore("Le password non coincidono.");
            return false;
        }
        String err = controller.impostaCredenzialiPersonali(nu, np);
        if (!err.isEmpty()) {
            stampaErrore(err);
            return false;
        }
        System.out.println("\n  Credenziali impostate correttamente.");
        return true;
    }

    // ================================================================
    // MENU PRINCIPALE
    // ================================================================
    private boolean menuPrincipale() {
        while (true) {
            int nSessione = controller.getProposteSessione().size();
            int nBacheca = controller.getBacheca().size();
            System.out.println("\n" + SEP);
            System.out.println("  MENU PRINCIPALE  [" + controller.getConfiguratoreCorrente().getUsername() + "]");
            System.out.println(SEP);
            System.out.println("  1. Gestione campi base");
            System.out.println("  2. Gestione campi comuni");
            System.out.println("  3. Gestione categorie");
            System.out.println("  4. Visualizza riepilogo categorie e campi");
            System.out.println("  5. Gestione proposte          [" + nSessione + " in sessione]");
            System.out.println("  6. Visualizza bacheca         [" + nBacheca + " proposte aperte]");
            System.out.println("  7. Visualizza archivio proposte");
            System.out.println("  0. Logout");
            System.out.println();
            System.out.print("  Scelta: ");
            switch (scanner.nextLine().trim()) {
                case "1" ->
                    menuCampiBase();
                case "2" ->
                    menuCampiComuni();
                case "3" ->
                    menuCategorie();
                case "4" ->
                    visualizzaRiepilogo();
                case "5" ->
                    menuProposte();
                case "6" ->
                    visualizzaBacheca();
                case "7" ->
                    visualizzaArchivio();
                case "0" -> {
                    return false;
                }
                default ->
                    stampaErrore("Scelta non valida.");
            }
        }
    }

    // ================================================================
    // CAMPI BASE  (invariata da V2)
    // ================================================================
    private void menuCampiBase() {
        System.out.println("\n" + SEP);
        System.out.println("  CAMPI BASE  (immutabili)");
        System.out.println(SEP);
        List<Campo> cb = controller.getCampiBase();
        if (cb.isEmpty()) {
            System.out.println("  (nessun campo base definito)");
        } else {
            for (int i = 0; i < cb.size(); i++) {
                System.out.printf("  %d. %s%n", i + 1, cb.get(i));
            }
        }
        System.out.println();
        System.out.println("  I campi base sono fissati al primo avvio e non modificabili.");
        premInvio();
    }

    // ================================================================
    // CAMPI COMUNI  (invariata da V2)
    // ================================================================
    private void menuCampiComuni() {
        while (true) {
            System.out.println("\n" + SEP);
            System.out.println("  GESTIONE CAMPI COMUNI");
            System.out.println(SEP);
            List<Campo> cc = controller.getCampiComuni();
            if (cc.isEmpty()) {
                System.out.println("  (nessun campo comune definito)");
            } else {
                for (int i = 0; i < cc.size(); i++) {
                    System.out.printf("  %d. %s%n", i + 1, cc.get(i));
                }
            }
            System.out.println();
            System.out.println("  a. Aggiungi campo comune");
            System.out.println("  r. Rimuovi campo comune");
            System.out.println("  m. Modifica obbligatorieta' campo comune");
            System.out.println("  0. Torna al menu principale");
            System.out.println();
            System.out.print("  Scelta: ");
            switch (scanner.nextLine().trim()) {
                case "a" ->
                    aggiungiCampoComune();
                case "r" ->
                    rimuoviCampoComune();
                case "m" ->
                    modificaObbligCampoComune();
                case "0" -> {
                    return;
                }
                default ->
                    stampaErrore("Scelta non valida.");
            }
        }
    }

    private void aggiungiCampoComune() {
        System.out.print("  Nome del nuovo campo comune: ");
        String nome = scanner.nextLine().trim();
        if (nome.isEmpty()) {
            stampaErrore("Il nome non puo' essere vuoto.");
            return;
        }
        String err = controller.aggiungiCampoComune(nome, chiediObbligatorio());
        if (err.isEmpty()) {
            System.out.println("  Campo comune '" + nome + "' aggiunto."); 
        }else {
            stampaErrore(err);
        }
    }

    private void rimuoviCampoComune() {
        System.out.print("  Nome del campo comune da rimuovere: ");
        String nome = scanner.nextLine().trim();
        String err = controller.rimuoviCampoComune(nome);
        if (err.isEmpty()) {
            System.out.println("  Campo comune '" + nome + "' rimosso."); 
        }else {
            stampaErrore(err);
        }
    }

    private void modificaObbligCampoComune() {
        System.out.print("  Nome del campo comune da modificare: ");
        String nome = scanner.nextLine().trim();
        String err = controller.modificaObbligatorietaCampoComune(nome, chiediObbligatorio());
        if (err.isEmpty()) {
            System.out.println("  Obbligatorieta' di '" + nome + "' aggiornata."); 
        }else {
            stampaErrore(err);
        }
    }

    // ================================================================
    // CATEGORIE  (invariata da V2)
    // ================================================================
    private void menuCategorie() {
        while (true) {
            System.out.println("\n" + SEP);
            System.out.println("  GESTIONE CATEGORIE");
            System.out.println(SEP);
            List<Categoria> cats = controller.getCategorie();
            if (cats.isEmpty()) {
                System.out.println("  (nessuna categoria definita)");
            } else {
                for (int i = 0; i < cats.size(); i++) {
                    System.out.printf("  %d. %s%n", i + 1, cats.get(i).getNome());
                }
            }
            System.out.println();
            System.out.println("  a. Aggiungi categoria");
            System.out.println("  r. Rimuovi categoria");
            System.out.println("  c. Gestisci campi specifici");
            System.out.println("  v. Visualizza dettaglio categoria");
            System.out.println("  0. Torna al menu principale");
            System.out.println();
            System.out.print("  Scelta: ");
            switch (scanner.nextLine().trim()) {
                case "a" ->
                    aggiungiCategoria();
                case "r" ->
                    rimuoviCategoria();
                case "c" ->
                    menuCampiSpecifici();
                case "v" ->
                    visualizzaDettaglioCategoria();
                case "0" -> {
                    return;
                }
                default ->
                    stampaErrore("Scelta non valida.");
            }
        }
    }

    private void aggiungiCategoria() {
        System.out.print("  Nome della nuova categoria: ");
        String nome = scanner.nextLine().trim();
        if (nome.isEmpty()) {
            stampaErrore("Il nome non puo' essere vuoto.");
            return;
        }
        String err = controller.aggiungiCategoria(nome);
        if (err.isEmpty()) {
            System.out.println("  Categoria '" + nome + "' aggiunta.");
            System.out.print("  Aggiungere subito campi specifici? (s/n): ");
            if (scanner.nextLine().trim().equalsIgnoreCase("s")) {
                aggiungiCampiSpecificiInterattivo(nome);
            }
        } else {
            stampaErrore(err);
        }
    }

    private void aggiungiCampiSpecificiInterattivo(String nomeCategoria) {
        System.out.println("  (premi INVIO senza testo per terminare)");
        while (true) {
            System.out.print("  Nome campo specifico: ");
            String nome = scanner.nextLine().trim();
            if (nome.isEmpty()) {
                stampaErrore("Il nome non può essere vuoto.");
                continue;
            }
            String err = controller.aggiungiCampoSpecifico(nomeCategoria, nome, chiediObbligatorio());
            if (err.isEmpty()) {
                System.out.println("  Campo '" + nome + "' aggiunto."); 
            }else {
                stampaErrore(err);
            }

            System.out.print("  Aggiungere un altro campo specifico? (s/n): ");
            if (!scanner.nextLine().trim().equalsIgnoreCase("s")) {
                break;
            }
        }
    }

    private void rimuoviCategoria() {
        System.out.print("  Nome della categoria da rimuovere: ");
        String nome = scanner.nextLine().trim();
        if (nome.isEmpty()) {
            stampaErrore("Il nome non puo' essere vuoto.");
            return;
        }
        System.out.print("  Confermi la rimozione di '" + nome
                + "'? L'operazione e' irreversibile. (s/n): ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("s")) {
            System.out.println("  Operazione annullata.");
            return;
        }
        String err = controller.rimuoviCategoria(nome);
        if (err.isEmpty()) {
            System.out.println("  Categoria '" + nome + "' rimossa (compresi tutti i campi specifici)."); 
        }else {
            stampaErrore(err);
        }
    }

    private void menuCampiSpecifici() {
        if (controller.getCategorie().isEmpty()) {
            stampaErrore("Nessuna categoria disponibile.");
            return;
        }
        System.out.print("  Nome della categoria: ");
        String nomeCategoria = scanner.nextLine().trim();
        Categoria cat = controller.getCategoria(nomeCategoria);
        if (cat == null) {
            stampaErrore("Categoria non trovata: " + nomeCategoria);
            return;
        }

        while (true) {
            System.out.println("\n" + SEP);
            System.out.println("  CAMPI SPECIFICI di '" + cat.getNome() + "'");
            System.out.println(SEP);
            if (cat.getCampiSpecifici().isEmpty()) {
                System.out.println("  (nessun campo specifico)");
            } else {
                for (int i = 0; i < cat.getCampiSpecifici().size(); i++) {
                    System.out.printf("  %d. %s%n", i + 1, cat.getCampiSpecifici().get(i));
                }
            }
            System.out.println();
            System.out.println("  a. Aggiungi campo specifico");
            System.out.println("  r. Rimuovi campo specifico");
            System.out.println("  m. Modifica obbligatorieta' campo specifico");
            System.out.println("  0. Torna");
            System.out.println();
            System.out.print("  Scelta: ");
            switch (scanner.nextLine().trim()) {
                case "a" -> {
                    System.out.print("  Nome del nuovo campo specifico: ");
                    String n = scanner.nextLine().trim();
                    if (n.isEmpty()) {
                        stampaErrore("Nome vuoto.");
                        break;
                    }
                    String err = controller.aggiungiCampoSpecifico(nomeCategoria, n, chiediObbligatorio());
                    if (err.isEmpty()) {
                        System.out.println("  Campo '" + n + "' aggiunto."); 
                    }else {
                        stampaErrore(err);
                    }
                }
                case "r" -> {
                    System.out.print("  Nome del campo da rimuovere: ");
                    String n = scanner.nextLine().trim();
                    String err = controller.rimuoviCampoSpecifico(nomeCategoria, n);
                    if (err.isEmpty()) {
                        System.out.println("  Campo '" + n + "' rimosso."); 
                    }else {
                        stampaErrore(err);
                    }
                }
                case "m" -> {
                    System.out.print("  Nome del campo da modificare: ");
                    String n = scanner.nextLine().trim();
                    String err = controller.modificaObbligatorietaCampoSpecifico(
                            nomeCategoria, n, chiediObbligatorio());
                    if (err.isEmpty()) {
                        System.out.println("  Obbligatorieta' aggiornata."); 
                    }else {
                        stampaErrore(err);
                    }
                }
                case "0" -> {
                    return;
                }
                default ->
                    stampaErrore("Scelta non valida.");
            }
        }
    }

    private void visualizzaDettaglioCategoria() {
        System.out.print("  Nome della categoria: ");
        String nome = scanner.nextLine().trim();
        Categoria cat = controller.getCategoria(nome);
        if (cat == null) {
            stampaErrore("Categoria non trovata: " + nome);
            return;
        }

        System.out.println("\n" + SEP);
        System.out.println("  DETTAGLIO CATEGORIA: " + cat.getNome().toUpperCase());
        System.out.println(SEP);
        System.out.println("\n  Campi BASE  (obbligatori, condivisi da tutte le categorie):");
        controller.getCampiBase().forEach(c
                -> System.out.println("    - " + c.getNome() + "  [obbligatorio]"));

        System.out.println("\n  Campi COMUNI  (condivisi da tutte le categorie):");
        if (controller.getCampiComuni().isEmpty()) {
            System.out.println("    (nessuno definito)");
        } else {
            controller.getCampiComuni().forEach(c
                    -> System.out.println("    - " + c.getNome()
                            + "  [" + (c.isObbligatorio() ? "obbligatorio" : "facoltativo") + "]"));
        }

        System.out.println("\n  Campi SPECIFICI di '" + cat.getNome() + "':");
        if (cat.getCampiSpecifici().isEmpty()) {
            System.out.println("    (nessuno)");
        } else {
            cat.getCampiSpecifici().forEach(c
                    -> System.out.println("    - " + c.getNome()
                            + "  [" + (c.isObbligatorio() ? "obbligatorio" : "facoltativo") + "]"));
        }
        premInvio();
    }

    // ================================================================
    // RIEPILOGO GENERALE  (invariata da V2)
    // ================================================================
    private void visualizzaRiepilogo() {
        System.out.println("\n" + SEP);
        System.out.println("  RIEPILOGO CATEGORIE E CAMPI");
        System.out.println(SEP);

        System.out.println("\n  CAMPI BASE  (obbligatori - condivisi da tutte le categorie)");
        List<Campo> cb = controller.getCampiBase();
        if (cb.isEmpty()) {
            System.out.println("    (non ancora inizializzati)"); 
        }else {
            cb.forEach(c -> System.out.println("    - " + c.getNome()));
        }

        System.out.println("\n  CAMPI COMUNI  (condivisi da tutte le categorie)");
        List<Campo> cc = controller.getCampiComuni();
        if (cc.isEmpty()) {
            System.out.println("    (nessuno definito)"); 
        }else {
            cc.forEach(c -> System.out.println("    - " + c.getNome()
                    + "  [" + (c.isObbligatorio() ? "obbligatorio" : "facoltativo") + "]"));
        }

        System.out.println("\n  CATEGORIE");
        List<Categoria> cats = controller.getCategorie();
        if (cats.isEmpty()) {
            System.out.println("    (nessuna categoria definita)");
        } else {
            for (Categoria cat : cats) {
                System.out.println("    > " + cat.getNome());
                if (cat.getCampiSpecifici().isEmpty()) {
                    System.out.println("      (nessun campo specifico)");
                } else {
                    cat.getCampiSpecifici().forEach(c
                            -> System.out.println("      - " + c.getNome()
                                    + "  [" + (c.isObbligatorio() ? "obbligatorio" : "facoltativo") + "]"));
                }
            }
        }
        premInvio();
    }

    // ================================================================
    // GESTIONE PROPOSTE  (invariata da V2)
    // ================================================================
    private void menuProposte() {
        while (true) {
            List<Proposta> sessione = controller.getProposteSessione();
            System.out.println("\n" + SEP);
            System.out.println("  GESTIONE PROPOSTE  (sessione corrente)");
            System.out.println(SEP);
            if (sessione.isEmpty()) {
                System.out.println("  Nessuna proposta in sessione.");
            } else {
                System.out.println("  Proposte in sessione:");
                for (Proposta p : sessione) {
                    String titolo = p.getValore("Titolo");
                    String display = titolo.isBlank() ? "(senza titolo)" : titolo;
                    System.out.println("    [ID " + p.getId() + "]"
                            + "  Categoria: " + p.getNomeCategoria()
                            + "  Titolo: \"" + display + "\""
                            + "  Stato: " + p.getStato());
                }
            }
            System.out.println();
            System.out.println("  n. Nuova proposta");
            System.out.println("  c. Continua compilazione proposta esistente");
            System.out.println("  p. Pubblica proposta in bacheca");
            System.out.println("  e. Elimina proposta dalla sessione");
            System.out.println("  0. Torna al menu principale");
            System.out.println();
            System.out.print("  Scelta: ");
            switch (scanner.nextLine().trim()) {
                case "n" ->
                    nuovaProposta();
                case "c" ->
                    continuaCompilazione();
                case "p" ->
                    pubblicaPropostaInterattivo();
                case "e" ->
                    eliminaPropostaSessione();
                case "0" -> {
                    return;
                }
                default ->
                    stampaErrore("Scelta non valida.");
            }
        }
    }

    private void nuovaProposta() {
        List<Categoria> cats = controller.getCategorie();
        if (cats.isEmpty()) {
            stampaErrore("Nessuna categoria disponibile."
                    + " Aggiungere almeno una categoria prima di creare proposte.");
            return;
        }
        System.out.println("\n  Categorie disponibili:");
        cats.forEach(c -> System.out.println("    - " + c.getNome()));
        System.out.print("\n  Categoria della proposta: ");
        String nomeCategoria = scanner.nextLine().trim();

        Proposta p = controller.creaProposta(nomeCategoria);
        if (p == null) {
            stampaErrore("Categoria non trovata: " + nomeCategoria);
            return;
        }
        System.out.println("\n  Proposta [ID " + p.getId() + "] per categoria '"
                + nomeCategoria + "' creata.");
        System.out.println("  Legenda:  [OBB] = obbligatorio   [FAC] = facoltativo");
        System.out.println("  (premi INVIO senza testo per saltare un campo facoltativo)\n");

        compilaCampiProposta(p);
        mostraRiepilogoProposta(p);
        offriPubblicazione(p);
    }

    private void continuaCompilazione() {
        if (controller.getProposteSessione().isEmpty()) {
            stampaErrore("Nessuna proposta in sessione.");
            return;
        }
        Proposta p = selezionaPropostaSessione("da continuare a compilare", false);
        if (p == null) {
            return;
        }
        System.out.println("\n  Compilazione proposta [ID " + p.getId() + "]");
        System.out.println("  (premi INVIO senza testo per mantenere il valore attuale)\n");
        compilaCampiProposta(p);
        mostraRiepilogoProposta(p);
        offriPubblicazione(p);
    }

    private void compilaCampiProposta(Proposta p) {
        Map<String, Boolean> snapshot = p.getCampiSnapshot();
        for (Map.Entry<String, Boolean> entry : snapshot.entrySet()) {
            String nome = entry.getKey();
            boolean obblig = entry.getValue();
            String valoreAtt = p.getValore(nome);
            String etichetta = obblig ? "[OBB]" : "[FAC]";
            boolean isData = nome.contains("Data")
                    || nome.equals(Proposta.CAMPO_TERMINE_ISCRIZIONE);

            System.out.print("  " + etichetta + " " + nome);
            if (isData) {
                System.out.print("  (gg/mm/aaaa)");
            }
            if (!valoreAtt.isBlank()) {
                System.out.print("  [attuale: " + valoreAtt + "]");
            }
            System.out.print(": ");

            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) {
                String err = controller.setValoreCampo(p, nome, input);
                if (!err.isEmpty()) {
                    stampaErrore(err);
                }
            }
        }
    }

    private void mostraRiepilogoProposta(Proposta p) {
        System.out.println("\n" + SEP2);
        System.out.println("  Riepilogo proposta [ID " + p.getId() + "]");
        System.out.println(SEP2);
        System.out.println("  Categoria : " + p.getNomeCategoria());
        System.out.println("  Creatore  : " + p.getUsernameCreatore());
        System.out.println("  Stato     : " + p.getStato());
        System.out.println();
        for (Map.Entry<String, Boolean> entry : p.getCampiSnapshot().entrySet()) {
            String nome = entry.getKey();
            boolean ob = entry.getValue();
            String val = p.getValore(nome);
            String disp = val.isBlank() ? "(non compilato)" : val;
            System.out.println("  " + (ob ? "[OBB]" : "[FAC]") + " " + nome + ": " + disp);
        }
        if (p.getStato() == StatoProposta.BOZZA) {
            System.out.println("\n  Problemi che impediscono la pubblicazione:");
            p.validazioneErrori(LocalDate.now())
                    .forEach(e -> System.out.println("    * " + e));
        }
        System.out.println(SEP2);
    }

    private void offriPubblicazione(Proposta p) {
        if (p.getStato() == StatoProposta.APERTA) {
            return;
        }
        if (p.getStato() == StatoProposta.VALIDA) {
            System.out.print("\n  La proposta e' VALIDA. Pubblicarla in bacheca ora? (s/n): ");
            if (scanner.nextLine().trim().equalsIgnoreCase("s")) {
                String err = controller.pubblicaProposta(p);
                if (err.isEmpty()) {
                    System.out.println("  Proposta [ID " + p.getId() + "] pubblicata in bacheca."); 
                }else {
                    stampaErrore(err);
                }
            } else {
                System.out.println("  Proposta conservata in sessione. Pubblicabile in seguito.");
            }
        } else {
            System.out.println("\n  Proposta salvata come BOZZA."
                    + " Completare i campi mancanti prima di pubblicare.");
        }
    }

    private void pubblicaPropostaInterattivo() {
        List<Proposta> valide = controller.getProposteSessione().stream()
                .filter(p -> p.getStato() == StatoProposta.VALIDA)
                .collect(Collectors.toList());
        if (valide.isEmpty()) {
            stampaErrore("Nessuna proposta VALIDA in sessione.");
            return;
        }
        System.out.println("\n  Proposte VALIDE disponibili:");
        for (Proposta p : valide) {
            String titolo = p.getValore("Titolo");
            System.out.println("    [ID " + p.getId() + "]"
                    + "  Categoria: " + p.getNomeCategoria()
                    + "  Titolo: \"" + (titolo.isBlank() ? "(senza titolo)" : titolo) + "\"");
        }
        System.out.print("\n  ID della proposta da pubblicare: ");
        String input = scanner.nextLine().trim();
        Proposta scelta = null;
        try {
            int id = Integer.parseInt(input);
            scelta = valide.stream().filter(p -> p.getId() == id).findFirst().orElse(null);
        } catch (NumberFormatException e) {
            stampaErrore("ID non valido: " + input);
            return;
        }
        if (scelta == null) {
            stampaErrore("Nessuna proposta VALIDA con ID " + input + ".");
            return;
        }
        String err = controller.pubblicaProposta(scelta);
        if (err.isEmpty()) {
            System.out.println("  Proposta [ID " + scelta.getId() + "] pubblicata in bacheca."); 
        }else {
            stampaErrore(err);
        }
    }

    private void eliminaPropostaSessione() {
        if (controller.getProposteSessione().isEmpty()) {
            stampaErrore("Nessuna proposta in sessione.");
            return;
        }
        Proposta p = selezionaPropostaSessione("da eliminare", false);
        if (p == null) {
            return;
        }
        System.out.print("  Confermi l'eliminazione della proposta [ID " + p.getId() + "]? (s/n): ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("s")) {
            System.out.println("  Operazione annullata.");
            return;
        }
        controller.eliminaPropostaSessione(p);
        System.out.println("  Proposta [ID " + p.getId() + "] eliminata dalla sessione.");
    }

    private Proposta selezionaPropostaSessione(String azione, boolean soloValide) {
        List<Proposta> pool = soloValide
                ? controller.getProposteSessione().stream()
                        .filter(p -> p.getStato() == StatoProposta.VALIDA)
                        .collect(Collectors.toList())
                : controller.getProposteSessione();
        if (pool.isEmpty()) {
            stampaErrore(soloValide ? "Nessuna proposta VALIDA in sessione." : "Nessuna proposta in sessione.");
            return null;
        }
        System.out.print("  ID della proposta " + azione + ": ");
        String input = scanner.nextLine().trim();
        try {
            int id = Integer.parseInt(input);
            Proposta trovata = pool.stream().filter(p -> p.getId() == id).findFirst().orElse(null);
            if (trovata == null) {
                stampaErrore("Nessuna proposta con ID " + id + ".");
            }
            return trovata;
        } catch (NumberFormatException e) {
            stampaErrore("ID non valido: " + input);
            return null;
        }
    }

    // ================================================================
    // BACHECA  (invariata da V2)
    // ================================================================
    private void visualizzaBacheca() {
        System.out.println("\n" + SEP);
        System.out.println("  BACHECA  -  PROPOSTE APERTE");
        System.out.println(SEP);

        List<Proposta> tutte = controller.getBacheca();
        if (tutte.isEmpty()) {
            System.out.println("\n  (Nessuna proposta aperta in bacheca)");
        } else {
            for (Categoria cat : controller.getCategorie()) {
                List<Proposta> perCat = controller.getBachecaPerCategoria(cat.getNome());
                if (perCat.isEmpty()) {
                    continue;
                }
                System.out.println("\n  CATEGORIA: " + cat.getNome().toUpperCase()
                        + "  (" + perCat.size() + " proposta/e)");
                System.out.println(SEP2);
                perCat.forEach(this::stampaBloccoPropostaBacheca);
            }

            List<Proposta> orfane = tutte.stream()
                    .filter(p -> controller.getCategorie().stream()
                    .noneMatch(c -> c.getNome().equalsIgnoreCase(p.getNomeCategoria())))
                    .collect(Collectors.toList());
            if (!orfane.isEmpty()) {
                System.out.println("\n  CATEGORIA: (categoria rimossa)  (" + orfane.size() + " proposta/e)");
                System.out.println(SEP2);
                orfane.forEach(this::stampaBloccoPropostaBacheca);
            }

            System.out.println("\n  Totale proposte aperte: " + tutte.size());
        }
        premInvio();
    }

    private void stampaBloccoPropostaBacheca(Proposta p) {
        String dataPub = p.getDataPubblicazione() != null
                ? p.getDataPubblicazione().format(Proposta.DATE_FORMAT) : "-";
        int numMax = p.getNumeroMaxPartecipanti();
        String maxStr = numMax < 0 ? "N/D" : String.valueOf(numMax);
        System.out.println("\n  [ID " + p.getId() + "]"
                + "  Pubblicata: " + dataPub
                + "  Creatore: " + p.getUsernameCreatore()
                + "  Iscritti: " + p.getAderenti().size()
                + "/" + maxStr);
        p.getValori().forEach((nome, val) -> {
            if (!val.isBlank()) {
                System.out.println("    " + nome + ": " + val);
            }
        });
    }

    // ================================================================
    // ARCHIVIO PROPOSTE  (nuovo in V3)
    // ================================================================
    /**
     * Mostra tutte le proposte pubblicate (tutti gli stati) con storico.
     * Organizzate per stato, poi per categoria.
     */
    private void visualizzaArchivio() {
        System.out.println("\n" + SEP);
        System.out.println("  ARCHIVIO PROPOSTE  (tutte le proposte pubblicate)");
        System.out.println(SEP);

        List<Proposta> archivio = controller.getArchivio();
        if (archivio.isEmpty()) {
            System.out.println("\n  (Nessuna proposta nell'archivio)");
            premInvio();
            return;
        }

        // Raggruppa per stato. Usando values() qualsiasi nuovo stato (es. RITIRATA in V4)
        // viene mostrato automaticamente senza modifiche alla view.
        for (StatoProposta stato : StatoProposta.values()) {
            // Ignora gli stati interni (mai persistiti in archivio)
            if (stato == StatoProposta.BOZZA || stato == StatoProposta.VALIDA) {
                continue;
            }
            List<Proposta> perStato = archivio.stream()
                    .filter(p -> p.getStato() == stato)
                    .collect(Collectors.toList());
            if (perStato.isEmpty()) {
                continue;
            }

            System.out.println("\n  ---- " + stato + "  (" + perStato.size() + ") ----");
            for (Proposta p : perStato) {
                String titolo = p.getValore("Titolo");
                String dataPub = p.getDataPubblicazione() != null
                        ? p.getDataPubblicazione().format(Proposta.DATE_FORMAT) : "-";
                System.out.println();
                System.out.println("    [ID " + p.getId() + "]"
                        + "  Categoria: " + p.getNomeCategoria()
                        + "  Titolo: \"" + (titolo.isBlank() ? "(senza titolo)" : titolo) + "\"");
                System.out.println("    Creatore: " + p.getUsernameCreatore()
                        + "   Pubblicata: " + dataPub
                        + "   Iscritti: " + p.getAderenti().size()
                        + "/" + (p.getNumeroMaxPartecipanti() < 0 ? "N/D" : p.getNumeroMaxPartecipanti()));
                System.out.println("    Termine iscrizione: " + p.getValore(Proposta.CAMPO_TERMINE_ISCRIZIONE)
                        + "   Data: " + p.getValore(Proposta.CAMPO_DATA)
                        + "   Data conclusiva: " + p.getValore(Proposta.CAMPO_DATA_CONCLUSIVA));
                // Storico stati
                if (!p.getStoricoStati().isEmpty()) {
                    System.out.print("    Storico: ");
                    System.out.println(p.getStoricoStati().stream()
                            .map(Object::toString)
                            .collect(Collectors.joining(" -> ")));
                }
                // Aderenti
                if (!p.getAderenti().isEmpty()) {
                    System.out.println("    Aderenti: " + String.join(", ", p.getAderenti()));
                }
            }
        }

        System.out.println("\n  Totale proposte in archivio: " + archivio.size());
        premInvio();
    }

    // ================================================================
    // UTILITY  (invariata da V2)
    // ================================================================
    private boolean chiediObbligatorio() {
        System.out.print("  Il campo e' obbligatorio? (s/n): ");
        return scanner.nextLine().trim().equalsIgnoreCase("s");
    }

    private void premInvio() {
        System.out.println();
        System.out.print("  Premi INVIO per tornare al menu...");
        scanner.nextLine();
    }

    private void stampaErrore(String msg) {
        System.out.println("\n  ERRORE: " + msg);
    }

    private void stampaBanner() {
        System.out.println("\n" + SEP);
        System.out.println("  EasyEvent  -  Back-end Configuratore  (Versione 3)");
        System.out.println(SEP);
        System.out.println("  Sistema di gestione iniziative ricreative");
        System.out.println("  Ingegneria del Software  -  A.A. 2025-2026");
        System.out.println(SEP);
    }
}
