package dev.undrrwrldd.customshields.mixin;

import dev.undrrwrldd.customshields.client.ShieldAtlasController;
import net.minecraft.client.render.item.model.special.ShieldModelRenderer;
import net.minecraft.client.texture.SpriteHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShieldModelRenderer.class)
public abstract class ShieldModelRendererCaptureMixin {

    @Shadow @Final private SpriteHolder spriteHolder;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void customshields$captureSprite(CallbackInfo ci) {
        ShieldAtlasController.get().captureFrom(this.spriteHolder);
    }
}
