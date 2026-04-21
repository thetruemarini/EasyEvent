package easyevent.model;
/**
 * Rappresenta un campo generico che descrive un'iniziativa.
 *
 * Invariante di classe:
 * - nome != null && !nome.isBlank()
 * - tipo != null
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
            throw new UnsupportedOperationException("I campi BASE sono immutabili.");
        }
        this.obbligatorio = obbligatorio;
    }

    public boolean repOk() {
        return nome != null && !nome.isBlank() && tipo != null;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (%s)", tipo, nome, obbligatorio ? "obbligatorio" : "facoltativo");
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
}
