package com.bartekclk.jlfix.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class DeployerSneakMixin {

    @Unique
    private static final String DEPLOYER_FAKE_PLAYER_CLASS =
            "com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer";

    /** Forces Create's Deployer to sneak only when holding a Sophisticated Backpack. */
    @Inject(method = "isShiftKeyDown", at = @At("HEAD"), cancellable = true)
    private void jlfix$forceDeployerSneak(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof FakePlayer fakePlayer
                && fakePlayer instanceof ServerPlayer
                && DEPLOYER_FAKE_PLAYER_CLASS.equals(fakePlayer.getClass().getName())
                && jlfix$isSophisticatedBackpack(fakePlayer.getMainHandItem())) {
            cir.setReturnValue(true);
        }
    }

    /** Checks whether the held item is a backpack from Sophisticated Backpacks. */
    @Unique
    private static boolean jlfix$isSophisticatedBackpack(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null
                && "sophisticatedbackpacks".equals(id.getNamespace())
                && id.getPath().contains("backpack");
    }
}