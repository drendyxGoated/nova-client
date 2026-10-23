package dev.nova.mixin;

import dev.nova.NewModules;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freezes your real player while Freecam is on (the camera flies, you stay put). */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true, require = 0)
    private void nova$freezeForFreecam(CallbackInfo ci) {
        if (NewModules.Freecam.active) ci.cancel();
    }
}
