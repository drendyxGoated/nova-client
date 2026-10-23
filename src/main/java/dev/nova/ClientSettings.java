package dev.nova;

/** The "Settings" tab: theme + HUD customization. Not a real module (no on/off). */
public class ClientSettings extends Module {
    final Setting theme = mode("Theme", 0, "Blue", "Purple", "Pink", "Red", "Orange", "Green", "Cyan", "Rainbow", "Custom");
    final Setting hue = num("Custom hue", 210, 0, 360);
    final Setting opacity = num("GUI opacity", 92, 40, 100);
    final Setting radius = num("Corner radius", 3, 0, 6);
    final Setting rowSize = mode("Row size", 1, "Compact", "Normal", "Large");
    final Setting anims = bool("Animations", true);
    final Setting dim = bool("Dim background", true);
    final Setting notifs = bool("Notifications", true);
    final Setting watermark = bool("Watermark", true);
    final Setting arrayOn = bool("Module list", true);
    final Setting arraySide = mode("List side", 0, "Right", "Left");
    final Setting arrayColor = mode("List color", 0, "Accent", "Rainbow", "White");
    final Setting arrayBg = bool("List background", true);

    ClientSettings() {
        super("Client", "Settings");
        hidden = true;
        persistEnabled = false;
    }
}
