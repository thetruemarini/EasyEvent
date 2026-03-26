package it.easyevent.v4.view;

import it.easyevent.v4.controller.ConfiguratoreController;
import it.easyevent.v4.model.Campo;
import it.easyevent.v4.model.Categoria;
import it.easyevent.v4.model.Proposta;
import it.easyevent.v4.model.StatoProposta;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * Interfaccia testuale (CLI) per il configuratore - Versione 4.
 *
 * Novita' rispetto alla V3:
 *   - Voce "8. Ritira proposta": consente al configuratore di ritirare
 *     una proposta APERTA o CONFERMATA (UC-CONF-08).
 *
 * Invariante di classe: controller != null, scanner != null
 */
public class ConfiguratoreView {

    private static final String SEP  = "------------------------------------------------------------";
    private static final String SEP2 = "  ----------------------------------------------------------";

    private final ConfiguratoreController controller;
    private final Scanner scanner;

    public ConfiguratoreView(ConfiguratoreController controller, Scanner scanner) {
        if (controller == null) throw new IllegalArgumentException("Controller non puo' essere null.");
        if (scanner == null)    throw new IllegalArgumentException("Scanner non puo' essere null.");
        this.controller = controller;
        this.scanner    = scanner;
    }

    // ================================================================
    // ENTRY POINT
    // ================================================================

    public boolean avvia() {
        stampaBanner();
        while (true) {
            if (!controller.isLoggato()) {
                if (!gestioneLogin()) { System.out.println("\n  Arrivederci."); return false; }
                if (controller.richiedeCambioCredenziali()) {
                    System.out.println("\n*** Primo accesso: necessario impostare le credenziali personali. ***");
                    if (!gestionePrimoAccesso()) {
                        System.out.println("  Operazione annullata. Logout in corso.");
                        controller.logout(); continue;
                    }
                }
            }
            boolean continua = menuPrincipale();
            if (!continua) {
                int nScartate = controller.getProposteSessione().size();
                controller.logout();
                System.out.println("\n  Logout effettuato.");
                if (nScartate > 0)
                    System.out.println("  NOTA: " + nScartate + " proposta/e non pubblicata/e sono state scartate.");
                System.out.print("\n  Continuare con un altro account? (s/n): ");
                if (scanner.nextLine().trim().equalsIgnoreCase("s")) return true;
                System.out.println("\n  Arrivederci.");
                return false;
            }
        }
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
            if (username.equalsIgnoreCase("esci")) return false;
            System.out.print("  Password: ");
            String password = scanner.nextLine().trim();
            if (controller.login(username, password)) {
                System.out.println("\n  Benvenuto, " + username + "!"); return true;
            }
            int rimasti = 2 - t;
            stampaErrore("Credenziali non valide." + (rimasti > 0 ? " Tentativi rimasti: " + rimasti : ""));
            if (rimasti > 0) System.out.println();
        }
        System.out.println("\n  Accesso bloccato: numero massimo di tentativi raggiunto.");
        return false;
    }

    private boolean gestionePrimoAccesso() {
        System.out.println("\n" + SEP);
        System.out.println("  IMPOSTAZIONE CREDENZIALI PERSONALI");
        System.out.println(SEP);
        System.out.println("  (digita 'annulla' per interrompere)");
        System.out.println();
        System.out.print("  Nuovo username: ");
        String nu = scanner.nextLine().trim();
        if (nu.equalsIgnoreCase("annulla")) return false;
        if (nu.equalsIgnoreCase("esci")) { stampaErrore("Lo username 'esci' e' riservato."); return false; }
        System.out.print("  Nuova password: ");
        String np = scanner.nextLine().trim();
        if (np.equalsIgnoreCase("annulla")) return false;
        System.out.print("  Conferma password: ");
        String conf = scanner.nextLine().trim();
        if (!np.equals(conf)) { stampaErrore("Le password non coincidono."); return false; }
        String err = controller.impostaCredenzialiPersonali(nu, np);
        if (!err.isEmpty()) { stampaErrore(err); return false; }
        System.out.println("\n  Credenziali impostate correttamente.");
        return true;
    }

    // ================================================================
    // MENU PRINCIPALE
    // ================================================================

    private boolean menuPrincipale() {
        while (true) {
            int nSessione = controller.getProposteSessione().size();
            int nBacheca  = controller.getBacheca().size();
            // Proposte ritirabili = APERTE + CONFERMATE
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
            System.out.println("  0. Logout");
            System.out.println();
            System.out.print("  Scelta: ");

            switch (scanner.nextLine().trim()) {
                case "1" -> menuCampiBase();
                case "2" -> menuCampiComuni();
                case "3" -> menuCategorie();
                case "4" -> visualizzaRiepilogo();
                case "5" -> menuProposte();
                case "6" -> visualizzaBacheca();
                case "7" -> visualizzaArchivio();
                case "8" -> menuRitiraProposta();
                case "0" -> { return false; }
                default  -> stampaErrore("Scelta non valida.");
            }
        }
    }

    // ================================================================
    // RITIRA PROPOSTA (NUOVO V4 – UC-CONF-08)
    // ================================================================

    /**
     * Mostra le proposte ritirabili (APERTE e CONFERMATE) e chiede quale ritirare.
     *
     * Il ritiro e' una misura eccezionale (causa di forza maggiore).
     * Viene richiesta conferma esplicita prima di procedere.
     */
    private void menuRitiraProposta() {
        System.out.println("\n" + SEP);
        System.out.println("  RITIRO PROPOSTA  (misura eccezionale per cause di forza maggiore)");
        System.out.println(SEP);

        LocalDate oggi = LocalDate.now();
        List<Proposta> ritirabili = controller.getArchivio().stream()
                .filter(p -> (p.getStato() == StatoProposta.APERTA
                           || p.getStato() == StatoProposta.CONFERMATA)
                           && p.verificaRitiroConsentito(oggi).isEmpty())
                .collect(Collectors.toList());

        if (ritirabili.isEmpty()) {
            System.out.println("\n  Nessuna proposta ritirabile al momento.");
            System.out.println("  (Il ritiro e' possibile solo per proposte APERTE o CONFERMATE");
            System.out.println("   fino al giorno precedente la data dell'evento.)");
            premInvio();
            return;
        }

        System.out.println("\n  Proposte ritirabili:");
        System.out.println(SEP2);
        for (Proposta p : ritirabili) {
            String titolo = p.getValore("Titolo");
            String dataEv = p.getValore(Proposta.CAMPO_DATA);
            System.out.println("  [ID " + p.getId() + "]"
                    + "  Stato: " + p.getStato()
                    + "  Categoria: " + p.getNomeCategoria()
                    + "  Titolo: \"" + (titolo.isBlank() ? "(senza titolo)" : titolo) + "\""
                    + "  Data evento: " + dataEv
                    + "  Iscritti: " + p.getAderenti().size());
        }
        System.out.println();
        System.out.print("  ID della proposta da ritirare (0 per annullare): ");
        String input = scanner.nextLine().trim();
        if (input.equals("0")) { System.out.println("  Operazione annullata."); return; }

        int id;
        try { id = Integer.parseInt(input); }
        catch (NumberFormatException e) { stampaErrore("ID non valido: " + input); return; }

        Proposta scelta = ritirabili.stream().filter(p -> p.getId() == id).findFirst().orElse(null);
        if (scelta == null) {
            stampaErrore("Nessuna proposta ritirabile con ID " + id + ".");
            return;
        }

        // Mostra dettagli e chiede conferma
        String titolo = scelta.getValore("Titolo");
        System.out.println("\n  Stai per ritirare la proposta:");
        System.out.println("    [ID " + scelta.getId() + "] \"" + (titolo.isBlank() ? "(senza titolo)" : titolo) + "\"");
        System.out.println("    Stato attuale: " + scelta.getStato());
        System.out.println("    Iscritti che riceveranno notifica: " + scelta.getAderenti().size());
        System.out.println();
        System.out.println("  ATTENZIONE: il ritiro e' IRREVERSIBILE.");
        System.out.print("  Confermi il ritiro? (s/n): ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("s")) {
            System.out.println("  Operazione annullata.");
            return;
        }

        String err = controller.ritirareProposta(id);
        if (err.isEmpty()) {
            System.out.println("\n  Proposta [ID " + id + "] ritirata correttamente.");
            System.out.println("  Tutti gli iscritti sono stati notificati.");
        } else {
            stampaErrore(err);
        }
        premInvio();
    }

    // ================================================================
    // CAMPI BASE
    // ================================================================

    private void menuCampiBase() {
        System.out.println("\n" + SEP);
        System.out.println("  CAMPI BASE  (immutabili)");
        System.out.println(SEP);
        List<Campo> cb = controller.getCampiBase();
        if (cb.isEmpty()) { System.out.println("  (nessun campo base definito)"); }
        else for (int i = 0; i < cb.size(); i++) System.out.printf("  %d. %s%n", i + 1, cb.get(i));
        System.out.println("\n  I campi base sono fissati al primo avvio e non modificabili.");
        premInvio();
    }

    // ================================================================
    // CAMPI COMUNI
    // ================================================================

    private void menuCampiComuni() {
        while (true) {
            System.out.println("\n" + SEP);
            System.out.println("  GESTIONE CAMPI COMUNI");
            System.out.println(SEP);
            List<Campo> cc = controller.getCampiComuni();
            if (cc.isEmpty()) System.out.println("  (nessun campo comune definito)");
            else for (int i = 0; i < cc.size(); i++) System.out.printf("  %d. %s%n", i + 1, cc.get(i));
            System.out.println("\n  a. Aggiungi  r. Rimuovi  m. Modifica obbligatorieta'  0. Torna");
            System.out.print("  Scelta: ");
            switch (scanner.nextLine().trim()) {
                case "a" -> { System.out.print("  Nome: "); String n = scanner.nextLine().trim(); String err = controller.aggiungiCampoComune(n, chiediObbligatorio()); if (err.isEmpty()) System.out.println("  Campo '" + n + "' aggiunto."); else stampaErrore(err); }
                case "r" -> { System.out.print("  Nome: "); String n = scanner.nextLine().trim(); String err = controller.rimuoviCampoComune(n); if (err.isEmpty()) System.out.println("  Campo '" + n + "' rimosso."); else stampaErrore(err); }
                case "m" -> { System.out.print("  Nome: "); String n = scanner.nextLine().trim(); String err = controller.modificaObbligatorietaCampoComune(n, chiediObbligatorio()); if (err.isEmpty()) System.out.println("  Aggiornato."); else stampaErrore(err); }
                case "0" -> { return; }
                default  -> stampaErrore("Scelta non valida.");
            }
        }
    }

    // ================================================================
    // CATEGORIE
    // ================================================================

    private void menuCategorie() {
        while (true) {
            System.out.println("\n" + SEP);
            System.out.println("  GESTIONE CATEGORIE");
            System.out.println(SEP);
            List<Categoria> cats = controller.getCategorie();
            if (cats.isEmpty()) System.out.println("  (nessuna categoria definita)");
            else for (int i = 0; i < cats.size(); i++) System.out.printf("  %d. %s%n", i + 1, cats.get(i).getNome());
            System.out.println("\n  a. Aggiungi  r. Rimuovi  c. Campi specifici  v. Dettaglio  0. Torna");
            System.out.print("  Scelta: ");
            switch (scanner.nextLine().trim()) {
                case "a" -> aggiungiCategoria();
                case "r" -> rimuoviCategoria();
                case "c" -> menuCampiSpecifici();
                case "v" -> visualizzaDettaglioCategoria();
                case "0" -> { return; }
                default  -> stampaErrore("Scelta non valida.");
            }
        }
    }

    private void aggiungiCategoria() {
        System.out.print("  Nome della nuova categoria: ");
        String nome = scanner.nextLine().trim();
        if (nome.isEmpty()) { stampaErrore("Il nome non puo' essere vuoto."); return; }
        String err = controller.aggiungiCategoria(nome);
        if (err.isEmpty()) {
            System.out.println("  Categoria '" + nome + "' aggiunta.");
            System.out.print("  Aggiungere subito campi specifici? (s/n): ");
            if (scanner.nextLine().trim().equalsIgnoreCase("s")) aggiungiCampiSpecificiInterattivo(nome);
        } else stampaErrore(err);
    }

    private void aggiungiCampiSpecificiInterattivo(String nomeCategoria) {
        while (true) {
            System.out.print("  Nome campo specifico: ");
            String nome = scanner.nextLine().trim();
            if (nome.isEmpty()) { stampaErrore("Nome vuoto."); continue; }
            String err = controller.aggiungiCampoSpecifico(nomeCategoria, nome, chiediObbligatorio());
            if (err.isEmpty()) System.out.println("  Campo '" + nome + "' aggiunto."); else stampaErrore(err);
            System.out.print("  Aggiungere un altro campo specifico? (s/n): ");
            if (!scanner.nextLine().trim().equalsIgnoreCase("s")) break;
        }
    }

    private void rimuoviCategoria() {
        System.out.print("  Nome della categoria da rimuovere: ");
        String nome = scanner.nextLine().trim();
        if (nome.isEmpty()) { stampaErrore("Nome vuoto."); return; }
        System.out.print("  Confermi la rimozione di '" + nome + "'? (s/n): ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("s")) { System.out.println("  Annullato."); return; }
        String err = controller.rimuoviCategoria(nome);
        if (err.isEmpty()) System.out.println("  Categoria '" + nome + "' rimossa."); else stampaErrore(err);
    }

    private void menuCampiSpecifici() {
        if (controller.getCategorie().isEmpty()) { stampaErrore("Nessuna categoria disponibile."); return; }
        System.out.print("  Nome della categoria: ");
        String nomeCategoria = scanner.nextLine().trim();
        Categoria cat = controller.getCategoria(nomeCategoria);
        if (cat == null) { stampaErrore("Categoria non trovata: " + nomeCategoria); return; }

        while (true) {
            System.out.println("\n" + SEP);
            System.out.println("  CAMPI SPECIFICI di '" + cat.getNome() + "'");
            System.out.println(SEP);
            if (cat.getCampiSpecifici().isEmpty()) System.out.println("  (nessun campo specifico)");
            else for (int i = 0; i < cat.getCampiSpecifici().size(); i++)
                System.out.printf("  %d. %s%n", i + 1, cat.getCampiSpecifici().get(i));
            System.out.println("\n  a. Aggiungi  r. Rimuovi  m. Modifica obbligatorieta'  0. Torna");
            System.out.print("  Scelta: ");
            switch (scanner.nextLine().trim()) {
                case "a" -> { System.out.print("  Nome: "); String n = scanner.nextLine().trim(); if (n.isEmpty()) { stampaErrore("Nome vuoto."); break; } String err = controller.aggiungiCampoSpecifico(nomeCategoria, n, chiediObbligatorio()); if (err.isEmpty()) System.out.println("  Campo '" + n + "' aggiunto."); else stampaErrore(err); }
                case "r" -> { System.out.print("  Nome: "); String n = scanner.nextLine().trim(); String err = controller.rimuoviCampoSpecifico(nomeCategoria, n); if (err.isEmpty()) System.out.println("  Campo rimosso."); else stampaErrore(err); }
                case "m" -> { System.out.print("  Nome: "); String n = scanner.nextLine().trim(); String err = controller.modificaObbligatorietaCampoSpecifico(nomeCategoria, n, chiediObbligatorio()); if (err.isEmpty()) System.out.println("  Aggiornato."); else stampaErrore(err); }
                case "0" -> { return; }
                default  -> stampaErrore("Scelta non valida.");
            }
        }
    }

    private void visualizzaDettaglioCategoria() {
        System.out.print("  Nome della categoria: ");
        String nome = scanner.nextLine().trim();
        Categoria cat = controller.getCategoria(nome);
        if (cat == null) { stampaErrore("Categoria non trovata: " + nome); return; }
        System.out.println("\n" + SEP + "\n  DETTAGLIO: " + cat.getNome().toUpperCase() + "\n" + SEP);
        System.out.println("\n  Campi BASE:");
        controller.getCampiBase().forEach(c -> System.out.println("    - " + c.getNome() + "  [obbligatorio]"));
        System.out.println("\n  Campi COMUNI:");
        if (controller.getCampiComuni().isEmpty()) System.out.println("    (nessuno)");
        else controller.getCampiComuni().forEach(c -> System.out.println("    - " + c.getNome() + "  [" + (c.isObbligatorio() ? "obbligatorio" : "facoltativo") + "]"));
        System.out.println("\n  Campi SPECIFICI:");
        if (cat.getCampiSpecifici().isEmpty()) System.out.println("    (nessuno)");
        else cat.getCampiSpecifici().forEach(c -> System.out.println("    - " + c.getNome() + "  [" + (c.isObbligatorio() ? "obbligatorio" : "facoltativo") + "]"));
        premInvio();
    }

    // ================================================================
    // RIEPILOGO
    // ================================================================

    private void visualizzaRiepilogo() {
        System.out.println("\n" + SEP + "\n  RIEPILOGO CATEGORIE E CAMPI\n" + SEP);
        System.out.println("\n  CAMPI BASE:"); controller.getCampiBase().forEach(c -> System.out.println("    - " + c.getNome()));
        System.out.println("\n  CAMPI COMUNI:");
        if (controller.getCampiComuni().isEmpty()) System.out.println("    (nessuno)");
        else controller.getCampiComuni().forEach(c -> System.out.println("    - " + c.getNome() + "  [" + (c.isObbligatorio() ? "obb" : "fac") + "]"));
        System.out.println("\n  CATEGORIE:");
        if (controller.getCategorie().isEmpty()) System.out.println("    (nessuna)");
        else controller.getCategorie().forEach(cat -> {
            System.out.println("    > " + cat.getNome());
            if (cat.getCampiSpecifici().isEmpty()) System.out.println("      (nessun campo specifico)");
            else cat.getCampiSpecifici().forEach(c -> System.out.println("      - " + c.getNome() + "  [" + (c.isObbligatorio() ? "obb" : "fac") + "]"));
        });
        premInvio();
    }

    // ================================================================
    // PROPOSTE
    // ================================================================

    private void menuProposte() {
        while (true) {
            List<Proposta> sessione = controller.getProposteSessione();
            System.out.println("\n" + SEP + "\n  GESTIONE PROPOSTE  (sessione corrente)\n" + SEP);
            if (sessione.isEmpty()) System.out.println("  Nessuna proposta in sessione.");
            else sessione.forEach(p -> {
                String t = p.getValore("Titolo");
                System.out.println("    [ID " + p.getId() + "]  " + p.getNomeCategoria() + "  \"" + (t.isBlank() ? "(senza titolo)" : t) + "\"  " + p.getStato());
            });
            System.out.println("\n  n. Nuova  c. Continua compilazione  p. Pubblica  e. Elimina  0. Torna");
            System.out.print("  Scelta: ");
            switch (scanner.nextLine().trim()) {
                case "n" -> nuovaProposta();
                case "c" -> continuaCompilazione();
                case "p" -> pubblicaPropostaInterattivo();
                case "e" -> eliminaPropostaSessione();
                case "0" -> { return; }
                default  -> stampaErrore("Scelta non valida.");
            }
        }
    }

    private void nuovaProposta() {
        List<Categoria> cats = controller.getCategorie();
        if (cats.isEmpty()) { stampaErrore("Nessuna categoria disponibile."); return; }
        System.out.println("\n  Categorie: " + cats.stream().map(Categoria::getNome).collect(Collectors.joining(", ")));
        System.out.print("  Categoria della proposta: ");
        String nomeCategoria = scanner.nextLine().trim();
        Proposta p = controller.creaProposta(nomeCategoria);
        if (p == null) { stampaErrore("Categoria non trovata: " + nomeCategoria); return; }
        System.out.println("\n  Proposta [ID " + p.getId() + "] creata.  [OBB]=obbligatorio  [FAC]=facoltativo");
        compilaCampiProposta(p);
        mostraRiepilogoProposta(p);
        offriPubblicazione(p);
    }

    private void continuaCompilazione() {
        if (controller.getProposteSessione().isEmpty()) { stampaErrore("Nessuna proposta in sessione."); return; }
        Proposta p = selezionaPropostaSessione("da continuare");
        if (p == null) return;
        compilaCampiProposta(p);
        mostraRiepilogoProposta(p);
        offriPubblicazione(p);
    }

    private void compilaCampiProposta(Proposta p) {
        for (Map.Entry<String, Boolean> entry : p.getCampiSnapshot().entrySet()) {
            String nome = entry.getKey(); boolean ob = entry.getValue();
            String val = p.getValore(nome);
            boolean isData = nome.contains("Data") || nome.equals(Proposta.CAMPO_TERMINE_ISCRIZIONE);
            System.out.print("  " + (ob ? "[OBB]" : "[FAC]") + " " + nome);
            if (isData) System.out.print("  (gg/mm/aaaa)");
            if (!val.isBlank()) System.out.print("  [attuale: " + val + "]");
            System.out.print(": ");
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) { String err = controller.setValoreCampo(p, nome, input); if (!err.isEmpty()) stampaErrore(err); }
        }
    }

    private void mostraRiepilogoProposta(Proposta p) {
        System.out.println("\n" + SEP2 + "\n  Riepilogo [ID " + p.getId() + "]  Stato: " + p.getStato() + "\n" + SEP2);
        p.getCampiSnapshot().forEach((nome, ob) -> {
            String val = p.getValore(nome);
            System.out.println("  " + (ob ? "[OBB]" : "[FAC]") + " " + nome + ": " + (val.isBlank() ? "(non compilato)" : val));
        });
        if (p.getStato() == StatoProposta.BOZZA) {
            System.out.println("\n  Problemi:");
            p.validazioneErrori(LocalDate.now()).forEach(e -> System.out.println("    * " + e));
        }
        System.out.println(SEP2);
    }

    private void offriPubblicazione(Proposta p) {
        if (p.getStato() == StatoProposta.APERTA) return;
        if (p.getStato() == StatoProposta.VALIDA) {
            System.out.print("\n  Proposta VALIDA. Pubblicarla ora? (s/n): ");
            if (scanner.nextLine().trim().equalsIgnoreCase("s")) {
                String err = controller.pubblicaProposta(p);
                if (err.isEmpty()) System.out.println("  Proposta [ID " + p.getId() + "] pubblicata.");
                else stampaErrore(err);
            } else System.out.println("  Conservata in sessione.");
        } else System.out.println("\n  Salvata come BOZZA. Completare i campi mancanti prima di pubblicare.");
    }

    private void pubblicaPropostaInterattivo() {
        List<Proposta> valide = controller.getProposteSessione().stream()
                .filter(p -> p.getStato() == StatoProposta.VALIDA).collect(Collectors.toList());
        if (valide.isEmpty()) { stampaErrore("Nessuna proposta VALIDA in sessione."); return; }
        System.out.println("\n  Proposte VALIDE:");
        valide.forEach(p -> System.out.println("    [ID " + p.getId() + "]  " + p.getNomeCategoria() + "  \"" + p.getValore("Titolo") + "\""));
        System.out.print("  ID da pubblicare: ");
        try {
            int id = Integer.parseInt(scanner.nextLine().trim());
            Proposta p = valide.stream().filter(x -> x.getId() == id).findFirst().orElse(null);
            if (p == null) { stampaErrore("ID non trovato."); return; }
            String err = controller.pubblicaProposta(p);
            if (err.isEmpty()) System.out.println("  Proposta [ID " + id + "] pubblicata."); else stampaErrore(err);
        } catch (NumberFormatException e) { stampaErrore("ID non valido."); }
    }

    private void eliminaPropostaSessione() {
        if (controller.getProposteSessione().isEmpty()) { stampaErrore("Nessuna proposta in sessione."); return; }
        Proposta p = selezionaPropostaSessione("da eliminare");
        if (p == null) return;
        System.out.print("  Confermi eliminazione [ID " + p.getId() + "]? (s/n): ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("s")) { System.out.println("  Annullato."); return; }
        controller.eliminaPropostaSessione(p);
        System.out.println("  Proposta [ID " + p.getId() + "] eliminata dalla sessione.");
    }

    private Proposta selezionaPropostaSessione(String azione) {
        System.out.print("  ID proposta " + azione + ": ");
        try {
            int id = Integer.parseInt(scanner.nextLine().trim());
            Proposta p = controller.getProposteSessione().stream().filter(x -> x.getId() == id).findFirst().orElse(null);
            if (p == null) stampaErrore("Nessuna proposta con ID " + id + ".");
            return p;
        } catch (NumberFormatException e) { stampaErrore("ID non valido."); return null; }
    }

    // ================================================================
    // BACHECA
    // ================================================================

    private void visualizzaBacheca() {
        System.out.println("\n" + SEP + "\n  BACHECA  -  PROPOSTE APERTE\n" + SEP);
        List<Proposta> tutte = controller.getBacheca();
        if (tutte.isEmpty()) { System.out.println("\n  (Nessuna proposta aperta in bacheca)"); }
        else {
            controller.getCategorie().forEach(cat -> {
                List<Proposta> perCat = controller.getBachecaPerCategoria(cat.getNome());
                if (!perCat.isEmpty()) {
                    System.out.println("\n  CATEGORIA: " + cat.getNome().toUpperCase() + "  (" + perCat.size() + ")");
                    perCat.forEach(this::stampaBloccoPropostaBacheca);
                }
            });
            System.out.println("\n  Totale: " + tutte.size());
        }
        premInvio();
    }

    private void stampaBloccoPropostaBacheca(Proposta p) {
        String dataPub = p.getDataPubblicazione() != null ? p.getDataPubblicazione().format(Proposta.DATE_FORMAT) : "-";
        int numMax = p.getNumeroMaxPartecipanti();
        System.out.println("\n  [ID " + p.getId() + "]  Pubblicata: " + dataPub + "  Iscritti: " + p.getAderenti().size() + "/" + (numMax < 0 ? "N/D" : numMax));
        p.getValori().forEach((nome, val) -> { if (!val.isBlank()) System.out.println("    " + nome + ": " + val); });
    }

    // ================================================================
    // ARCHIVIO
    // ================================================================

    private void visualizzaArchivio() {
        System.out.println("\n" + SEP + "\n  ARCHIVIO PROPOSTE\n" + SEP);
        List<Proposta> archivio = controller.getArchivio();
        if (archivio.isEmpty()) { System.out.println("\n  (Nessuna proposta nell'archivio)"); premInvio(); return; }

        for (StatoProposta stato : StatoProposta.values()) {
            if (stato == StatoProposta.BOZZA || stato == StatoProposta.VALIDA) continue;
            List<Proposta> perStato = archivio.stream().filter(p -> p.getStato() == stato).collect(Collectors.toList());
            if (perStato.isEmpty()) continue;
            System.out.println("\n  ---- " + stato + "  (" + perStato.size() + ") ----");
            for (Proposta p : perStato) {
                String titolo = p.getValore("Titolo");
                String dataPub = p.getDataPubblicazione() != null ? p.getDataPubblicazione().format(Proposta.DATE_FORMAT) : "-";
                System.out.println("\n    [ID " + p.getId() + "]  \"" + (titolo.isBlank() ? "(senza titolo)" : titolo) + "\"  Categoria: " + p.getNomeCategoria());
                System.out.println("    Creatore: " + p.getUsernameCreatore() + "  Pubblicata: " + dataPub + "  Iscritti: " + p.getAderenti().size() + "/" + (p.getNumeroMaxPartecipanti() < 0 ? "N/D" : p.getNumeroMaxPartecipanti()));
                System.out.println("    Termine: " + p.getValore(Proposta.CAMPO_TERMINE_ISCRIZIONE) + "  Data: " + p.getValore(Proposta.CAMPO_DATA));
                if (!p.getStoricoStati().isEmpty())
                    System.out.println("    Storico: " + p.getStoricoStati().stream().map(Object::toString).collect(Collectors.joining(" -> ")));
                if (!p.getAderenti().isEmpty())
                    System.out.println("    Aderenti: " + String.join(", ", p.getAderenti()));
            }
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
        System.out.println(); System.out.print("  Premi INVIO per tornare al menu..."); scanner.nextLine();
    }

    private void stampaErrore(String msg) { System.out.println("\n  ERRORE: " + msg); }

    private void stampaBanner() {
        System.out.println("\n" + SEP);
        System.out.println("  EasyEvent  -  Back-end Configuratore  (Versione 4)");
        System.out.println(SEP);
        System.out.println("  Sistema di gestione iniziative ricreative");
        System.out.println("  Ingegneria del Software  -  A.A. 2025-2026");
        System.out.println(SEP);
    }
}