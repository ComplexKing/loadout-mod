package dev.loadout.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Turning a badge into something that can sit beside a name.
 *
 * <p>Composed onto whatever the name already is, never substituted for it. Other mods
 * decorate names too -- Essential is the obvious one -- and the way to coexist with them
 * is to add to what is there rather than to return something built from scratch. Whichever
 * mod runs second then wraps the first's work instead of discarding it, so the order the
 * two happen to load in stops mattering.
 */
public final class NameBadges {
	/**
	 * The mark itself.
	 *
	 * <p>A character rather than an image. Drawing a texture into a name tag means owning
	 * its layout -- width, baseline, and the background that sizes itself to the text --
	 * and every mod that has tried has fought every other mod doing the same. A glyph is
	 * measured and positioned by the same code as the name, so it cannot disagree with it.
	 */
	private static final String MARK = "\u25C6";

	private NameBadges() {
	}

	/**
	 * @param name the name as it currently stands, after anything else that changed it
	 * @return the name with the badge in front, and a space that belongs to the badge
	 */
	public static Component decorate(Component name, BadgeRegistry.Badge badge) {
		MutableComponent mark = Component.literal(MARK + " ")
				.withStyle(style -> style
						.withColor(ChatFormatting.GREEN)
						.withHoverEvent(hoverText(badge)));

		// copy() because a Component handed back by the game may be shared, and appending
		// to it in place would change the name everywhere else it is used.
		return mark.append(name.copy());
	}

	private static net.minecraft.network.chat.HoverEvent hoverText(BadgeRegistry.Badge badge) {
		return new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal(badge.tooltip()));
	}
}
