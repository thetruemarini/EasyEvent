package easyevent.utente;

import easyevent.batch.BatchRisultato;
import easyevent.categoria.Campo;
import easyevent.categoria.Categoria;
import easyevent.exception.ElementoGiaEsistenteException;
import easyevent.exception.ElementoInSessioneException;
import easyevent.exception.ElementoNonTrovatoException;
import easyevent.exception.ErroreValidazione;
import easyevent.exception.ModificaNonConsentitaException;
import easyevent.exception.RitiroNonConsensitoException;
import easyevent.proposta.Proposta;
import easyevent.proposta.StatoProposta;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * Interfaccia testuale (CLI) per il configuratore - Versione 5.
 *
 * Novita' rispetto alla V4: - " Importa da file batch": consente al
 * configuratore di importare categorie, campi e proposte da uno o piu' file di
 * testo con sintassi batch, invece di inserirli manualmente riga per riga. La
 * modalita' interattiva resta invariata.
 *
 * Invariante di classe: controller != null, scanner != null
 */
public class ConfiguratoreView {

    private static final String SEP = "------------------------------------------------------------";
    private static final String SEP2 = "  ----------------------------------------------------------";

    private final ConfiguratoreController controller;
    private final Scanner scanner;

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
    public boolean avvia() {
        stampaBanner();
        while (true) {
            if (!controller.isLoggato()) {
                if (!gestioneLogin()) {
                    System.out.println("\n  Arrivederci.");
                    return false;
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
                if (scanner.nextLine().trim().equalsIgnoreCase("s")) {
                    return true;
                }
                System.out.println("\n  Arrivederci.");
                return false;
            }
        }
    }

    // ================================================================
    // LOGIN
    // ================================================================
    private boolean gestioneLogin() {
        System.out.println("\n" + SEP + "\n  LOGIN CONFIGURATORE\n" + SEP);
        System.out.println("  (digita 'esci' per uscire dall'applicazione)\n");
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
            stampaErrore("Credenziali non valide." + (rimasti > 0 ? " Tentativi rimasti: " + rimasti : ""));
            if (rimasti > 0) {
                System.out.println();
            }
        }
        System.out.println("\n  Accesso bloccato: numero massimo di tentativi raggiunto.");
        return false;
    }

    private boolean gestionePrimoAccesso() {
        System.out.println("\n" + SEP + "\n  IMPOSTAZIONE CREDENZIALI PERSONALI\n" + SEP);
        System.out.println("  (digita 'annulla' per interrompere)\n");
        System.out.print("  Nuovo username: ");
        String nu = scanner.nextLine().trim();
        if (nu.equalsIgnoreCase("annulla")) {
            return false;
        }
        if (nu.equalsIgnoreCase("esci")) {
            stampaErrore("Lo username 'esci' e' riservato.");
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
        try {
            controller.impostaCredenzialiPersonali(nu, np);
        } catch (ElementoGiaEsistenteException e) {
            stampaErrore(messaggioElementoGiaEsistente(e));
            return false;
        } catch (IllegalStateException | IllegalArgumentException e) {
            stampaErrore(e.getMessage());
            return false;
        } catch (RuntimeException e) {
            stampaErrore("Errore di sistema: " + e.getMessage());
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
            long nRitirabili = controller.getArchivio().stream()
                    .filter(p -> p.getStato() == StatoProposta.APERTA
                    || p.getStato() == StatoProposta.CONFERMATA)
                    .count();

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
            System.out.println("  8. Ritira proposta            [" + nRitirabili + " ritirabili]");
            System.out.println("  9. Importa da file batch      [NUOVO V5]");
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
                case "8" ->
                    menuRitiraProposta();
                case "9" ->
                    menuImportaBatch();
                case "0" -> {
                    return false;
                }
                default ->
                    stampaErrore("Scelta non valida.");
            }
        }
    }

    // ================================================================
    // IMPORTAZIONE BATCH
    // ================================================================
    /**
     * Interfaccia per l'importazione batch.
     *
     * L'utente può importare: a) un singolo file (inserendo il percorso) b) più
     * file in sequenza (inserendo i percorsi uno alla volta)
     *
     * Al termine mostra il resoconto completo dell'importazione.
     */
    private void menuImportaBatch() {
        System.out.println("\n" + SEP);
        System.out.println("  IMPORTAZIONE BATCH  [NUOVO V5]");
        System.out.println(SEP);
        System.out.println("  Importa categorie, campi e proposte da file di testo.");
        System.out.println("  Formato: CAMPO_COMUNE | <nome> | <si/no>");
        System.out.println("           CATEGORIA    | <nome> [| CAMPO_SPECIFICO | <nome> | <si/no>]*");
        System.out.println("           PROPOSTA     | <categoria> | <campo> = <valore> [| ...]");
        System.out.println("  Le righe che iniziano con '#' sono trattate come commenti.");
        System.out.println();
        System.out.println("  s. Importa singolo file");
        System.out.println("  m. Importa piu' file in sequenza");
        System.out.println("  e. Mostra esempio del formato batch");
        System.out.println("  0. Torna al menu principale");
        System.out.println();
        System.out.print("  Scelta: ");

        switch (scanner.nextLine().trim()) {
            case "s" ->
                importaSingoloFile();
            case "m" ->
                importaFilMultipli();
            case "e" ->
                mostraEsempioBatch();
            case "0" -> {
                /* torna */ }
            default ->
                stampaErrore("Scelta non valida.");
        }
    }

    /**
     * Importa un singolo file batch specificato dall'utente.
     */
    private void importaSingoloFile() {
        System.out.println("\n" + SEP);
        System.out.println("  IMPORTA SINGOLO FILE BATCH");
        System.out.println(SEP);
        System.out.println("  Inserisci il percorso del file batch.");
        System.out.println("  Esempio: batch_examples/setup_completo.batch");
        System.out.println("           /home/utente/mio_file.txt");
        System.out.println("  (digita 'annulla' per tornare)\n");

        System.out.print("  Percorso file: ");
        String percorso = scanner.nextLine().trim();
        if (percorso.equalsIgnoreCase("annulla") || percorso.isEmpty()) {
            System.out.println("  Operazione annullata.");
            return;
        }

        System.out.println("\n  Elaborazione in corso...\n");

        try {
            BatchRisultato risultato = controller.importaBatch(percorso);
            stampaResoconto(percorso, risultato);
        } catch (IllegalStateException e) {
            stampaErrore("Impossibile importare: " + e.getMessage());
        } catch (IOException e) {
            stampaErrore("Errore di lettura file: " + e.getMessage()
                    + "\n  Verificare che il percorso sia corretto e il file sia leggibile.");
        }

        premInvio();
    }

    /**
     * Importa più file batch in sequenza.
     */
    private void importaFilMultipli() {
        System.out.println("\n" + SEP);
        System.out.println("  IMPORTA PIU' FILE BATCH IN SEQUENZA");
        System.out.println(SEP);
        System.out.println("  Inserisci i percorsi dei file batch uno alla volta.");
        System.out.println("  Premi INVIO senza testo per avviare l'importazione.");
        System.out.println("  (digita 'annulla' per tornare)\n");

        List<String> percorsi = new ArrayList<>();
        while (true) {
            System.out.print("  File " + (percorsi.size() + 1) + " (INVIO per terminare): ");
            String percorso = scanner.nextLine().trim();
            if (percorso.equalsIgnoreCase("annulla")) {
                System.out.println("  Operazione annullata.");
                return;
            }
            if (percorso.isEmpty()) {
                if (percorsi.isEmpty()) {
                    stampaErrore("Nessun file specificato.");
                    return;
                }
                break;
            }
            percorsi.add(percorso);
            System.out.println("  Aggiunto: " + percorso);
        }

        System.out.println("\n  Elaborazione di " + percorsi.size() + " file/s in corso...\n");

        BatchRisultato risultato = controller.importaBatch(percorsi);
        stampaResocontoMulti(percorsi, risultato);
        premInvio();
    }

    /**
     * Mostra un esempio del formato batch direttamente in console.
     */
    private void mostraEsempioBatch() {
        System.out.println("\n" + SEP);
        System.out.println("  ESEMPIO DI FILE BATCH");
        System.out.println(SEP);
        System.out.println();
        System.out.println("  # EasyEvent – file batch di esempio");
        System.out.println("  # Le righe che iniziano con '#' sono commenti e vengono ignorate.");
        System.out.println("  # Le righe vuote sono ignorate.");
        System.out.println();
        System.out.println("  # ─── CAMPI COMUNI ───────────────────────────────────");
        System.out.println("  # Formato: CAMPO_COMUNE | <nome> | <si/no>");
        System.out.println("  CAMPO_COMUNE | Durata | no");
        System.out.println("  CAMPO_COMUNE | Note | no");
        System.out.println("  CAMPO_COMUNE | Compreso nella quota | no");
        System.out.println();
        System.out.println("  # ─── CATEGORIE ──────────────────────────────────────");
        System.out.println("  # Formato semplice: CATEGORIA | <nome>");
        System.out.println("  CATEGORIA | Musica");
        System.out.println();
        System.out.println("  # Formato con campi specifici inline:");
        System.out.println("  # CATEGORIA | <nome> | CAMPO_SPECIFICO | <nome_cs> | <si/no> [| ...]");
        System.out.println("  CATEGORIA | Sport | CAMPO_SPECIFICO | Certificato medico | si");
        System.out.println("  CATEGORIA | Gite | CAMPO_SPECIFICO | Mezzo di trasporto | no | CAMPO_SPECIFICO | Guida | si");
        System.out.println();
        System.out.println("  # ─── PROPOSTE ───────────────────────────────────────");
        System.out.println("  # Formato: PROPOSTA | <categoria> | <campo> = <valore> [| ...]");
        System.out.println("  # I nomi dei campi sono case-insensitive.");
        System.out.println("  # Le date vanno nel formato gg/mm/aaaa.");
        System.out.println("  # La proposta viene pubblicata in bacheca se valida.");
        System.out.println("  PROPOSTA | Sport | Titolo = Torneo di Padel | Numero di partecipanti = 8 | Termine ultimo di iscrizione = 30/06/2026 | Luogo = Palestra Centrale | Data inizio = 10/07/2026 | Ora = 09:00 | Quota individuale = 15 | Data conclusiva = 10/07/2026 | Certificato medico = Si | Durata = 6 ore");
        System.out.println();
        System.out.println("  # ─── NOTE ───────────────────────────────────────────");
        System.out.println("  # - Separatore: ' | ' (spazio-pipe-spazio)");
        System.out.println("  # - Categorie o campi gia' esistenti vengono saltati (warning)");
        System.out.println("  # - Proposte non valide vengono segnalate come errore (non pubblicate)");
        System.out.println("  # - L'elaborazione continua anche in caso di errori su singole righe");
        System.out.println();

        premInvio();
    }

    /**
     * Stampa il resoconto di un'importazione di singolo file.
     */
    private void stampaResoconto(String percorso, BatchRisultato risultato) {
        System.out.println("  " + SEP);
        System.out.println("  RESOCONTO IMPORTAZIONE: " + percorso);
        System.out.println("  " + SEP);

        System.out.println("  Righe elaborate: " + risultato.getRigheTotali());
        System.out.println("  Successi:        " + risultato.getNumSuccessi());
        System.out.println("  Warning:         " + risultato.getNumWarnings());
        System.out.println("  Errori:          " + risultato.getNumErrori());

        stampaVociResoconto(risultato);

        if (risultato.isCompletamenteOk()) {
            System.out.println("  Importazione completata senza errori ne' warning.");
        } else if (risultato.isSenzaErrori()) {
            System.out.println("  Importazione completata con " + risultato.getNumWarnings()
                    + " warning (nessun errore critico).");
        } else {
            System.out.println("  Importazione completata con " + risultato.getNumErrori()
                    + " errore/i e " + risultato.getNumWarnings() + " warning.");
        }
    }

    /**
     * Stampa il resoconto aggregato di un'importazione multi-file.
     */
    private void stampaResocontoMulti(List<String> percorsi, BatchRisultato risultato) {
        System.out.println("  " + SEP);
        System.out.println("  RESOCONTO IMPORTAZIONE MULTI-FILE (" + percorsi.size() + " file)");
        System.out.println("  File elaborati:");
        percorsi.forEach(p -> System.out.println("    - " + p));
        System.out.println("  " + SEP);

        System.out.println("  Righe elaborate: " + risultato.getRigheTotali());
        System.out.println("  Successi:        " + risultato.getNumSuccessi());
        System.out.println("  Warning:         " + risultato.getNumWarnings());
        System.out.println("  Errori:          " + risultato.getNumErrori());

        stampaVociResoconto(risultato);

        System.out.println("  Totale: " + risultato.getNumSuccessi() + " successi, "
                + risultato.getNumWarnings() + " warning, "
                + risultato.getNumErrori() + " errori su "
                + risultato.getRigheTotali() + " righe elaborate.");
    }

    /**
     * Costruisce e stampa le righe di dettaglio del resoconto. Tutta la
     * formattazione (simboli, etichette, italiano) vive qui nella View.
     */
    private void stampaVociResoconto(BatchRisultato risultato) {
        List<BatchRisultato.Voce> successi = risultato.getVoci().stream()
                .filter(v -> v.getTipo() == BatchRisultato.TipoVoce.SUCCESSO)
                .collect(java.util.stream.Collectors.toList());
        List<BatchRisultato.Voce> warnings = risultato.getVoci().stream()
                .filter(v -> v.getTipo() == BatchRisultato.TipoVoce.WARNING)
                .collect(java.util.stream.Collectors.toList());
        List<BatchRisultato.Voce> errori = risultato.getVoci().stream()
                .filter(v -> v.getTipo() == BatchRisultato.TipoVoce.ERRORE)
                .collect(java.util.stream.Collectors.toList());

        if (!successi.isEmpty()) {
            System.out.println("\n  --- OPERAZIONI COMPLETATE ---");
            successi.forEach(v -> System.out.println("  \u2713 " + v.getMessaggio()));
        }
        if (!warnings.isEmpty()) {
            System.out.println("\n  --- WARNING (operazioni saltate) ---");
            warnings.forEach(v -> {
                String prefisso = v.getNumeroRiga() > 0 ? "Riga " + v.getNumeroRiga() + ": " : "";
                System.out.println("  \u26A0 " + prefisso + v.getMessaggio());
            });
        }
        if (!errori.isEmpty()) {
            System.out.println("\n  --- ERRORI ---");
            errori.forEach(v -> {
                String prefisso = v.getNumeroRiga() > 0 ? "Riga " + v.getNumeroRiga() + ": " : "";
                System.out.println("  \u2717 " + prefisso + v.getMessaggio());
            });
        }
    }

    // ================================================================
    // RITIRO PROPOSTA (V4 – invariato)
    // ================================================================
    private void menuRitiraProposta() {
        System.out.println("\n" + SEP);
        System.out.println("  RITIRO PROPOSTA  (misura eccezionale per cause di forza maggiore)");
        System.out.println(SEP);
        LocalDate oggi = LocalDate.now();
        List<Proposta> ritirabili = controller.getArchivio().stream()
                .filter(p -> {
                    if (p.getStato() != StatoProposta.APERTA
                            && p.getStato() != StatoProposta.CONFERMATA) {
                        return false;
                    }
                    try {
                        p.verificaRitiroConsentito(oggi);
                        return true;
                    } catch (RitiroNonConsensitoException e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());
        if (ritirabili.isEmpty()) {
            System.out.println("\n  Nessuna proposta ritirabile al momento.");
            premInvio();
            return;
        }
        System.out.println("\n  Proposte ritirabili:");
        for (Proposta p : ritirabili) {
            System.out.println("  [ID " + p.getId() + "]  Stato: " + p.getStato()
                    + "  \"" + p.getValore("Titolo") + "\"  Data: " + p.getValore(Proposta.CAMPO_DATA)
                    + "  Iscritti: " + p.getAderenti().size());
        }
        System.out.print("\n  ID proposta da ritirare (0 per annullare): ");
        String input = scanner.nextLine().trim();
        if (input.equals("0")) {
            System.out.println("  Operazione annullata.");
            return;
        }
        int id;
        try {
            id = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            stampaErrore("ID non valido.");
            return;
        }
        Proposta scelta = ritirabili.stream().filter(p -> p.getId() == id).findFirst().orElse(null);
        if (scelta == null) {
            stampaErrore("Nessuna proposta ritirabile con ID " + id + ".");
            return;
        }
        System.out.print("  Confermi il ritiro di [ID " + id + "] \"" + scelta.getValore("Titolo") + "\"? (s/n): ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("s")) {
            System.out.println("  Annullato.");
            return;
        }
        try {
            controller.ritirareProposta(id);
            System.out.println("\n  Proposta [ID " + id + "] ritirata. "
                    + "Tutti gli iscritti sono stati notificati.");
        } catch (RitiroNonConsensitoException ex) {
            stampaErrore(messaggioRitiroNonConsentito(ex));
        } catch (ElementoNonTrovatoException ex) {
            stampaErrore(messaggioElementoNonTrovato(ex));
        } catch (RuntimeException ex) {
            stampaErrore("Errore: " + ex.getMessage());
        }
        premInvio();
    }

    // ================================================================
    // CAMPI BASE
    // ================================================================
    private void menuCampiBase() {
        System.out.println("\n" + SEP + "\n  CAMPI BASE  (immutabili)\n" + SEP);
        List<Campo> cb = controller.getCampiBase();
        if (cb.isEmpty()) {
            System.out.println("  (nessun campo base definito)");
        } else {
            for (int i = 0; i < cb.size(); i++) {
                System.out.printf("  %d. %s%n", i + 1, formattaCampoPerMenu(cb.get(i)));
            }
        }
        System.out.println("\n  I campi base sono fissati al primo avvio e non modificabili.");
        premInvio();
    }

    // ================================================================
    // CAMPI COMUNI
    // ================================================================
    private void menuCampiComuni() {
        while (true) {
            System.out.println("\n" + SEP + "\n  GESTIONE CAMPI COMUNI\n" + SEP);
            List<Campo> cc = controller.getCampiComuni();
            if (cc.isEmpty()) {
                System.out.println("  (nessun campo comune)");
            } else {
                for (int i = 0; i < cc.size(); i++) {
                    System.out.printf("  %d. %s%n", i + 1, formattaCampoPerMenu(cc.get(i)));
                }
            }
            System.out.println("\n  a. Aggiungi  r. Rimuovi  m. Modifica obbligatorieta'  0. Torna");
            System.out.print("  Scelta: ");
            switch (scanner.nextLine().trim()) {
                case "a" -> {
                    System.out.print("  Nome: ");
                    String n = scanner.nextLine().trim();
                    try {
                        controller.aggiungiCampoComune(n, chiediObbligatorio());
                        System.out.println("  Campo '" + n + "' aggiunto.");
                    } catch (ElementoGiaEsistenteException ex) {
                        stampaErrore(messaggioElementoGiaEsistente(ex));
                    } catch (RuntimeException ex) {
                        stampaErrore("Errore: " + ex.getMessage());
                    }
                }
                case "r" -> {
                    System.out.print("  Nome: ");
                    String n = scanner.nextLine().trim();
                    try {
                        controller.rimuoviCampoComune(n);
                        System.out.println("  Campo rimosso.");
                    } catch (ElementoNonTrovatoException ex) {
                        stampaErrore(messaggioElementoNonTrovato(ex));
                    } catch (ElementoInSessioneException ex) {
                        stampaErrore("Impossibile rimuovere: ci sono proposte in sessione "
                                + "che contengono il campo '" + ex.getNomeElemento() + "'.");
                    } catch (IllegalStateException ex) {
                        stampaErrore("Operazione non consentita: " + ex.getMessage());
                    } catch (RuntimeException ex) {
                        stampaErrore("Errore: " + ex.getMessage());
                    }
                }
                case "m" -> {
                    System.out.print("  Nome: ");
                    String n = scanner.nextLine().trim();
                    try {
                        controller.modificaObbligatorietaCampoComune(n, chiediObbligatorio());
                        System.out.println("  Aggiornato.");
                    } catch (ElementoNonTrovatoException ex) {
                        stampaErrore(messaggioElementoNonTrovato(ex));
                    } catch (RuntimeException ex) {
                        stampaErrore("Errore: " + ex.getMessage());
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

    // ================================================================
    // CATEGORIE
    // ================================================================
    private void menuCategorie() {
        while (true) {
            System.out.println("\n" + SEP + "\n  GESTIONE CATEGORIE\n" + SEP);
            List<Categoria> cats = controller.getCategorie();
            if (cats.isEmpty()) {
                System.out.println("  (nessuna categoria)");
            } else {
                for (int i = 0; i < cats.size(); i++) {
                    System.out.printf("  %d. %s%n", i + 1, cats.get(i).getNome());
                }
            }
            System.out.println("\n  a. Aggiungi  r. Rimuovi  c. Campi specifici  v. Dettaglio  0. Torna");
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
            stampaErrore("Nome vuoto.");
            return;
        }
        try {
            controller.aggiungiCategoria(nome);
            System.out.println("  Categoria '" + nome + "' aggiunta.");
            System.out.print("  Aggiungere subito campi specifici? (s/n): ");
            if (scanner.nextLine().trim().equalsIgnoreCase("s")) {
                aggiungiCampiSpecificiInterattivo(nome);
            }
        } catch (ElementoGiaEsistenteException ex) {
            stampaErrore(messaggioElementoGiaEsistente(ex));
        } catch (RuntimeException ex) {
            stampaErrore("Errore: " + ex.getMessage());
        }
    }

    private void aggiungiCampiSpecificiInterattivo(String nomeCategoria) {
        while (true) {
            System.out.print("  Nome campo specifico: ");
            String nome = scanner.nextLine().trim();
            if (nome.isEmpty()) {
                stampaErrore("Nome vuoto.");
                continue;
            }
            try {
                controller.aggiungiCampoSpecifico(nomeCategoria, nome, chiediObbligatorio());
                System.out.println("  Campo '" + nome + "' aggiunto.");
            } catch (ElementoGiaEsistenteException ex) {
                stampaErrore(messaggioElementoGiaEsistente(ex));
            } catch (ElementoNonTrovatoException ex) {
                stampaErrore(messaggioElementoNonTrovato(ex));
            } catch (RuntimeException ex) {
                stampaErrore("Errore: " + ex.getMessage());
            }
            System.out.print("  Aggiungere un altro? (s/n): ");
            if (!scanner.nextLine().trim().equalsIgnoreCase("s")) {
                break;
            }
        }
    }

    private void rimuoviCategoria() {
        System.out.print("  Nome da rimuovere: ");
        String nome = scanner.nextLine().trim();
        if (nome.isEmpty()) {
            stampaErrore("Nome vuoto.");
            return;
        }
        System.out.print("  Confermi la rimozione di '" + nome + "'? (s/n): ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("s")) {
            System.out.println("  Annullato.");
            return;
        }
        try {
            controller.rimuoviCategoria(nome);
            System.out.println("  Categoria '" + nome + "' rimossa.");
        } catch (ElementoNonTrovatoException ex) {
            stampaErrore(messaggioElementoNonTrovato(ex));
        } catch (ElementoInSessioneException ex) {
            stampaErrore("Impossibile rimuovere: ci sono proposte in sessione "
                    + "per la categoria '" + ex.getNomeElemento() + "'.");
        } catch (IllegalStateException ex) {
            stampaErrore("Operazione non consentita: " + ex.getMessage());
        } catch (RuntimeException ex) {
            stampaErrore("Errore: " + ex.getMessage());
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
            System.out.println("\n" + SEP + "\n  CAMPI SPECIFICI di '" + cat.getNome() + "'\n" + SEP);
            if (cat.getCampiSpecifici().isEmpty()) {
                System.out.println("  (nessuno)");
            } else {
                for (int i = 0; i < cat.getCampiSpecifici().size(); i++) {
                    System.out.printf("  %d. %s%n", i + 1, formattaCampoPerMenu(cat.getCampiSpecifici().get(i)));
                }
            }
            System.out.println("\n  a. Aggiungi  r. Rimuovi  m. Modifica obbligatorieta'  0. Torna");
            System.out.print("  Scelta: ");
            switch (scanner.nextLine().trim()) {
                case "a" -> {
                    System.out.print("  Nome: ");
                    String n = scanner.nextLine().trim();
                    if (n.isEmpty()) {
                        stampaErrore("Nome vuoto.");
                        break;
                    }
                    try {
                        controller.aggiungiCampoSpecifico(nomeCategoria, n, chiediObbligatorio());
                        System.out.println("  Campo aggiunto.");
                    } catch (ElementoGiaEsistenteException ex) {
                        stampaErrore(messaggioElementoGiaEsistente(ex));
                    } catch (ElementoNonTrovatoException ex) {
                        stampaErrore(messaggioElementoNonTrovato(ex));
                    } catch (RuntimeException ex) {
                        stampaErrore("Errore: " + ex.getMessage());
                    }
                }
                case "r" -> {
                    System.out.print("  Nome: ");
                    String n = scanner.nextLine().trim();
                    try {
                        controller.rimuoviCampoSpecifico(nomeCategoria, n);
                        System.out.println("  Campo rimosso.");
                    } catch (ElementoNonTrovatoException ex) {
                        stampaErrore(messaggioElementoNonTrovato(ex));
                    } catch (RuntimeException ex) {
                        stampaErrore("Errore: " + ex.getMessage());
                    }
                }
                case "m" -> {
                    System.out.print("  Nome: ");
                    String n = scanner.nextLine().trim();
                    try {
                        controller.modificaObbligatorietaCampoSpecifico(nomeCategoria, n, chiediObbligatorio());
                        System.out.println("  Aggiornato.");
                    } catch (ElementoNonTrovatoException ex) {
                        stampaErrore(messaggioElementoNonTrovato(ex));
                    } catch (RuntimeException ex) {
                        stampaErrore("Errore: " + ex.getMessage());
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
        System.out.println("\n" + SEP + "\n  DETTAGLIO: " + cat.getNome().toUpperCase() + "\n" + SEP);
        System.out.println("\n  Campi BASE:");
        controller.getCampiBase().forEach(c -> System.out.println("    - " + c.getNome() + "  [obb]"));
        System.out.println("\n  Campi COMUNI:");
        if (controller.getCampiComuni().isEmpty()) {
            System.out.println("    (nessuno)");
        } else {
            controller.getCampiComuni().forEach(c -> System.out.println("    - " + c.getNome() + "  [" + (c.isObbligatorio() ? "obb" : "fac") + "]"));
        }
        System.out.println("\n  Campi SPECIFICI:");
        if (cat.getCampiSpecifici().isEmpty()) {
            System.out.println("    (nessuno)");
        } else {
            cat.getCampiSpecifici().forEach(c -> System.out.println("    - " + c.getNome() + "  [" + (c.isObbligatorio() ? "obb" : "fac") + "]"));
        }
        premInvio();
    }

    // ================================================================
    // RIEPILOGO
    // ================================================================
    private void visualizzaRiepilogo() {
        System.out.println("\n" + SEP + "\n  RIEPILOGO CATEGORIE E CAMPI\n" + SEP);
        System.out.println("\n  CAMPI BASE:");
        controller.getCampiBase().forEach(c -> System.out.println("    - " + c.getNome()));
        System.out.println("\n  CAMPI COMUNI:");
        if (controller.getCampiComuni().isEmpty()) {
            System.out.println("    (nessuno)");
        } else {
            controller.getCampiComuni().forEach(c -> System.out.println("    - " + c.getNome() + "  [" + (c.isObbligatorio() ? "obb" : "fac") + "]"));
        }
        System.out.println("\n  CATEGORIE:");
        if (controller.getCategorie().isEmpty()) {
            System.out.println("    (nessuna)");
        } else {
            controller.getCategorie().forEach(cat -> {
                System.out.println("    > " + cat.getNome());
                if (cat.getCampiSpecifici().isEmpty()) {
                    System.out.println("      (nessun campo specifico)");
                } else {
                    cat.getCampiSpecifici().forEach(c -> System.out.println("      - " + c.getNome() + "  [" + (c.isObbligatorio() ? "obb" : "fac") + "]"));
                }
            });
        }
        premInvio();
    }

    // ================================================================
    // PROPOSTE
    // ================================================================
    private void menuProposte() {
        while (true) {
            List<Proposta> sessione = controller.getProposteSessione();
            System.out.println("\n" + SEP + "\n  GESTIONE PROPOSTE  (sessione corrente)\n" + SEP);
            if (sessione.isEmpty()) {
                System.out.println("  Nessuna proposta in sessione.");
            } else {
                sessione.forEach(p -> {
                    String t = p.getValore("Titolo");
                    System.out.println("    [ID " + p.getId() + "]  " + p.getNomeCategoria() + "  \""
                            + (t.isBlank() ? "(senza titolo)" : t) + "\"  " + p.getStato());
                });
            }
            System.out.println("\n  n. Nuova  c. Continua compilazione  p. Pubblica  e. Elimina  0. Torna");
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
            stampaErrore("Nessuna categoria disponibile.");
            return;
        }
        System.out.println("\n  Categorie: " + cats.stream().map(Categoria::getNome).collect(Collectors.joining(", ")));
        System.out.print("  Categoria della proposta: ");
        String nomeCat = scanner.nextLine().trim();
        Proposta p = controller.creaProposta(nomeCat);
        if (p == null) {
            stampaErrore("Categoria non trovata: " + nomeCat);
            return;
        }
        System.out.println("\n  Proposta [ID " + p.getId() + "] creata.");
        compilaCampiProposta(p);
        mostraRiepilogoProposta(p);
        offriPubblicazione(p);
    }

    private void continuaCompilazione() {
        if (controller.getProposteSessione().isEmpty()) {
            stampaErrore("Nessuna proposta in sessione.");
            return;
        }
        Proposta p = selezionaPropostaSessione();
        if (p == null) {
            return;
        }
        compilaCampiProposta(p);
        mostraRiepilogoProposta(p);
        offriPubblicazione(p);
    }

    private void compilaCampiProposta(Proposta p) {
        for (String nome : p.getNomiCampi()) {
            boolean ob = p.isCampoObbligatorio(nome);
            String val = p.getValore(nome);
            boolean isData = controller.isCampoData(p.getNomeCategoria(), nome);
            boolean isOra = controller.isCampoOra(p.getNomeCategoria(), nome);

            System.out.print("  " + (ob ? "[OBB]" : "[FAC]") + " " + nome);
            if (isData) {
                System.out.print("  (gg/mm/aaaa)");
            } else if (isOra) {
                System.out.print("  (HH:mm)");
            }
            if (!val.isBlank()) {
                System.out.print("  [attuale: " + val + "]");
            }
            System.out.print(": ");
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) {
                try {
                    controller.setValoreCampo(p, nome, input);
                } catch (ModificaNonConsentitaException ex) {
                    stampaErrore("Modifica non consentita per il campo '" + ex.getDettaglio() + "'.");
                } catch (RuntimeException ex) {
                    stampaErrore("Errore: " + ex.getMessage());
                }
            }
        }
    }

    private void mostraRiepilogoProposta(Proposta p) {
        System.out.println("\n" + SEP2 + "\n  Riepilogo [ID " + p.getId() + "]  Stato: " + p.getStato() + "\n" + SEP2);
        for (String nome : p.getNomiCampi()) {
            boolean ob = p.isCampoObbligatorio(nome);
            String val = p.getValore(nome);
            System.out.println("  " + (ob ? "[OBB]" : "[FAC]") + " " + nome + ": " + (val.isBlank() ? "(non compilato)" : val));
        }
        if (p.getStato() == StatoProposta.BOZZA) {
            System.out.println("\n  Problemi:");
            p.validazioneErrori(LocalDate.now())
                    .forEach(e -> System.out.println("    * " + messaggioErroreValidazione(e)));
        }
        System.out.println(SEP2);
    }

    private void offriPubblicazione(Proposta p) {
        if (p.getStato() == StatoProposta.APERTA) {
            return;
        }
        if (p.getStato() == StatoProposta.VALIDA) {
            System.out.print("\n  Proposta VALIDA. Pubblicarla ora? (s/n): ");
            if (scanner.nextLine().trim().equalsIgnoreCase("s")) {
                try {
                    List<ErroreValidazione> erroriValidazione = controller.pubblicaProposta(p);
                    if (erroriValidazione.isEmpty()) {
                        System.out.println("  Proposta [ID " + p.getId() + "] pubblicata.");
                    } else {
                        System.out.println("\n  Proposta non valida. Problemi:");
                        erroriValidazione.forEach(e -> System.out.println("    * " + messaggioErroreValidazione(e)));
                    }
                } catch (RuntimeException ex) {
                    stampaErrore("Errore: " + ex.getMessage());
                }
            } else {
                System.out.println("  Conservata in sessione.");
            }
        } else {
            System.out.println("\n  Salvata come BOZZA. Completare i campi mancanti prima di pubblicare.");
        }
    }

    private void pubblicaPropostaInterattivo() {
        List<Proposta> valide = controller.getProposteSessione().stream()
                .filter(p -> p.getStato() == StatoProposta.VALIDA).collect(Collectors.toList());
        if (valide.isEmpty()) {
            stampaErrore("Nessuna proposta VALIDA in sessione.");
            return;
        }
        valide.forEach(p -> System.out.println("    [ID " + p.getId() + "]  " + p.getNomeCategoria() + "  \"" + p.getValore("Titolo") + "\""));
        System.out.print("  ID da pubblicare: ");
        try {
            int id = Integer.parseInt(scanner.nextLine().trim());
            Proposta p = valide.stream().filter(x -> x.getId() == id).findFirst().orElse(null);
            if (p == null) {
                stampaErrore("ID non trovato.");
                return;
            }
            try {
                List<ErroreValidazione> erroriValidazione = controller.pubblicaProposta(p);
                if (erroriValidazione.isEmpty()) {
                    System.out.println("  Proposta [ID " + p.getId() + "] pubblicata.");
                } else {
                    System.out.println("\n  Proposta non valida. Problemi:");
                    erroriValidazione.forEach(e -> System.out.println("    * " + messaggioErroreValidazione(e)));
                }
            } catch (RuntimeException ex) {
                stampaErrore("Errore: " + ex.getMessage());
            }
        } catch (NumberFormatException e) {
            stampaErrore("ID non valido.");
        }
    }

    private void eliminaPropostaSessione() {
        if (controller.getProposteSessione().isEmpty()) {
            stampaErrore("Nessuna proposta in sessione.");
            return;
        }
        Proposta p = selezionaPropostaSessione();
        if (p == null) {
            return;
        }
        System.out.print("  Confermi eliminazione [ID " + p.getId() + "]? (s/n): ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("s")) {
            System.out.println("  Annullato.");
            return;
        }
        controller.eliminaPropostaSessione(p);
        System.out.println("  Proposta [ID " + p.getId() + "] eliminata.");
    }

    private Proposta selezionaPropostaSessione() {
        System.out.print("  ID proposta: ");
        try {
            int id = Integer.parseInt(scanner.nextLine().trim());
            Proposta p = controller.getProposteSessione().stream().filter(x -> x.getId() == id).findFirst().orElse(null);
            if (p == null) {
                stampaErrore("Nessuna proposta con ID " + id + ".");
            }
            return p;
        } catch (NumberFormatException e) {
            stampaErrore("ID non valido.");
            return null;
        }
    }

    // ================================================================
    // BACHECA E ARCHIVIO
    // ================================================================
    private void visualizzaBacheca() {
        System.out.println("\n" + SEP + "\n  BACHECA  -  PROPOSTE APERTE\n" + SEP);
        List<Proposta> tutte = controller.getBacheca();
        if (tutte.isEmpty()) {
            System.out.println("\n  (Nessuna proposta aperta)");
        } else {
            controller.getCategorie().forEach(cat -> {
                List<Proposta> perCat = controller.getBachecaPerCategoria(cat.getNome());
                if (!perCat.isEmpty()) {
                    System.out.println("\n  CATEGORIA: " + cat.getNome().toUpperCase() + "  (" + perCat.size() + ")");
                    perCat.forEach(p -> {
                        String dataPub = p.getDataPubblicazione() != null ? p.getDataPubblicazione().format(Proposta.DATE_FORMAT) : "-";
                        int numMax = p.getNumeroMaxPartecipanti();
                        System.out.println("\n  [ID " + p.getId() + "]  Pub: " + dataPub + "  Iscritti: " + p.getAderenti().size() + "/" + (numMax < 0 ? "N/D" : numMax));
                        p.getValori().forEach((nome, val) -> {
                            if (!val.isBlank()) {
                                System.out.println("    " + nome + ": " + val);

                            }
                        });
                    });
                }
            });
            System.out.println("\n  Totale: " + tutte.size());
        }
        premInvio();
    }

    private void visualizzaArchivio() {
        System.out.println("\n" + SEP + "\n  ARCHIVIO PROPOSTE\n" + SEP);
        List<Proposta> archivio = controller.getArchivio();
        if (archivio.isEmpty()) {
            System.out.println("\n  (Nessuna proposta)");
            premInvio();
            return;
        }
        for (StatoProposta stato : StatoProposta.values()) {
            if (stato == StatoProposta.BOZZA || stato == StatoProposta.VALIDA) {
                continue;
            }
            List<Proposta> perStato = archivio.stream().filter(p -> p.getStato() == stato).collect(Collectors.toList());
            if (perStato.isEmpty()) {
                continue;
            }
            System.out.println("\n  ---- " + stato + "  (" + perStato.size() + ") ----");
            perStato.forEach(p -> {
                String titolo = p.getValore("Titolo");
                String dataPub = p.getDataPubblicazione() != null ? p.getDataPubblicazione().format(Proposta.DATE_FORMAT) : "-";
                System.out.println("\n    [ID " + p.getId() + "]  \"" + (titolo.isBlank() ? "(senza titolo)" : titolo) + "\"  " + p.getNomeCategoria());
                System.out.println("    Pub: " + dataPub + "  Iscritti: " + p.getAderenti().size() + "/" + (p.getNumeroMaxPartecipanti() < 0 ? "N/D" : p.getNumeroMaxPartecipanti()));
                if (!p.getStoricoStati().isEmpty()) {
                    System.out.println("    Storico: " + p.getStoricoStati().stream().map(Object::toString).collect(Collectors.joining(" -> ")));
                }
            });
        }
        System.out.println("\n  Totale: " + archivio.size());
        premInvio();
    }

    // ================================================================
    // UTILITY
    // ================================================================
    private boolean chiediObbligatorio() {
        System.out.print("  Il campo e' obbligatorio? (s/n): ");
        return scanner.nextLine().trim().equalsIgnoreCase("s");
    }

    private void premInvio() {
        System.out.println();
        System.out.print("  Premi INVIO per tornare...");
        scanner.nextLine();
    }

    private void stampaErrore(String msg) {
        System.out.println("\n  ERRORE: " + msg);
    }

    private void stampaBanner() {
        System.out.println("\n" + SEP);
        System.out.println("  EasyEvent  -  Back-end Configuratore  (Versione 5)");
        System.out.println(SEP);
        System.out.println("  Sistema di gestione iniziative ricreative");
        System.out.println("  Ingegneria del Software  -  A.A. 2025-2026");
        System.out.println(SEP);
    }

    // ================================================================
    // METODI HELPER — costruiscono testo italiano da eccezioni strutturate
    // ================================================================
    private String messaggioElementoGiaEsistente(ElementoGiaEsistenteException e) {
        return switch (e.getTipoElemento()) {
            case CAMPO_BASE ->
                "Esiste già un campo BASE con nome '" + e.getNomeElemento() + "'.";
            case CAMPO_COMUNE ->
                "Esiste già un campo COMUNE con nome '" + e.getNomeElemento() + "'.";
            case CAMPO_SPECIFICO ->
                "Esiste già un campo SPECIFICO con nome '" + e.getNomeElemento() + "'.";
            case CATEGORIA ->
                "Esiste già una categoria con nome '" + e.getNomeElemento() + "'.";
            case USERNAME ->
                "Username già in uso: '" + e.getNomeElemento() + "'.";
        };
    }

    private String messaggioElementoNonTrovato(ElementoNonTrovatoException e) {
        return switch (e.getTipoElemento()) {
            case CAMPO_COMUNE ->
                "Nessun campo comune trovato con nome '" + e.getNomeElemento() + "'.";
            case CAMPO_SPECIFICO ->
                "Nessun campo specifico trovato con nome '" + e.getNomeElemento() + "'.";
            case CATEGORIA ->
                "Nessuna categoria trovata con nome '" + e.getNomeElemento() + "'.";
            case PROPOSTA ->
                "Nessuna proposta trovata con ID " + e.getNomeElemento() + ".";
        };
    }

    private String messaggioRitiroNonConsentito(RitiroNonConsensitoException e) {
        return switch (e.getTipoErrore()) {
            case STATO_NON_RITIRABILE ->
                "Il ritiro è consentito solo per proposte APERTE o CONFERMATE. "
                + "Stato attuale: " + e.getStatoAttuale() + ".";
            case DATA_EVENTO_PASSATA ->
                "Non è più possibile ritirare la proposta: "
                + "la data dell'evento è già oggi o passata.";
            case DATA_EVENTO_NON_VALORIZZATA ->
                "Impossibile verificare il ritiro: "
                + "il campo 'Data inizio' non è valorizzato o ha formato non valido.";
        };
    }

    private String formattaCampoPerMenu(Campo c) {
        String tipoLabel = switch (c.getTipo()) {
            case BASE ->
                "BASE";
            case COMUNE ->
                "COMUNE";
            case SPECIFICO ->
                "SPECIFICO";
        };
        String obbLabel = c.isObbligatorio() ? "obbligatorio" : "facoltativo";
        return "[" + tipoLabel + "] " + c.getNome() + " (" + obbLabel + ")";
    }

    private String messaggioErroreValidazione(ErroreValidazione e) {
        return switch (e.getTipo()) {
            case CAMPO_OBBLIGATORIO_VUOTO ->
                "Campo obbligatorio non compilato: '" + e.getNomeCampo() + "'";
            case DATA_FORMATO_NON_VALIDO ->
                "'" + e.getNomeCampo() + "': formato data non valido (usare gg/mm/aaaa).";
            case ORA_FORMATO_NON_VALIDO ->
                "'" + e.getNomeCampo() + "': formato non valido (usare HH:MM, es. 09:30).";
            case TERMINE_NON_FUTURO ->
                "'" + e.getNomeCampo() + "' deve essere successivo alla data odierna ("
                + e.getDettaglio() + ").";
            case DATA_INIZIO_TROPPO_VICINA ->
                "'" + e.getNomeCampo() + "' deve essere almeno 2 giorni dopo '"
                + Proposta.CAMPO_TERMINE_ISCRIZIONE + "' (minimo: " + e.getDettaglio() + ").";
            case DATA_CONCLUSIVA_PRECEDENTE ->
                "'" + e.getNomeCampo() + "' non può essere precedente a '" + Proposta.CAMPO_DATA + "'.";
            case NUM_PARTECIPANTI_NON_POSITIVO ->
                "'" + e.getNomeCampo() + "' deve essere un numero intero positivo.";
            case NUM_PARTECIPANTI_NON_NUMERICO ->
                "'" + e.getNomeCampo() + "': valore non numerico.";
        };
    }
}
