package easyevent.model;

import easyevent.model.exception.ModificaNonConsentitaException;

/**
 * Rappresenta un campo generico che descrive un'iniziativa.
 *
 * Invariante di classe: - nome != null && !nome.isBlank() - tipo != null
 */
public class Campo {

    public enum TipoCampo {
        BASE, COMUNE, SPECIFICO
    }

    private String nome;
    private boolean obbligatorio;
    private TipoCampo tipo;

    public Campo(String nome, boolean obbligatorio, TipoCampo tipo) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Il nome del campo non puo' essere null o vuoto.");
        }
        if (tipo == null) {
            throw new IllegalArgumentException("Il tipo del campo non puo' essere null.");
        }
        this.nome = nome.trim();
        this.obbligatorio = obbligatorio;
        this.tipo = tipo;
        assert repOk() : "Invariante violato dopo costruzione Campo";
    }

    public String getNome() {
        return nome;
    }

    public boolean isObbligatorio() {
        return obbligatorio;
    }

    public TipoCampo getTipo() {
        return tipo;
    }

    public void setObbligatorio(boolean obbligatorio) {
        if (this.tipo == TipoCampo.BASE) {
            throw new ModificaNonConsentitaException(
                    ModificaNonConsentitaException.TipoModifica.CAMPO_BASE_IMMUTABILE,
                    this.nome
            );
        }
        this.obbligatorio = obbligatorio;
    }

    public boolean repOk() {
        return nome != null && !nome.isBlank() && tipo != null;
    }

    // solo per debug e log  
    @Override
    public String toString() {
        return "Campo{tipo=" + tipo + ", nome='" + nome + "', obbligatorio=" + obbligatorio + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Campo)) {
            return false;
        }
        Campo other = (Campo) obj;
        return this.nome.equalsIgnoreCase(other.nome) && this.tipo == other.tipo;
    }

    @Override
    public int hashCode() {
        return nome.toLowerCase().hashCode() + tipo.hashCode();
    }

    /**
     * Determina se il campo rappresenta una data.
     */
    public boolean isData() {
        String lower = this.nome.toLowerCase();
        return lower.contains("data")
                || lower.equals("termine ultimo di iscrizione");
    }

    /**
     * Determina se il campo rappresenta un orario.
     */
    public boolean isOra() {
        return this.nome.equalsIgnoreCase("ora");
    }

    /**
     * Determina se il campo fa parte delle informazioni base "in evidenza" che
     * la bacheca del Fruitore deve mostrare per prime.
     */
    public boolean isInEvidenza() {
        String[] inEvidenza = {
            "data inizio", "ora", "luogo", "quota individuale",
            "data conclusiva", "durata", "note", "compreso nella quota"
        };
        for (String s : inEvidenza) {
            if (s.equalsIgnoreCase(this.nome)) {
                return true;
            }
        }
        return false;
    }
}
