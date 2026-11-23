package com.bartekclk.jlfix.mixin;

import com.dplayend.justleveling.common.capability.AptitudeCapability;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AptitudeCapability.class)
public class AptitudeCapabilityMixin {

    @Inject(method = "canUseItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void jlfix$allowFakePlayerUseItem(Player player, ItemStack item, CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof FakePlayer) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "isDroppable", at = @At("HEAD"), cancellable = true, remap = false)
    private void jlfix$preventFakePlayerDrop(Player player, ItemStack item, CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof FakePlayer) {
            cir.setReturnValue(false);
        }
    }
}