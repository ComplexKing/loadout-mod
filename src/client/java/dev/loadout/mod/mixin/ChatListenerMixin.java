package dev.loadout.mod.mixin;

import com.mojang.authlib.GameProfile;
import dev.loadout.mod.BadgeRegistry;
import dev.loadout.mod.NameBadges;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The badge on a chat line's sender.
 *
 * <p>Chat carries the sender's name as a Component inside a {@link ChatType.Bound}, and the
 * bound is what the chat type formats the final line from. Replacing the bound with one
 * whose name is decorated therefore reaches every chat format the server might use --
 * ordinary chat, team chat, whispers -- without this mod knowing any of them.
 *
 * <p>The sender's profile is a parameter of the same method, which is the reason to hook
 * here rather than at the formatting itself: by the time the line is assembled the UUID is
 * gone, and matching a badge by display name would be guesswork.
 *
 * <p>The message text is never touched. Only the name is.
 */
@Mixin(ChatListener.class)
public class ChatListenerMixin {

	@ModifyVariable(method = "handlePlayerChatMessage", at = @At("HEAD"), argsOnly = true)
	private ChatType.Bound loadout$badgeInChat(
			ChatType.Bound bound, PlayerChatMessage message, GameProfile sender) {

		if (bound == null || sender == null) {
			return bound;
		}

		return BadgeRegistry.forPlayer(sender.id())
				.map(badge -> new ChatType.Bound(
						bound.chatType(),
						NameBadges.decorate(bound.name(), badge),
						bound.targetName()))
				.orElse(bound);
	}
}
