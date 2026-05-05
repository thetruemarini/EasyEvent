package easyevent.categoria;

/**
 * Rappresenta un campo generico che descrive un'iniziativa. Classe astratta:
 * ogni tipo di campo definisce il proprio comportamento per le operazioni che
 * dipendono dal tipo (es. setObbligatorio).
 *
 * Invariante: nome != null && !nome.isBlank()
 */
public abstract class Campo {

    public enum TipoCampo {
        BASE, COMUNE, SPECIFICO
    }

    private final String nome;
    protected boolean obbligatorio;

    protected Campo(String nome, boolean obbligatorio) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Il nome del campo non puo' essere null o vuoto.");
        }
        this.nome = nome.trim();
        this.obbligatorio = obbligatorio;
    }

    public String getNome() {
        return nome;
    }

    public boolean isObbligatorio() {
        return obbligatorio;
    }

    /**
     * Ogni sottoclasse decide se e come permettere la modifica
     * dell'obbligatorietà. I campi BASE lanciano eccezione, COMUNE e SPECIFICO
     * la consentono.
     */
    public abstract void setObbligatorio(boolean obbligatorio);

    /**
     * Tipo del campo — mantenuto per compatibilità con serializzazione JSON.
     */
    public abstract TipoCampo getTipo();

    public abstract boolean isData();

    public abstract boolean isOra();

    public abstract boolean isInEvidenza();

    public boolean repOk() {
        return nome != null && !nome.isBlank();
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
        return this.nome.equalsIgnoreCase(other.nome) && this.getTipo() == other.getTipo();
    }

    @Override
    public int hashCode() {
        return nome.toLowerCase().hashCode() + getTipo().hashCode();
    }

    // Solo per debug e log infrastrutturale
    @Override
    public String toString() {
        return "Campo{tipo=" + getTipo() + ", nome='" + nome
                + "', obbligatorio=" + obbligatorio + "}";
    }
}
