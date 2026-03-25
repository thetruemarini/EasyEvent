package it.easyevent.v1.model;

import it.easyevent.v1.model.Campo;

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

    /**
     * Costruttore.
     * 
     * @param nome        nome univoco del campo, non null e non blank
     * @param obbligatorio true se la compilazione è obbligatoria
     * @param tipo        tipologia del campo (BASE, COMUNE, SPECIFICO)
     * @throws IllegalArgumentException se nome è null o blank, o tipo è null
     */
    public Campo(String nome, boolean obbligatorio, TipoCampo tipo) {
        // Precondizioni
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Il nome del campo non può essere null o vuoto.");
        }
        if (tipo == null) {
            throw new IllegalArgumentException("Il tipo del campo non può essere null.");
        }
        this.nome = nome.trim();
        this.obbligatorio = obbligatorio;
        this.tipo = tipo;

        // Postcondizione
        assert this.nome != null && !this.nome.isBlank() : "Postcondizione violata: nome non valido dopo costruzione";
        assert this.tipo != null : "Postcondizione violata: tipo null dopo costruzione";
    }

    // ---- Getters ----

    public String getNome() {
        return nome;
    }

    public boolean isObbligatorio() {
        return obbligatorio;
    }

    public TipoCampo getTipo() {
        return tipo;
    }

    // ---- Setters con precondizioni ----

    /**
     * Modifica l'obbligatorietà del campo.
     * Solo i campi COMUNE e SPECIFICO possono essere modificati.
     * 
     * @param obbligatorio nuovo valore di obbligatorietà
     * @throws UnsupportedOperationException se il campo è BASE
     */
    public void setObbligatorio(boolean obbligatorio) {
        if (this.tipo == TipoCampo.BASE) {
            throw new UnsupportedOperationException("I campi BASE sono immutabili.");
        }
        this.obbligatorio = obbligatorio;
    }

    /**
     * Verifica l'invariante di classe.
     */
    public boolean repOk() {
        return nome != null && !nome.isBlank() && tipo != null;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (%s)", tipo, nome, obbligatorio ? "obbligatorio" : "facoltativo");
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Campo)) return false;
        Campo other = (Campo) obj;
        return this.nome.equalsIgnoreCase(other.nome) && this.tipo == other.tipo;
    }

    @Override
    public int hashCode() {
        return nome.toLowerCase().hashCode() + tipo.hashCode();
    }
}
