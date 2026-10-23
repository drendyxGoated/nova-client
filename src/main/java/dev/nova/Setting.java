package dev.nova;

public class Setting {
    public final String name;
    public final boolean isBool;
    public double value;
    public final double min, max;

    public Setting(String name, boolean def) {
        this.name = name; this.isBool = true; this.value = def ? 1 : 0; this.min = 0; this.max = 1;
    }
    public Setting(String name, double def, double min, double max) {
        this.name = name; this.isBool = false; this.value = def; this.min = min; this.max = max;
    }
    public boolean on() { return value > 0.5; }
    public double num() { return value; }
    public void flip() { value = on() ? 0 : 1; }
}
