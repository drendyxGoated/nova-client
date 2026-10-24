package dev.nova;

public class Setting {
    public final String name;
    public final boolean isBool;
    public final String[] modes;
    public double value;
    public final double min, max;
    public double step;

    public Setting(String name, boolean def) {
        this.name = name; this.isBool = true; this.modes = null;
        this.value = def ? 1 : 0; this.min = 0; this.max = 1; this.step = 1;
    }
    public Setting(String name, double def, double min, double max) {
        this.name = name; this.isBool = false; this.modes = null;
        this.value = def; this.min = min; this.max = max; this.step = (max - min) / 100.0;
    }
    public Setting(String name, int def, String... modes) {
        this.name = name; this.isBool = false; this.modes = modes;
        this.value = def; this.min = 0; this.max = modes.length - 1; this.step = 1;
    }

    public boolean isMode() { return modes != null; }
    public int mode() { return (int) value; }
    public String modeName() { return modes[Math.max(0, Math.min(modes.length - 1, (int) value))]; }
    public void cycle() { value = ((int) value + 1) % modes.length; }
    public boolean on() { return value > 0.5; }
    public double num() { return value; }
    public void flip() { value = on() ? 0 : 1; }
}
