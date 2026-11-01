package dev.tierprofiles.core.anim;

import dev.tierprofiles.core.settings.CardSettings.AnimationStyle;

/**
 * Where a card is during its entrance or exit.
 *
 * @param offsetX pixels away from its resting place
 * @param scale   size multiplier
 * @param alpha   opacity from 0 to 1
 */
public record CardPose(float offsetX, float scale, float alpha) {
    public static final CardPose RESTING = new CardPose(0, 1, 1);

    private static final float SMALLEST_SCALE = 0.85f;
    private static final float GLIDE_FADE_SPEED = 1.5f;

    /**
     * @param progress how open the card is, already eased
     * @param distance how far a glide travels. Its sign chooses the direction.
     */
    public static CardPose of(AnimationStyle style, float progress, float distance) {
        return switch (style) {
            case GLIDE -> new CardPose((1 - progress) * distance, 1, Math.min(1, progress * GLIDE_FADE_SPEED));
            case FADE -> new CardPose(0, 1, progress);
            case SCALE -> new CardPose(0, SMALLEST_SCALE + (1 - SMALLEST_SCALE) * progress, progress);
            case OFF -> RESTING;
        };
    }
}
