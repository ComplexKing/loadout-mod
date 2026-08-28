package dev.loadout.mod.mixin;

import dev.loadout.mod.BadgeRegistry;
import dev.loadout.mod.NameBadges;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The badge in the player list.
 *
 * <p>Same seam as the name tag above a head: at the return of the method that turns a
 * player into a Component, so whatever the server, a team colour, or another mod has
 * already made of the name is what gets decorated rather than replaced.
 *
 * <p>{@code getNameForDisplay} is the one place the tab list builds a name -- the header's
 * comma-separated list of players calls it too, so both pick the badge up from here.
 */
@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {

	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
	private void loadout$badgeInTabList(PlayerInfo info, CallbackInfoReturnable<Component> callback) {
		Component name = callback.getReturnValue();
		if (name == null || info == null) {
			return;
		}

		BadgeRegistry.forPlayer(info.getProfile().id())
				.ifPresent(badge -> callback.setReturnValue(NameBadges.decorate(name, badge)));
	}
}
