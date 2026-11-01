package dev.tierprofiles.hud;

import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.core.anim.CardPose;
import dev.tierprofiles.core.anim.Stagger;
import dev.tierprofiles.core.logic.CardSummary;
import dev.tierprofiles.core.model.ListProfile;
import dev.tierprofiles.core.model.ListStatus;
import dev.tierprofiles.core.model.PlayerProfile;
import dev.tierprofiles.core.model.Tier;
import dev.tierprofiles.core.model.TierEntry;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.model.TimeAgo;
import dev.tierprofiles.core.model.Title;
import dev.tierprofiles.core.settings.CardSettings;
import dev.tierprofiles.core.settings.CardSettings.AnimationStyle;
import dev.tierprofiles.core.settings.CardSettings.Detail;
import dev.tierprofiles.core.settings.Settings;
import dev.tierprofiles.surface.Icons;
import dev.tierprofiles.ui.Theme;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * The full profile card, drawn on the HUD. The default look is deliberately quiet:
 * a top strip with the player, their best rank and total points, then one row per
 * list. "Card detail" decides how much each row says. Everything moves by the
 * clock, so the card can glide while the player keeps fighting.
 */
public final class ProfileCard {
    public static final int WIDTH = 244;

    private static final int MARGIN = 4;
    private static final int MODEL_WIDTH = 40;
    private static final int TOP_STRIP = 24;
    private static final int ROW_HEIGHT = 12;
    private static final int BOTTOM_PADDING = 3;
    private static final int LIST_NAME_WIDTH = 52;
    private static final int ICON_WIDTH = 10;
    private static final int TIER_WIDTH = 18;
    private static final int RIGHT_EDGE = WIDTH - 4;
    private static final int STAT_CELL_RANK = 74;
    private static final int STAT_CELL_POINTS = 38;
    private static final int MODEL_SIZE = 35;
    private static final int LOADING_HEIGHT = TOP_STRIP + ROW_HEIGHT + BOTTOM_PADDING;
    private static final int CHIP_FILL_ALPHA = 0x26;
    private static final int STAT_CELL = 0x40000000;

    private static final long ROW_DELAY = 45;
    private static final long CHIP_DELAY = 22;
    private static final long ITEM_MILLIS = 200;
    private static final float ROW_SLIDE = 7;
    private static final float SWAY_DEGREES = 25;
    private static final float RESTING_YAW = -20;
    /** The card always stays above this far past the middle, clear of the crosshair. */
    private static final int CROSSHAIR_CLEARANCE = 16;

    private ProfileCard() {
    }

    private record Row(TierList list, ListProfile result, List<List<TierEntry>> lines) {
        int height() {
            return Math.max(1, lines.size()) * ROW_HEIGHT;
        }
    }

    /** One drawing pass, which saves handing the same values to every method. */
    private record Pass(GuiGraphicsExtractor graphics, Font font, Settings settings, float alpha, long elapsed, AnimationStyle style) {
        int color(int argb) {
            return Theme.fade(argb, alpha);
        }

        CardSettings card() {
            return settings.card;
        }

        Pass fadedBy(float amount) {
            return new Pass(graphics, font, settings, alpha * amount, elapsed, style);
        }
    }

    /**
     * @param tabRight how far right the tab list reaches, or 0 when it isn't showing
     * @param belowTab the first free y below the tab list
     */
    public static void draw(GuiGraphicsExtractor graphics, UUID uuid, String name, int tabRight, int belowTab) {
        Settings settings = TierProfiles.settings();
        CardSettings card = settings.card;
        PlayerProfile profile = TierProfiles.profiles().profile(uuid);
        List<Row> rows = rows(settings, profile);
        int height = height(rows);

        float scale = fitScale(card.overlayScale, height, graphics.guiWidth(), graphics.guiHeight());
        int width = Math.round(WIDTH * scale);
        int x = switch (card.overlayAnchor) {
            case LEFT -> MARGIN;
            case CENTER -> (graphics.guiWidth() - width) / 2;
            case RIGHT -> graphics.guiWidth() - width - MARGIN;
        };
        // You're using the tab list to pick players, so the card must not cover it.
        int y = tabRight > x ? belowTab : MARGIN;

        AnimationStyle style = card.effectiveStyle(TierProfiles.config().global.reduceMotion);
        float travel = card.overlayAnchor == CardSettings.OverlayAnchor.LEFT ? -(x + width) : graphics.guiWidth() - x;
        CardPose pose = CardPose.of(style, CardState.progress(), travel);

        drawPosed(graphics, uuid, name, profile, rows, height, x, y, scale, pose, style, CardState.millisSinceOpen(), CardState.remaining());
    }

    /** Shrinks the card if it would otherwise reach the crosshair or leave the screen. */
    static float fitScale(float wanted, int height, int screenWidth, int screenHeight) {
        float widest = (screenWidth - 2f * MARGIN) / WIDTH;
        float tallest = (screenHeight / 2f - CROSSHAIR_CLEARANCE - MARGIN) / height;
        return Math.min(wanted, Math.min(widest, tallest));
    }

    private static int height(List<Row> rows) {
        int height = TOP_STRIP + BOTTOM_PADDING;
        for (Row row : rows) {
            height += row.height();
        }
        return rows.isEmpty() ? LOADING_HEIGHT : height;
    }

    private static List<Row> rows(Settings settings, PlayerProfile profile) {
        List<Row> rows = new ArrayList<>();
        if (profile == null) {
            return rows;
        }

        Detail detail = settings.card.detail;
        for (TierList list : settings.filters.listPriority) {
            if (!settings.filters.isEnabled(list)) {
                continue;
            }
            ListProfile result = profile.list(list);
            if (result.status() == ListStatus.UNAVAILABLE && !settings.card.showUnavailableLists) {
                continue;
            }
            if (result.status() == ListStatus.UNRANKED && detail == Detail.MINIMAL) {
                continue;
            }
            rows.add(new Row(list, result, lines(settings, result, detail)));
        }
        return rows;
    }

    private static List<List<TierEntry>> lines(Settings settings, ListProfile result, Detail detail) {
        List<TierEntry> entries = new ArrayList<>(result.tiers());
        entries.removeIf(entry -> entry.retired() && !settings.card.showRetired);
        entries.sort(Comparator.comparing(TierEntry::rank, Tier.STRONGEST_FIRST));

        int perLine = chipsPerLine(detail);
        int wanted = detail == Detail.FULL ? entries.size() : Math.min(perLine, entries.size());
        List<List<TierEntry>> lines = new ArrayList<>();
        for (int start = 0; start < wanted; start += perLine) {
            lines.add(entries.subList(start, Math.min(start + perLine, wanted)));
        }
        return lines;
    }

    private static int chipsPerLine(Detail detail) {
        return detail == Detail.STANDARD ? 3 : 4;
    }

    private static int chipStep(Detail detail) {
        return detail == Detail.FULL ? 38 : 31;
    }

    private static void drawPosed(
            GuiGraphicsExtractor graphics,
            UUID uuid,
            String name,
            PlayerProfile profile,
            List<Row> rows,
            int height,
            int x,
            int y,
            float scale,
            CardPose pose,
            AnimationStyle style,
            long elapsed,
            float remaining) {
        CardSettings card = TierProfiles.settings().card;
        Pass pass = new Pass(graphics, Minecraft.getInstance().font, TierProfiles.settings(), pose.alpha(), elapsed, style);

        float shownScale = scale * pose.scale();
        float drawX = x + pose.offsetX() + WIDTH * scale * (1 - pose.scale()) / 2;
        float drawY = y + height * scale * (1 - pose.scale()) / 2;

        graphics.pose().pushMatrix();
        graphics.pose().translate(drawX, drawY);
        graphics.pose().scale(shownScale, shownScale);

        graphics.fill(0, 0, WIDTH, height, pass.color(Theme.withAlpha(0x0C0E10, card.overlayOpacity)));
        graphics.outline(0, 0, WIDTH, height, pass.color(0xFF2E3338));

        int left = card.showModel ? MODEL_WIDTH : 3;
        drawTopStrip(pass, profile, name, left);
        drawRows(pass, profile, rows, left + 2);

        if (remaining < 1) {
            graphics.fill(1, height - 1, 1 + Math.round((WIDTH - 2) * remaining), height, pass.color(Theme.withAlpha(Theme.ACCENT, 0.8f)));
        }
        graphics.pose().popMatrix();

        if (card.showModel && pose.alpha() > 0.5f) {
            int x0 = Math.round(drawX + 1 * shownScale);
            int y0 = Math.round(drawY + 2 * shownScale);
            int x1 = Math.round(drawX + (MODEL_WIDTH - 1) * shownScale);
            int y1 = Math.round(drawY + (height - 2) * shownScale);
            int size = Math.round(Math.min(MODEL_SIZE, (height - 6) / 2f) * shownScale);
            Portrait.draw(graphics, uuid, name, x0, y0, x1, y1, size, modelYaw(card));
        }
    }

    private static float modelYaw(CardSettings card) {
        if (!card.spinModel || TierProfiles.config().global.reduceMotion) {
            return RESTING_YAW;
        }
        return Mth.sin(CardState.now() / 1500f) * SWAY_DEGREES;
    }

    private static void drawTopStrip(Pass pass, PlayerProfile profile, String name, int left) {
        GuiGraphicsExtractor graphics = pass.graphics();
        Font font = pass.font();
        Settings settings = pass.settings();
        int x = left + 2;

        graphics.text(font, name, x, 3, pass.color(0xFFFFFFFF));
        if (profile == null) {
            return;
        }

        String region = profile.region(settings.filters.listPriority);
        if (region != null) {
            int regionX = x + font.width(name) + 4;
            graphics.fill(regionX - 1, 2, regionX + font.width(region) + 1, 12, pass.color(0x402A6A3A));
            graphics.text(font, region, regionX, 3, pass.color(Theme.GOOD));
        }

        Title.Earned title = bestTitle(settings, profile);
        if (title != null) {
            graphics.text(font, title.label(), x, 13, pass.color(Theme.BETA));
        }
        drawStats(pass, CardSummary.of(profile, settings.filters));
    }

    /** Two small cells at the top right: best position, then total points. */
    private static void drawStats(Pass pass, CardSummary summary) {
        GuiGraphicsExtractor graphics = pass.graphics();
        Font font = pass.font();
        int pointsX = RIGHT_EDGE - STAT_CELL_POINTS;
        int rankX = pointsX - STAT_CELL_RANK;

        if (summary.bestPosition() != null) {
            graphics.fill(rankX, 1, pointsX - 1, TOP_STRIP - 1, pass.color(STAT_CELL));
            graphics.text(font, "#" + summary.bestPosition(), rankX + 3, 3, pass.color(Theme.BETA));
            int listX = rankX + 3;
            graphics.text(font, summary.bestList().displayName(), listX, 13, pass.color(Theme.MUTED));
            if (summary.bestList().isBeta()) {
                graphics.text(font, "beta", listX + font.width(summary.bestList().displayName()) + 3, 13, pass.color(Theme.BETA));
            }
        }

        if (pass.card().showPoints) {
            graphics.fill(pointsX, 1, RIGHT_EDGE + 2, TOP_STRIP - 1, pass.color(STAT_CELL));
            graphics.text(font, Long.toString(summary.totalPoints()), pointsX + 3, 3, pass.color(0xFFFFFFFF));
            graphics.text(font, "points", pointsX + 3, 13, pass.color(Theme.MUTED));
        }
    }

    private static Title.Earned bestTitle(Settings settings, PlayerProfile profile) {
        for (TierList list : settings.filters.listPriority) {
            Title.Earned title = Title.of(list, profile.list(list));
            if (title != null) {
                return title;
            }
        }
        return null;
    }

    private static void drawRows(Pass pass, PlayerProfile profile, List<Row> rows, int x) {
        int y = TOP_STRIP + 1;
        if (profile == null) {
            String message = TierProfiles.config().global.lookupsEnabled ? "Looking up..." : "Lookups are off";
            pass.graphics().text(pass.font(), message, x, y, pass.color(Theme.MUTED));
            return;
        }

        int index = 0;
        for (Row row : rows) {
            drawRow(pass, row, x, y, index);
            y += row.height();
            index += 1;
        }
    }

    private static float stagger(Pass pass, long delay) {
        if (pass.style() == AnimationStyle.OFF) {
            return 1;
        }
        CardSettings card = pass.card();
        return Stagger.progress(pass.elapsed(), card.scaled(delay), card.scaled(ITEM_MILLIS));
    }

    private static void drawRow(Pass pass, Row row, int x, int y, int index) {
        GuiGraphicsExtractor graphics = pass.graphics();
        Font font = pass.font();
        long rowDelay = CardState.OPEN_DELAY_FOR_ROWS + index * ROW_DELAY;
        float progress = stagger(pass, rowDelay);
        if (progress <= 0) {
            return;
        }

        Pass rowPass = pass.fadedBy(progress);
        graphics.pose().pushMatrix();
        graphics.pose().translate((1 - progress) * ROW_SLIDE, 0);

        graphics.text(font, Icons.list(row.list()), x, y, rowPass.color(0xFFFFFFFF));
        int nameX = x + ICON_WIDTH + 1;
        graphics.text(font, row.list().displayName(), nameX, y, rowPass.color(Theme.TEXT));

        int contentX = x + LIST_NAME_WIDTH;
        if (row.list().isBeta()) {
            contentX += font.width(" beta");
            graphics.text(font, "beta", nameX + font.width(row.list().displayName()) + 3, y, rowPass.color(Theme.BETA));
        }

        switch (row.result().status()) {
            case OK -> {
                drawChips(pass, row, contentX, y, rowDelay);
                drawDate(rowPass, row, y);
            }
            case UNRANKED -> graphics.text(font, "Not ranked", contentX, y, rowPass.color(Theme.MUTED));
            case UNAVAILABLE -> graphics.text(font, "Offline", contentX, y, rowPass.color(Theme.MUTED));
        }
        graphics.pose().popMatrix();
    }

    /** Standard detail shows one date per row, on the right. */
    private static void drawDate(Pass pass, Row row, int y) {
        if (pass.card().detail != Detail.STANDARD || !pass.card().showAttained) {
            return;
        }
        Long date = CardSummary.latestDate(row.result());
        if (date != null) {
            String text = TimeAgo.date(date);
            pass.graphics().text(pass.font(), text, RIGHT_EDGE - pass.font().width(text), y, pass.color(Theme.MUTED));
        }
    }

    private static void drawChips(Pass pass, Row row, int x, int y, long rowDelay) {
        Detail detail = pass.card().detail;
        int step = chipStep(detail);
        long now = System.currentTimeMillis() / 1000;

        int chip = 0;
        for (int line = 0; line < row.lines().size(); line++) {
            int chipX = x;
            for (TierEntry entry : row.lines().get(line)) {
                float progress = stagger(pass, rowDelay + chip * CHIP_DELAY);
                if (progress > 0) {
                    Pass chipPass = pass.fadedBy(progress);
                    int chipY = y + line * ROW_HEIGHT;
                    pass.graphics().text(pass.font(), Icons.kit(entry.kit(), pass.settings().style.iconSet), chipX, chipY, chipPass.color(0xFFFFFFFF));
                    drawTier(chipPass, row.list(), entry, chipX + ICON_WIDTH, chipY);
                    if (detail == Detail.FULL && pass.card().showAttained && entry.attained() != null) {
                        drawSmallText(chipPass, TimeAgo.compact(entry.attained(), now), chipX + ICON_WIDTH + TIER_WIDTH, chipY + 1);
                    }
                }
                chipX += step;
                chip += 1;
            }
        }
    }

    private static void drawSmallText(Pass pass, String text, int x, int y) {
        GuiGraphicsExtractor graphics = pass.graphics();
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(0.75f, 0.75f);
        graphics.text(pass.font(), text, 0, 0, pass.color(Theme.MUTED), false);
        graphics.pose().popMatrix();
    }

    /** Coloured text on a faint tint. Retired tiers are dimmed and marked R. */
    private static void drawTier(Pass pass, TierList list, TierEntry entry, int x, int y) {
        GuiGraphicsExtractor graphics = pass.graphics();
        Font font = pass.font();
        Settings settings = pass.settings();
        int rgb = settings.style.color(list, entry.rank());
        String label = entry.rank().label();

        if (entry.retired()) {
            int dim = pass.color(Theme.withAlpha(rgb, 0.6f));
            graphics.text(font, label + "R", x, y, dim, false);
        } else {
            int width = font.width(label) + 1;
            graphics.fill(x - 1, y - 1, x + width, y + 8, pass.color(CHIP_FILL_ALPHA << 24 | rgb));
            graphics.text(font, label, x, y, pass.color(Theme.opaque(rgb)), false);
        }

        if (pass.card().showPeak && entry.peak() != null && !entry.rank().isAtLeast(entry.peak())) {
            graphics.fill(x + font.width(label) + (entry.retired() ? 6 : 1), y, x + font.width(label) + (entry.retired() ? 8 : 3), y + 2, pass.color(Theme.TEXT));
        }
    }
}
