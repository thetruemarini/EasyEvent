package easyevent.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rappresenta una categoria di iniziative.
 *
 * Invariante di classe: - nome != null && !nome.isBlank() - campiSpecifici !=
 * null - nessun campo in campiSpecifici ha lo stesso nome di un altro
 * (case-insensitive)
 */
public class Categoria {

    private String nome;
    private List<Campo> campiSpecifici;

    public Categoria(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Il nome della categoria non puo' essere null o vuoto.");
        }
        this.nome = nome.trim();
        this.campiSpecifici = new ArrayList<>();
        assert repOk() : "Invariante violato dopo costruzione Categoria";
    }

    public void aggiungiCampoSpecifico(Campo campo) {
        if (campo == null) {
            throw new IllegalArgumentException("Il campo non puo' essere null.");
        }
        if (campo.getTipo() != Campo.TipoCampo.SPECIFICO) {
            throw new IllegalArgumentException("Il campo deve essere di tipo SPECIFICO.");
        }
        if (contieneCampo(campo.getNome())) {
            throw new IllegalArgumentException("Esiste gia' un campo specifico con nome: " + campo.getNome());
        }
        campiSpecifici.add(campo);
        assert repOk() : "Invariante violato dopo aggiungiCampoSpecifico";
    }

    public boolean rimuoviCampoSpecifico(String nomeCampo) {
        if (nomeCampo == null) {
            return false;
        }
        boolean rimosso = campiSpecifici.removeIf(c -> c.getNome().equalsIgnoreCase(nomeCampo));
        assert repOk() : "Invariante violato dopo rimuoviCampoSpecifico";
        return rimosso;
    }

    public boolean modificaObbligatorietaCampoSpecifico(String nomeCampo, boolean obbligatorio) {
        for (Campo c : campiSpecifici) {
            if (c.getNome().equalsIgnoreCase(nomeCampo)) {
                c.setObbligatorio(obbligatorio);
                return true;
            }
        }
        return false;
    }

    public boolean contieneCampo(String nomeCampo) {
        if (nomeCampo == null) {
            return false;
        }
        return campiSpecifici.stream().anyMatch(c -> c.getNome().equalsIgnoreCase(nomeCampo));
    }

    public Campo getCampoSpecifico(String nomeCampo) {
        if (nomeCampo == null) {
            return null;
        }
        return campiSpecifici.stream()
                .filter(c -> c.getNome().equalsIgnoreCase(nomeCampo))
                .findFirst().orElse(null);
    }

    public String getNome() {
        return nome;
    }

    public List<Campo> getCampiSpecifici() {
        return Collections.unmodifiableList(campiSpecifici);
    }

    public boolean repOk() {
        if (nome == null || nome.isBlank() || campiSpecifici == null) {
            return false;
        }
        long distinti = campiSpecifici.stream()
                .map(c -> c.getNome().toLowerCase()).distinct().count();
        return distinti == campiSpecifici.size();
    }

    // solo per debug
    @Override
    public String toString() {
        return "Categoria{nome='" + nome
                + "', campiSpecifici=" + campiSpecifici.size() + "}";
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
