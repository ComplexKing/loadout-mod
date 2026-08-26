package dev.loadout.mod.mixin;

import dev.loadout.mod.BadgeRegistry;
import dev.loadout.mod.NameBadges;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the badge to a player's name.
 *
 * <p>Injected into getNameTag rather than into the drawing, because in 26.2 the name
 * travels through the render state as a Component and the drawing simply lays out
 * whatever it finds there. Composing the component is therefore both simpler and far more
 * compatible than intercepting a draw call: anything else that decorates names is working
 * on the same value, so the two additions stack instead of one winning.
 *
 * <p>RETURN rather than HEAD, and composing the existing value rather than replacing it,
 * for the same reason -- a mod that ran before this one has already had its say, and its
 * work is carried through.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
	private void loadout$addBadge(Entity entity, CallbackInfoReturnable<Component> info) {
		Component existing = info.getReturnValue();

		// Null when the entity has no name to show at all, which is most entities most of
		// the time. Nothing to decorate, and no reason to invent one.
		if (existing == null || !(entity instanceof Player player)) {
			return;
		}

		BadgeRegistry.forPlayer(player.getUUID())
				.ifPresent(badge -> info.setReturnValue(NameBadges.decorate(existing, badge)));
	}
}
