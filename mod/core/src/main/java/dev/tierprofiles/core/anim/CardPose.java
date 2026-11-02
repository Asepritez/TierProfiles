package dev.tierprofiles.core.anim;

import dev.tierprofiles.core.settings.CardSettings.AnimationStyle;

/**
 * Where a card is during its entrance or exit.
 *
 * @param offsetX pixels sideways from its resting place
 * @param offsetY pixels up or down from its resting place
 * @param scale   size multiplier
 * @param alpha   opacity from 0 to 1
 */
public record CardPose(float offsetX, float offsetY, float scale, float alpha) {
    public static final CardPose RESTING = new CardPose(0, 0, 1, 1);

    private static final float SMALLEST_SCALE = 0.85f;
    private static final float GLIDE_FADE_SPEED = 1.5f;

    /**
     * @param progress how open the card is, already eased
     * @param dx       how far sideways a glide travels. Its sign chooses the direction.
     * @param dy       the same, up or down
     */
    public static CardPose of(AnimationStyle style, float progress, float dx, float dy) {
        return switch (style) {
            case GLIDE -> new CardPose((1 - progress) * dx, (1 - progress) * dy, 1, Math.min(1, progress * GLIDE_FADE_SPEED));
            case FADE -> new CardPose(0, 0, 1, progress);
            case SCALE -> new CardPose(0, 0, SMALLEST_SCALE + (1 - SMALLEST_SCALE) * progress, progress);
            case OFF -> RESTING;
        };
    }
}
