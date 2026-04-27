package easyevent.model;

import easyevent.model.exception.ElementoGiaEsistenteException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Contenitore centrale dello stato dell'applicazione (Versione 5).
 *
 * Identico alla V4; la V5 non aggiunge nuovi stati o strutture dati. Le nuove
 * funzionalità batch operano sulle stesse strutture dati di V4.
 *
 * Pattern Singleton.
 *
 * Invariante di classe: - tutti i campi != null - prossimoIdProposta >= 1,
 * prossimoIdNotifica >= 1 - username globalmente univoci - archivio: nessuna
 * proposta con stato BOZZA o VALIDA
 */
public class AppData {

    public static final String DEFAULT_USERNAME = "admin";
    public static final String DEFAULT_PASSWORD = "admin123";

    public static final String[] NOMI_CAMPI_BASE = {
        "Titolo",
        "Numero di partecipanti",
        "Termine ultimo di iscrizione",
        "Luogo",
        "Data inizio",
        "Ora",
        "Quota individuale",
        "Data conclusiva"
    };

    private List<Configuratore> configuratori;
    private List<Campo> campiBase;
    private boolean campiBaseInizialized;
    private List<Campo> campiComuni;
    private List<Categoria> categorie;
    private List<Proposta> archivio;
    private int prossimoIdProposta;
    private List<Fruitore> fruitori;
    private int prossimoIdNotifica;

    public AppData() {
        configuratori = new ArrayList<>();
        campiBase = new ArrayList<>();
        campiBaseInizialized = false;
        campiComuni = new ArrayList<>();
        categorie = new ArrayList<>();
        archivio = new ArrayList<>();
        prossimoIdProposta = 1;
        fruitori = new ArrayList<>();
        prossimoIdNotifica = 1;
    }

    // ================================================================
    // CONFIGURATORI
    // ================================================================
    public void aggiungiConfiguratore(Configuratore conf) {
        if (conf == null) {
            throw new IllegalArgumentException("Configuratore non puo' essere null.");
        }
        if (esisteUsernameGlobale(conf.getUsername())) {
            throw new ElementoGiaEsistenteException(
                    ElementoGiaEsistenteException.TipoElemento.USERNAME,
                    conf.getUsername()
            );
        }
        configuratori.add(conf);
        assert repOk() : "Invariante violato dopo aggiungiConfiguratore";
    }

    public Configuratore getConfiguratore(String username) {
        if (username == null) {
            return null;
        }
        return configuratori.stream()
                .filter(c -> c.getUsername().equalsIgnoreCase(username))
                .findFirst().orElse(null);
    }

    public List<Configuratore> getConfiguratori() {
        return Collections.unmodifiableList(configuratori);
    }

    // ================================================================
    // FRUITORI
    // ================================================================
    public void aggiungiFruitore(Fruitore fruitore) {
        if (fruitore == null) {
            throw new IllegalArgumentException("Fruitore non puo' essere null.");
        }
        if (esisteUsernameGlobale(fruitore.getUsername())) {
            throw new ElementoGiaEsistenteException(
                    ElementoGiaEsistenteException.TipoElemento.USERNAME,
                    fruitore.getUsername()
            );
        }
        fruitori.add(fruitore);
        assert repOk() : "Invariante violato dopo aggiungiFruitore";
    }

    public Fruitore getFruitore(String username) {
        if (username == null) {
            return null;
        }
        return fruitori.stream()
                .filter(f -> f.getUsername().equalsIgnoreCase(username))
                .findFirst().orElse(null);
    }

    public boolean rimuoviFruitore(String username) {
        if (username == null) {
            return false;
        }
        return fruitori.removeIf(f -> f.getUsername().equalsIgnoreCase(username));
    }

    public List<Fruitore> getFruitori() {
        return Collections.unmodifiableList(fruitori);
    }

    // ================================================================
    // UNICITA' USERNAME GLOBALE
    // ================================================================
    public boolean esisteUsernameGlobale(String username) {
        if (username == null) {
            return false;
        }
        return configuratori.stream().anyMatch(c -> c.getUsername().equalsIgnoreCase(username))
                || fruitori.stream().anyMatch(f -> f.getUsername().equalsIgnoreCase(username));
    }

    @Deprecated
    public boolean esisteUsername(String username) {
        return esisteUsernameGlobale(username);
    }

    // ================================================================
    // CAMPI BASE
    // ================================================================
    public void inizializzaCampiBase() {
        if (campiBaseInizialized) {
            throw new IllegalStateException("I campi base sono gia' stati inizializzati.");
        }
        for (String nome : NOMI_CAMPI_BASE) {
            campiBase.add(new Campo(nome, true, Campo.TipoCampo.BASE));
        }
        campiBaseInizialized = true;
        assert repOk() : "Invariante violato dopo inizializzaCampiBase";
    }

    public boolean isCampiBaseInitialized() {
        return campiBaseInizialized;
    }

    public List<Campo> getCampiBase() {
        return Collections.unmodifiableList(campiBase);
    }

    public boolean esisteCampoBase(String nome) {
        if (nome == null) {
            return false;
        }
        return campiBase.stream().anyMatch(c -> c.getNome().equalsIgnoreCase(nome));
    }

    // ================================================================
    // CAMPI COMUNI
    // ================================================================
    public void aggiungiCampoComune(Campo campo) {
        if (campo == null) {
            throw new IllegalArgumentException("Il campo non puo' essere null.");
        }
        if (campo.getTipo() != Campo.TipoCampo.COMUNE) {
            throw new IllegalArgumentException("Il campo deve essere di tipo COMUNE.");
        }
        if (esisteCampoBase(campo.getNome())) {
            throw new ElementoGiaEsistenteException(
                    ElementoGiaEsistenteException.TipoElemento.CAMPO_BASE,
                    campo.getNome()
            );
        }
        if (esisteCampoComune(campo.getNome())) {
            throw new ElementoGiaEsistenteException(
                    ElementoGiaEsistenteException.TipoElemento.CAMPO_COMUNE,
                    campo.getNome()
            );
        }
        campiComuni.add(campo);
        assert repOk() : "Invariante violato dopo aggiungiCampoComune";
    }

    public boolean rimuoviCampoComune(String nomeCampo) {
        boolean rimosso = campiComuni.removeIf(c -> c.getNome().equalsIgnoreCase(nomeCampo));
        assert repOk() : "Invariante violato dopo rimuoviCampoComune";
        return rimosso;
    }

    public boolean modificaObbligatorietaCampoComune(String nomeCampo, boolean obbligatorio) {
        for (Campo c : campiComuni) {
            if (c.getNome().equalsIgnoreCase(nomeCampo)) {
                c.setObbligatorio(obbligatorio);
                return true;
            }
        }
        return false;
    }

    public boolean esisteCampoComune(String nome) {
        if (nome == null) {
            return false;
        }
        return campiComuni.stream().anyMatch(c -> c.getNome().equalsIgnoreCase(nome));
    }

    public Campo getCampoComune(String nome) {
        if (nome == null) {
            return null;
        }
        return campiComuni.stream()
                .filter(c -> c.getNome().equalsIgnoreCase(nome)).findFirst().orElse(null);
    }

    public List<Campo> getCampiComuni() {
        return Collections.unmodifiableList(campiComuni);
    }

    // ================================================================
    // RICERCA GLOBALE CAMPI (Aggiunta per Refactoring Problema 6)
    // ================================================================
    public Campo getCampo(String nomeCategoria, String nomeCampo) {
        if (nomeCampo == null) {
            return null;
        }

        // 1. Cerca nei campi base
        for (Campo c : campiBase) {
            if (c.getNome().equalsIgnoreCase(nomeCampo)) {
                return c;
            }
        }
        // 2. Cerca nei campi comuni
        for (Campo c : campiComuni) {
            if (c.getNome().equalsIgnoreCase(nomeCampo)) {
                return c;
            }
        }
        // 3. Cerca nei campi specifici della categoria
        Categoria cat = getCategoria(nomeCategoria);
        if (cat != null) {
            for (Campo c : cat.getCampiSpecifici()) {
                if (c.getNome().equalsIgnoreCase(nomeCampo)) {
                    return c;
                }
            }
        }
        return null;
    }

    // ================================================================
    // CATEGORIE
    // ================================================================
    public void aggiungiCategoria(Categoria categoria) {
        if (categoria == null) {
            throw new IllegalArgumentException("La categoria non puo' essere null.");
        }
        if (esisteCategoria(categoria.getNome())) {
            throw new ElementoGiaEsistenteException(
                    ElementoGiaEsistenteException.TipoElemento.CATEGORIA,
                    categoria.getNome()
            );
        }
        categorie.add(categoria);
        assert repOk() : "Invariante violato dopo aggiungiCategoria";
    }

    public boolean rimuoviCategoria(String nomeCategoria) {
        boolean rimossa = categorie.removeIf(cat -> cat.getNome().equalsIgnoreCase(nomeCategoria));
        assert repOk() : "Invariante violato dopo rimuoviCategoria";
        return rimossa;
    }

    public boolean esisteCategoria(String nome) {
        if (nome == null) {
            return false;
        }
        return categorie.stream().anyMatch(cat -> cat.getNome().equalsIgnoreCase(nome));
    }

    public Categoria getCategoria(String nome) {
        if (nome == null) {
            return null;
        }
        return categorie.stream()
                .filter(cat -> cat.getNome().equalsIgnoreCase(nome)).findFirst().orElse(null);
    }

    public List<Categoria> getCategorie() {
        return Collections.unmodifiableList(categorie);
    }

    // ================================================================
    // ARCHIVIO
    // ================================================================
    public void aggiungiPropostaAperta(Proposta proposta) {
        if (proposta == null) {
            throw new IllegalArgumentException("La proposta non puo' essere null.");
        }
        if (proposta.getStato() != StatoProposta.APERTA) {
            throw new IllegalArgumentException("Solo proposte APERTE possono essere aggiunte all'archivio.");
        }
        archivio.add(proposta);
        assert repOk() : "Invariante violato dopo aggiungiPropostaAperta";
    }

    public List<Proposta> getArchivio() {
        return Collections.unmodifiableList(archivio);
    }

    public boolean rimuoviPropostaDaArchivio(int id) {
        return archivio.removeIf(p -> p.getId() == id);
    }

    public Proposta getPropostaDaArchivio(int id) {
        return archivio.stream().filter(p -> p.getId() == id).findFirst().orElse(null);
    }

    public List<Proposta> getBacheca() {
        return archivio.stream()
                .filter(p -> p.getStato() == StatoProposta.APERTA)
                .collect(Collectors.toList());
    }

    public List<Proposta> getBachecaPerCategoria(String nomeCategoria) {
        if (nomeCategoria == null) {
            return new ArrayList<>();
        }
        return archivio.stream()
                .filter(p -> p.getStato() == StatoProposta.APERTA
                && p.getNomeCategoria().equalsIgnoreCase(nomeCategoria))
                .collect(Collectors.toList());
    }

    public int getNuovoIdProposta() {
        return prossimoIdProposta++;
    }

    public int getProssimoIdProposta() {
        return prossimoIdProposta;
    }

    public int getNuovoIdNotifica() {
        return prossimoIdNotifica++;
    }

    public int getProssimoIdNotifica() {
        return prossimoIdNotifica;
    }

    // ================================================================
    // TRANSIZIONI AUTOMATICHE
    // ================================================================
    public int aggiornaTransizioni(LocalDate oggi) {
        int modificate = 0;
        for (Proposta p : archivio) {
            if (p.getStato() == StatoProposta.APERTA) {
                LocalDate termine = p.getTermineIscrizione();
                if (termine != null && oggi.isAfter(termine)) {
                    int numMax = p.getNumeroMaxPartecipanti();
                    int numAd = p.getAderenti().size();
                    if (numMax >= 0 && numAd >= numMax) {
                        p.transitaStato(StatoProposta.CONFERMATA, oggi);
                        notificaAderenti(p, TipoNotifica.PROPOSTA_CONFERMATA, oggi);
                    } else {
                        p.transitaStato(StatoProposta.ANNULLATA, oggi);
                        notificaAderenti(p, TipoNotifica.PROPOSTA_ANNULLATA, oggi);
                    }
                    modificate++;
                }
            }
        }
        for (Proposta p : archivio) {
            if (p.getStato() == StatoProposta.CONFERMATA) {
                LocalDate dataConc = p.getDataConclusiva();
                if (dataConc != null && oggi.isAfter(dataConc)) {
                    p.transitaStato(StatoProposta.CONCLUSA, oggi);
                    modificate++;
                }
            }
        }
        return modificate;
    }

    // ================================================================
    // RITIRO PROPOSTA (V4)
    // ================================================================
    public void ritirareProposta(Proposta proposta, LocalDate oggi) {
        if (proposta == null) {
            throw new IllegalArgumentException("La proposta non puo' essere null.");
        }
        proposta.transitaStato(StatoProposta.RITIRATA, oggi);
        notificaAderenti(proposta, TipoNotifica.PROPOSTA_RITIRATA, oggi);
        assert proposta.getStato() == StatoProposta.RITIRATA : "Postcondizione violata: stato non RITIRATA dopo ritirareProposta";
    }

    // ================================================================
    // HELPERS PRIVATI
    // ================================================================
    private void notificaAderenti(Proposta p, TipoNotifica tipo, LocalDate oggi) {
        for (String usernameF : p.getAderenti()) {
            Fruitore f = getFruitore(usernameF);
            if (f != null) {
                Notifica n = new Notifica(
                        getNuovoIdNotifica(),
                        tipo, // COSA è successo
                        p.getId(), // dati grezzi
                        p.getValore("Titolo"),
                        p.getValore(Proposta.CAMPO_DATA),
                        p.getValore("Ora"),
                        p.getValore("Luogo"),
                        p.getValore("Quota individuale"),
                        oggi
                );
                f.aggiungiNotifica(n);
            }
        }
    }

    // ================================================================
    // SETTERS PER DESERIALIZZAZIONE
    // ================================================================
    public void setCampiBase(List<Campo> campiBase) {
        this.campiBase = new ArrayList<>(campiBase);
        this.campiBaseInizialized = !campiBase.isEmpty();
    }

    public void setCampiComuni(List<Campo> campiComuni) {
        this.campiComuni = new ArrayList<>(campiComuni);
    }

    public void setCategorie(List<Categoria> categorie) {
        this.categorie = new ArrayList<>(categorie);
    }

    public void setConfiguratori(List<Configuratore> list) {
        this.configuratori = new ArrayList<>(list);
    }

    public void setFruitori(List<Fruitore> list) {
        this.fruitori = new ArrayList<>(list);
    }

    public void setArchivio(List<Proposta> archivio) {
        this.archivio = new ArrayList<>(archivio);
        int maxId = archivio.stream().mapToInt(Proposta::getId).max().orElse(0);
        this.prossimoIdProposta = Math.max(this.prossimoIdProposta, maxId + 1);
    }

    public void setBacheca(List<Proposta> bacheca) {
        setArchivio(bacheca);
    }

    public void setProssimoIdProposta(int id) {
        this.prossimoIdProposta = id;
    }

    public void setProssimoIdNotifica(int id) {
        this.prossimoIdNotifica = id;
    }

    // ================================================================
    // INVARIANTE
    // ================================================================
    public boolean repOk() {
        if (configuratori == null || fruitori == null || campiBase == null
                || campiComuni == null || categorie == null || archivio == null) {
            return false;
        }
        if (prossimoIdProposta < 1 || prossimoIdNotifica < 1) {
            return false;
        }
        List<String> tuttiUsername = new ArrayList<>();
        configuratori.forEach(c -> tuttiUsername.add(c.getUsername().toLowerCase()));
        fruitori.forEach(f -> tuttiUsername.add(f.getUsername().toLowerCase()));
        long distinti = tuttiUsername.stream().distinct().count();
        if (distinti != tuttiUsername.size()) {
            return false;
        }
        long nomiDistinti = categorie.stream()
                .map(cat -> cat.getNome().toLowerCase()).distinct().count();
        if (nomiDistinti != categorie.size()) {
            return false;
        }
        for (Campo cc : campiComuni) {
            if (esisteCampoBase(cc.getNome())) {
                return false;
            }
        }
        for (Proposta p : archivio) {
            if (p.getStato() == StatoProposta.BOZZA || p.getStato() == StatoProposta.VALIDA) {
                return false;
            }
        }
        return true;
    }
}
