package dev.loadout.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

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
	 * The mark itself: a glyph in a font this mod ships.
	 *
	 * <p>Still a glyph rather than an image, for the original reason. Drawing a texture
	 * into a name tag means owning its layout -- width, baseline, and the background that
	 * sizes itself to the text -- and every mod that has tried has fought every other mod
	 * doing the same. A glyph is measured and positioned by the same code as the name.
	 *
	 * <p>But a borrowed glyph brings someone else's metrics with it. This started as
	 * U+25C6, which Minecraft renders from a unicode page whose ascent suits the rest of
	 * that page and nothing else -- so the mark sat visibly off the line the letters were
	 * on. Shipping the glyph means declaring the metrics, and badge.json declares the
	 * default font's own: height 8, ascent 7. It now sits where a capital letter would.
	 *
	 * <p>Drawn white with grey shading rather than in colour, so the text colour tints it
	 * and the two faces stay a light and a dark shade of whatever that colour is.
	 */
	private static final String MARK = "\uE000";

	/** The font this mod ships, which is where {@link #MARK} lives. */
	private static final FontDescription FONT = new FontDescription.Resource(
			Identifier.fromNamespaceAndPath(LoadoutMod.MOD_ID, "badge"));

	private NameBadges() {
	}

	/**
	 * @param name the name as it currently stands, after anything else that changed it
	 * @return the name with the badge in front, and a space that belongs to the badge
	 */
	public static Component decorate(Component name, BadgeRegistry.Badge badge) {
		MutableComponent mark = Component.literal(MARK + " ")
				.withStyle(style -> style
						.withFont(FONT)
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
