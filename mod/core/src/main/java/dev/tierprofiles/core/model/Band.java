package dev.tierprofiles.core.model;

/** The position inside a tier level, strongest first. Only McPvP uses MT and ST. */
public enum Band {
    HT("High"),
    MT("Mid"),
    LT("Low"),
    ST("Sub");

    private final String longName;

    Band(String longName) {
        this.longName = longName;
    }

    public String longName() {
        return longName;
    }
}
