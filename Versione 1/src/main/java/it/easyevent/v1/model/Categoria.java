package it.easyevent.v1.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rappresenta una categoria di iniziative (es. "sport", "gite", "arte"). Ogni
 * categoria possiede un nome univoco e un insieme di campi specifici. I campi
 * base e comuni sono condivisi da tutte le categorie e gestiti centralmente da
 * AppData.
 *
 * Invariante di classe: 
 * - nome != null && !nome.isBlank() 
 * - campiSpecifici != null
 * - nessun campo in campiSpecifici ha lo stesso nome di un altro (case-insensitive)
 */
public class Categoria {

    private String nome;
    private List<Campo> campiSpecifici;

    /**
     * Costruttore.
     *
     * @param nome nome univoco della categoria, non null e non blank
     * @throws IllegalArgumentException se nome è null o blank
     */
    public Categoria(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Il nome della categoria non può essere null o vuoto.");
        }
        this.nome = nome.trim();
        this.campiSpecifici = new ArrayList<>();

        assert repOk() : "Invariante violato dopo costruzione di Categoria";
    }

    /**
     * Aggiunge un campo specifico alla categoria.
     *
     * @param campo campo da aggiungere, non null, di tipo SPECIFICO
     * @throws IllegalArgumentException se campo è null, non è SPECIFICO, o
     * esiste già un campo con lo stesso nome
     */
    public void aggiungiCampoSpecifico(Campo campo) {
        // Precondizioni
        if (campo == null) {
            throw new IllegalArgumentException("Il campo non può essere null.");
        }
        if (campo.getTipo() != Campo.TipoCampo.SPECIFICO) {
            throw new IllegalArgumentException("Il campo deve essere di tipo SPECIFICO.");
        }
        if (contieneCampo(campo.getNome())) {
            throw new IllegalArgumentException("Esiste già un campo specifico con nome: " + campo.getNome());
        }

        campiSpecifici.add(campo);

        // Postcondizione
        assert campiSpecifici.contains(campo) : "Postcondizione violata: campo non aggiunto";
        assert repOk() : "Invariante violato dopo aggiungiCampoSpecifico";
    }

    /**
     * Rimuove un campo specifico dalla categoria dato il suo nome.
     *
     * @param nomeCampo nome del campo da rimuovere, non null
     * @return true se il campo è stato rimosso, false se non trovato
     */
    public boolean rimuoviCampoSpecifico(String nomeCampo) {
        if (nomeCampo == null) {
            return false;
        }
        boolean rimosso = campiSpecifici.removeIf(c -> c.getNome().equalsIgnoreCase(nomeCampo));
        assert repOk() : "Invariante violato dopo rimuoviCampoSpecifico";
        return rimosso;
    }

    /**
     * Modifica l'obbligatorietà di un campo specifico.
     *
     * @param nomeCampo nome del campo da modificare
     * @param obbligatorio nuovo valore
     * @return true se la modifica è avvenuta, false se il campo non esiste
     */
    public boolean modificaObbligatorietaCampoSpecifico(String nomeCampo, boolean obbligatorio) {
        for (Campo c : campiSpecifici) {
            if (c.getNome().equalsIgnoreCase(nomeCampo)) {
                c.setObbligatorio(obbligatorio);
                return true;
            }
        }
        return false;
    }

    /**
     * Verifica se esiste un campo specifico con il nome dato.
     *
     * @param nomeCampo nome da cercare
     * @return true se esiste
     */
    public boolean contieneCampo(String nomeCampo) {
        if (nomeCampo == null) {
            return false;
        }
        return campiSpecifici.stream().anyMatch(c -> c.getNome().equalsIgnoreCase(nomeCampo));
    }

    /**
     * Restituisce il campo specifico con il nome dato, o null se assente.
     *
     * @param nomeCampo nome del campo
     * @return Campo trovato o null
     */
    public Campo getCampoSpecifico(String nomeCampo) {
        if (nomeCampo == null) {
            return null;
        }
        return campiSpecifici.stream()
                .filter(c -> c.getNome().equalsIgnoreCase(nomeCampo))
                .findFirst()
                .orElse(null);
    }

    // ---- Getters ----
    public String getNome() {
        return nome;
    }

    /**
     * @return lista non modificabile dei campi specifici
     */
    public List<Campo> getCampiSpecifici() {
        return Collections.unmodifiableList(campiSpecifici);
    }

    /**
     * Verifica l'invariante di classe.
     */
    public boolean repOk() {
        if (nome == null || nome.isBlank()) {
            return false;
        }
        if (campiSpecifici == null) {
            return false;
        }
        // Nessun duplicato (case-insensitive)
        long distinti = campiSpecifici.stream()
                .map(c -> c.getNome().toLowerCase())
                .distinct()
                .count();
        return distinti == campiSpecifici.size();
    }

    @Override
    public String toString() {
        return "Categoria: " + nome + " (" + campiSpecifici.size() + " campi specifici)";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Categoria)) {
            return false;
        }
        return this.nome.equalsIgnoreCase(((Categoria) obj).nome);
    }

    @Override
    public int hashCode() {
        return nome.toLowerCase().hashCode();
    }
}
