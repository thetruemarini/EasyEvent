package easyevent.model;

/**
 * Value Object per lo username di un utente (configuratore o fruitore).
 *
 * La validazione (non null, non blank) vive qui una volta sola. Il confronto è
 * sempre case-insensitive, perché nel dominio "Mario" e "mario" sono lo stesso
 * utente.
 *
 * Invariante: valore != null && !valore.isBlank()
 */
public final class Username {

    private final String valore;

    public Username(String valore) {
        if (valore == null || valore.isBlank()) {
            throw new IllegalArgumentException(
                    "Lo username non puo' essere null o vuoto.");
        }
        this.valore = valore.trim();
    }

    public String getValore() {
        return valore;
    }

    /**
     * Confronto case-insensitive: regola di dominio, non di implementazione.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Username)) {
            return false;
        }
        return this.valore.equalsIgnoreCase(((Username) obj).valore);
    }

    @Override
    public int hashCode() {
        return valore.toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return valore;
    }
}
