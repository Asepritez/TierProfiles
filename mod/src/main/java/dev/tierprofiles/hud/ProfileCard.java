package dev.tierprofiles.hud;

import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.core.anim.CardPose;
import dev.tierprofiles.core.anim.Stagger;
import dev.tierprofiles.core.layout.CardGrid;
import dev.tierprofiles.core.layout.CardGrid.Shape;
import dev.tierprofiles.core.layout.CardPlacement;
import dev.tierprofiles.core.logic.CardSummary;
import dev.tierprofiles.core.model.ListProfile;
import dev.tierprofiles.core.model.PlayerProfile;
import dev.tierprofiles.core.model.Tier;
import dev.tierprofiles.core.model.TierEntry;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.model.TimeAgo;
import dev.tierprofiles.core.model.Title;
import dev.tierprofiles.core.settings.CardSettings;
import dev.tierprofiles.core.settings.CardSettings.AnimationStyle;
import dev.tierprofiles.core.settings.Cosmetics;
import dev.tierprofiles.core.settings.Settings;
import dev.tierprofiles.surface.KitIcon;
import dev.tierprofiles.ui.Theme;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The full profile card, drawn on the HUD. Every position comes from CardGrid, which
 * measures the font, so this class only says what goes in which column and can't make
 * text collide. Everything moves by the clock, so the card can glide while the player
 * keeps fighting.
 */
public final class ProfileCard {
    private static final int STRIP_BACKGROUND = 0x30000000;
    private static final int STRIP_LINE = 0xFF2E3338;
    private static final int CARD_COLOR = 0x0C0E10;
    private static final int SLOT_FILL = 0xFF151B21;
    private static final int RETIRED_OUTLINE = 0xFF39424B;
    private static final int PEAK_GOLD = 0xFFFFCF4A;
    private static final int UNRANKED = 0xFF8E98A2;
    private static final float BIG_TEXT = 1.5f;
    private static final float RETIRED_TEXT = 0.72f;
    private static final int MODEL_SIZE = 58;

    private static final long MODEL_START = 220;
    private static final long MODEL_MILLIS = 340;
    private static final float MODEL_SLIDE = 9;
    private static final long ROW_START = 360;
    private static final long ROW_STEP = 85;
    private static final long ROW_MILLIS = 300;
    private static final float ROW_SLIDE = 13;
    private static final long CHIP_START = 60;
    private static final long CHIP_STEP = 40;
    private static final long CHIP_MILLIS = 220;
    private static final float SWAY_DEGREES = 25;
    private static final float RESTING_YAW = -20;

    private static CardGrid grid;

    private ProfileCard() {
    }

    private static CardGrid grid() {
        if (grid == null) {
            Font font = Minecraft.getInstance().font;
            grid = new CardGrid(font::width);
        }
        return grid;
    }

    /** The font can change with a resource pack, so the next card measures it again. */
    public static void forgetMeasurements() {
        grid = null;
    }

    private record Row(TierList list, ListProfile result, List<TierEntry> tiers) {
        boolean hasTiers() {
            return !tiers.isEmpty();
        }
    }

    /** One drawing pass, which saves handing the same values to every method. */
    private record Pass(GuiGraphicsExtractor graphics, Font font, CardGrid grid, Settings settings, Shape shape, float alpha, long elapsed, AnimationStyle style, Cosmetics look, float outerScale) {
        int color(int argb) {
            return Theme.fade(argb, alpha);
        }

        CardSettings card() {
            return settings.card;
        }

        Pass fadedBy(float amount) {
            return new Pass(graphics, font, grid, settings, shape, alpha * amount, elapsed, style, look, outerScale);
        }
    }

    /** Where and how the card sits once fitted to a screen. */
    private record Layout(CardPlacement.Rect rect, Shape shape) {
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

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        Layout layout = layout(settings, rows, screenWidth, screenHeight);

        // You're using the tab list to pick players, so the card must not cover it.
        CardPlacement.Rect rect = layout.rect();
        if (tabRight > rect.x() && rect.y() < belowTab && layout.shape().date()) {
            layout = withoutDate(layout, card, rows.size(), screenWidth, screenHeight);
            rect = layout.rect();
        }
        if (tabRight > rect.x() && rect.y() < belowTab) {
            rect = new CardPlacement.Rect(rect.x(), belowTab, rect.width(), rect.height());
        }

        AnimationStyle style = card.effectiveStyle(TierProfiles.config().global.reduceMotion);
        CardPlacement.Glide glide = CardPlacement.glide(card.anchor, rect, screenWidth, screenHeight);
        CardPose pose = CardPose.of(style, CardState.progress(), glide.dx(), glide.dy());

        Cosmetics look = TierProfiles.cosmeticsFor(uuid);
        Pass pass = new Pass(graphics, Minecraft.getInstance().font, grid(), settings, layout.shape(), pose.alpha(), CardState.millisSinceOpen(), style, look, 1);
        drawPosed(pass, uuid, name, profile, rows, rect, pose, CardState.remaining());
    }

    private static Shape previewShape(Settings settings) {
        CardGrid grid = grid();
        return switch (settings.card.detail) {
            case MINIMAL -> new Shape(CardSettings.Detail.MINIMAL, 0, false, 4, true, 1);
            case STANDARD -> CardGrid.standard();
            case FULL -> new Shape(CardSettings.Detail.FULL, CardGrid.FULL_KITS, true, 0, true, 1);
        };
    }

    public static int previewWidth(Settings settings) {
        return grid().width(previewShape(settings));
    }

    public static int previewHeight(Settings settings) {
        return grid().height(previewShape(settings), rows(settings, null).size());
    }

    /**
     * Draws the card in the settings Live Stage, with staged settings and no entrance.
     *
     * @param outerScale whatever scale the caller's own drawing is already under
     */
    public static void drawPreview(GuiGraphicsExtractor graphics, Settings settings, Cosmetics look, PlayerProfile profile, UUID uuid, String name,
            int x, int y, float scale, float outerScale) {
        Shape base = previewShape(settings);
        Shape shape = new Shape(base.detail(), base.kits(), base.date(), base.lists(), base.model(), scale);
        List<Row> rows = rows(settings, profile);
        CardGrid grid = grid();
        CardPlacement.Rect rect = new CardPlacement.Rect(x, y, Math.round(grid.width(shape) * scale), Math.round(grid.height(shape, rows.size()) * scale));
        long settled = 100_000;
        Pass pass = new Pass(graphics, Minecraft.getInstance().font, grid, settings, shape, 1, settled, AnimationStyle.OFF, look, outerScale);
        drawPosed(pass, uuid, name, profile, rows, rect, CardPose.RESTING, 1);
    }

    private static Layout layout(Settings settings, List<Row> rows, int screenWidth, int screenHeight) {
        CardSettings card = settings.card;
        CardGrid grid = grid();
        int ranked = (int) rows.stream().filter(Row::hasTiers).count();
        int guiScale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());

        Shape shape = grid.fit(card.detail, rows.size(), ranked, card.overlayScale, guiScale,
                screenWidth - CardGrid.SCREEN_ROOM, screenHeight - CardGrid.SCREEN_ROOM,
                candidate -> CardPlacement.isClear(placed(card, grid, candidate, rows.size(), screenWidth, screenHeight), screenWidth, screenHeight));
        return new Layout(placed(card, grid, shape, rows.size(), screenWidth, screenHeight), shape);
    }

    private static Layout withoutDate(Layout layout, CardSettings card, int rows, int screenWidth, int screenHeight) {
        Shape old = layout.shape();
        Shape shape = new Shape(old.detail(), old.kits(), false, old.lists(), old.model(), old.scale());
        return new Layout(placed(card, grid(), shape, rows, screenWidth, screenHeight), shape);
    }

    private static CardPlacement.Rect placed(CardSettings card, CardGrid grid, Shape shape, int rows, int screenWidth, int screenHeight) {
        int width = Math.round(grid.width(shape) * shape.scale());
        int height = Math.round(grid.height(shape, rows) * shape.scale());
        return CardPlacement.place(card.anchor, card.offsetX, card.offsetY, width, height, screenWidth, screenHeight);
    }

    /** Where the card would sit, for the position editor. */
    public static CardPlacement.Rect previewRect(Settings settings, int screenWidth, int screenHeight) {
        List<Row> rows = new ArrayList<>();
        for (TierList list : settings.filters.listPriority) {
            if (settings.filters.isEnabled(list)) {
                rows.add(new Row(list, ListProfile.UNRANKED, List.of()));
            }
        }
        return layout(settings, rows, screenWidth, screenHeight).rect();
    }

    private static List<Row> rows(Settings settings, PlayerProfile profile) {
        List<Row> rows = new ArrayList<>();
        for (TierList list : settings.filters.listPriority) {
            if (settings.filters.isEnabled(list)) {
                ListProfile result = profile == null ? ListProfile.UNRANKED : profile.list(list);
                rows.add(new Row(list, result, tiersOn(settings, result)));
            }
        }
        return rows;
    }

    private static List<TierEntry> tiersOn(Settings settings, ListProfile result) {
        List<TierEntry> entries = new ArrayList<>(result.tiers());
        entries.removeIf(entry -> entry.retired() && !settings.card.showRetired);
        entries.sort(Comparator.comparing(TierEntry::rank, Tier.STRONGEST_FIRST));
        return entries;
    }

    private static void drawPosed(Pass pass, UUID uuid, String name, PlayerProfile profile, List<Row> rows,
            CardPlacement.Rect rect, CardPose pose, float remaining) {
        GuiGraphicsExtractor graphics = pass.graphics();
        Settings settings = pass.settings();
        CardGrid grid = pass.grid();
        Shape shape = pass.shape();

        int width = grid.width(shape);
        int height = grid.height(shape, rows.size());
        float scale = shape.scale() * pose.scale();
        float drawX = rect.x() + pose.offsetX() + rect.width() * (1 - pose.scale()) / 2;
        float drawY = rect.y() + pose.offsetY() + rect.height() * (1 - pose.scale()) / 2;

        graphics.pose().pushMatrix();
        graphics.pose().translate(drawX, drawY);
        graphics.pose().scale(scale, scale);

        graphics.fill(0, 0, width, height, pass.color(Theme.withAlpha(CARD_COLOR, settings.card.overlayOpacity)));
        CardStyle.drawBackground(graphics, pass.look(), width, height, pass.alpha());
        graphics.fill(0, 0, width, CardGrid.STRIP, pass.color(STRIP_BACKGROUND));
        graphics.fill(0, CardGrid.STRIP - 1, width, CardGrid.STRIP, pass.color(STRIP_LINE));

        drawTopStrip(pass, profile, name);
        drawBody(pass, profile, rows);

        CardStyle.drawEffects(graphics, pass.look(), width, height, pass.alpha());
        CardStyle.drawBorder(graphics, pass.look(), width, height, pass.alpha());

        if (remaining < 1) {
            graphics.fill(1, height - 1, 1 + Math.round((width - 2) * remaining), height, pass.color(Theme.withAlpha(Theme.ACCENT, 0.8f)));
        }
        graphics.pose().popMatrix();

        if (shape.model() && pose.alpha() > 0.5f) {
            float outer = pass.outerScale();
            drawModel(pass, uuid, name, drawX * outer, drawY * outer, scale * outer, height);
        }
    }

    /** The model settles in by itself, just after the card arrives. */
    private static void drawModel(Pass pass, UUID uuid, String name, float drawX, float drawY, float scale, int height) {
        CardGrid grid = pass.grid();
        float settle = stagger(pass, MODEL_START, MODEL_MILLIS);
        float slide = (1 - settle) * MODEL_SLIDE;
        int x0 = Math.round(drawX + (CardGrid.MODEL_X - slide) * scale);
        int y0 = Math.round(drawY + 2 * scale);
        int x1 = Math.round(drawX + (CardGrid.MODEL_X + grid.modelWidth - slide) * scale);
        int y1 = Math.round(drawY + (height - 2) * scale);
        int size = Math.round(Math.min(MODEL_SIZE, (height - 8) / 2.05f) * scale);
        Portrait.draw(pass.graphics(), uuid, name, x0, y0, x1, y1, size, modelYaw(pass.card()), pass.look());
    }

    private static float modelYaw(CardSettings card) {
        if (!card.spinModel || TierProfiles.config().global.reduceMotion) {
            return RESTING_YAW;
        }
        return Mth.sin(CardState.now() / 1500f) * SWAY_DEGREES;
    }

    /** Cuts text to fit, so a long name stops at its column rather than running on. */
    private static String fit(Font font, String text, int width) {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("..")) + "..";
    }

    private static void bigText(Pass pass, String text, int x, int y, int color) {
        GuiGraphicsExtractor graphics = pass.graphics();
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(BIG_TEXT, BIG_TEXT);
        graphics.text(pass.font(), text, 0, 0, color);
        graphics.pose().popMatrix();
    }

    private static int bigWidth(Font font, String text) {
        return Math.round(font.width(text) * BIG_TEXT);
    }

    private static void drawTopStrip(Pass pass, PlayerProfile profile, String name) {
        GuiGraphicsExtractor graphics = pass.graphics();
        Font font = pass.font();
        Settings settings = pass.settings();
        CardGrid grid = pass.grid();
        Shape shape = pass.shape();
        int x = grid.contentX(shape);
        int rankX = grid.rankX(shape);
        int room = rankX - x - CardGrid.GAP;

        String shownName = fitBig(font, name, room);
        int nameEnd = x + bigWidth(font, shownName);
        if (pass.look().background.equals("moon") && nameEnd < grid.moonX(shape) - CardStyle.MOON_REACH) {
            CardStyle.drawMoon(graphics, grid.moonX(shape), CardGrid.STRIP / 2, CardGrid.STRIP, grid.width(shape), pass.alpha());
        }

        bigText(pass, shownName, x, 3, pass.color(0xFFFFFFFF));
        if (profile == null) {
            return;
        }

        String region = profile.region(settings.filters.listPriority);
        int regionX = nameEnd + 3;
        if (region != null && regionX + font.width(region) + 2 < x + room) {
            graphics.fill(regionX, 6, regionX + font.width(region) + 3, 16, pass.color(0x402A6A3A));
            graphics.text(font, region, regionX + 2, 7, pass.color(Theme.GOOD));
        }

        Title.Earned title = bestTitle(settings, profile);
        if (title != null && grid.showsTitle(shape)) {
            int badge = TitleBadge.draw(graphics, title.title(), x, 18, pass.alpha());
            String label = font.width(title.label()) <= room - badge ? title.label() : title.title().displayName();
            graphics.text(font, fit(font, label, room - badge), x + badge, 19, pass.color(Theme.BETA));
        }

        drawStats(pass, CardSummary.of(profile, settings.filters));
    }

    private static String fitBig(Font font, String text, int width) {
        return fit(font, text, Math.round(width / BIG_TEXT));
    }

    /** The rank sits on its column, and points end on the shared right edge. */
    private static void drawStats(Pass pass, CardSummary summary) {
        GuiGraphicsExtractor graphics = pass.graphics();
        Font font = pass.font();
        CardGrid grid = pass.grid();
        Shape shape = pass.shape();
        int right = grid.rightEdge(shape);

        if (pass.card().showPoints) {
            String points = Long.toString(summary.totalPoints());
            bigText(pass, points, right - bigWidth(font, points), 3, pass.color(0xFFFFFFFF));
            if (grid.showsPointsLabel(shape)) {
                graphics.text(font, "points", right - font.width("points"), 19, pass.color(Theme.MUTED));
            }
        }

        if (summary.bestPosition() != null) {
            int rankX = grid.rankX(shape);
            String rank = "#" + summary.bestPosition();
            if (!grid.showsRankList(shape)) {
                rankX -= bigWidth(font, rank);
            }
            bigText(pass, rank, rankX, 3, pass.color(Theme.BETA));
            if (grid.showsRankList(shape)) {
                graphics.text(font, summary.bestList().displayName(), rankX, 19, pass.color(Theme.MUTED));
                if (summary.bestList().isBeta()) {
                    graphics.text(font, "beta", rankX + font.width(summary.bestList().displayName() + " "), 19, pass.color(Theme.BETA));
                }
            }
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

    private static void drawBody(Pass pass, PlayerProfile profile, List<Row> rows) {
        if (profile == null) {
            String message = TierProfiles.config().global.lookupsEnabled ? "Looking up..." : "Lookups are off";
            pass.graphics().text(pass.font(), message, pass.grid().contentX(pass.shape()), CardGrid.ROWS_Y + 5, pass.color(Theme.MUTED));
            return;
        }

        switch (pass.shape().detail()) {
            case MINIMAL -> drawMinimal(pass, rows);
            case STANDARD -> {
                int y = CardGrid.ROWS_Y;
                int index = 0;
                for (Row row : rows) {
                    drawStandardRow(pass, row, y, index);
                    y += CardGrid.ROW;
                    index += 1;
                }
            }
            case FULL -> drawFull(pass, rows);
        }
    }

    private static float stagger(Pass pass, long delay, long duration) {
        if (pass.style() == AnimationStyle.OFF) {
            return 1;
        }
        CardSettings card = pass.card();
        return Stagger.progress(pass.elapsed(), card.scaled(delay), card.scaled(duration));
    }

    private static long rowStart(int index) {
        return ROW_START + index * ROW_STEP;
    }

    private static float chipProgress(Pass pass, long rowStart, int chip) {
        return stagger(pass, rowStart + CHIP_START + chip * CHIP_STEP, CHIP_MILLIS);
    }

    /** One line: each ranked list's logo and best tier. */
    private static void drawMinimal(Pass pass, List<Row> rows) {
        CardGrid grid = pass.grid();
        Shape shape = pass.shape();
        int y = CardGrid.ROWS_Y;
        int slot = 0;
        for (Row row : rows) {
            if (!row.hasTiers() || slot >= shape.lists()) {
                continue;
            }
            float progress = stagger(pass, rowStart(slot), ROW_MILLIS);
            if (progress > 0) {
                Pass rowPass = pass.fadedBy(progress);
                Logos.draw(pass.graphics(), row.list(), grid.minimalX(shape, slot), y + 1, CardGrid.ICON, rowPass.alpha());
                drawSlot(rowPass, row.list(), row.tiers().getFirst(), grid.minimalSlotX(shape, slot), y);
            }
            slot += 1;
        }
        if (slot == 0) {
            pass.graphics().text(pass.font(), "Unranked", grid.contentX(shape), y + 5, pass.color(UNRANKED));
        }
    }

    private static void drawStandardRow(Pass pass, Row row, int y, int index) {
        GuiGraphicsExtractor graphics = pass.graphics();
        Font font = pass.font();
        CardGrid grid = pass.grid();
        long start = rowStart(index);
        float progress = stagger(pass, start, ROW_MILLIS);
        if (progress <= 0) {
            return;
        }

        Pass rowPass = pass.fadedBy(progress);
        graphics.pose().pushMatrix();
        graphics.pose().translate((1 - progress) * ROW_SLIDE, 0);

        Logos.draw(graphics, row.list(), grid.listX, y + 1, CardGrid.ICON, rowPass.alpha());
        graphics.text(font, row.list().displayName(), grid.nameX, y + 5, rowPass.color(Theme.TEXT));
        if (row.list().isBeta()) {
            graphics.text(font, "beta", grid.nameX + font.width(row.list().displayName() + " "), y + 5, rowPass.color(Theme.BETA));
        }

        if (row.hasTiers()) {
            int slots = Math.min(pass.shape().kits(), row.tiers().size());
            for (int slot = 0; slot < slots; slot++) {
                float chip = chipProgress(pass, start, slot);
                if (chip > 0) {
                    drawSlot(pass.fadedBy(chip), row.list(), row.tiers().get(slot), grid.kitX(slot), y);
                }
            }
            drawRowDate(rowPass, row, y);
        } else {
            graphics.text(font, "Unranked", grid.kitX(0), y + 5, rowPass.color(UNRANKED));
        }
        graphics.pose().popMatrix();
    }

    /** The date belongs to the row's best tier, which is the first slot. */
    private static void drawRowDate(Pass pass, Row row, int y) {
        Long date = row.tiers().getFirst().attained();
        if (pass.shape().date() && pass.card().showAttained && date != null) {
            String text = TimeAgo.date(date);
            int right = pass.grid().rightEdge(pass.shape());
            pass.graphics().text(pass.font(), text, right - pass.font().width(text), y + 5, pass.color(Theme.MUTED));
        }
    }

    /** Five kits per list, each with its own date beneath. */
    private static void drawFull(Pass pass, List<Row> rows) {
        GuiGraphicsExtractor graphics = pass.graphics();
        Font font = pass.font();
        CardGrid grid = pass.grid();
        int y = CardGrid.ROWS_Y;
        int index = 0;
        for (Row row : rows) {
            long start = rowStart(index);
            float progress = stagger(pass, start, ROW_MILLIS);
            index += 1;
            if (progress > 0) {
                Pass rowPass = pass.fadedBy(progress);
                Logos.draw(graphics, row.list(), grid.listX, y + 1, CardGrid.ICON, rowPass.alpha());
                graphics.text(font, row.list().displayName(), grid.nameX, y + 1, rowPass.color(Theme.TEXT));
                if (row.list().isBeta()) {
                    graphics.text(font, "beta", grid.nameX, y + 11, rowPass.color(Theme.BETA));
                }
                if (row.hasTiers()) {
                    int slots = Math.min(CardGrid.FULL_KITS, row.tiers().size());
                    for (int slot = 0; slot < slots; slot++) {
                        TierEntry entry = row.tiers().get(slot);
                        float chip = chipProgress(pass, start, slot);
                        if (chip > 0) {
                            Pass chipPass = pass.fadedBy(chip);
                            int x = grid.fullKitX(slot);
                            drawSlot(chipPass, row.list(), entry, x, y);
                            String date = entry.attained() == null ? "No date" : TimeAgo.monthYear(entry.attained());
                            graphics.text(font, date, x, y + CardGrid.SLOT_HEIGHT + 2, chipPass.color(Theme.MUTED));
                        }
                    }
                } else {
                    graphics.text(font, "Unranked", grid.fullKitX(0), y + 5, rowPass.color(UNRANKED));
                }
            }
            y += grid.fullRow();
        }

        Component legend = Component.literal("Gold corner: peak was higher    Outline: retired    Date: when achieved");
        graphics.text(font, legend, grid.listX, y + 2, pass.color(Theme.MUTED));
    }

    /** Icon and tier as one shape. Retired and peak live inside it and never change its size. */
    private static void drawSlot(Pass pass, TierList list, TierEntry entry, int x, int y) {
        GuiGraphicsExtractor graphics = pass.graphics();
        Font font = pass.font();
        CardGrid grid = pass.grid();
        int width = grid.slotWidth;
        int rgb = pass.settings().style.color(list, entry.rank());

        if (entry.retired()) {
            graphics.outline(x, y, width, CardGrid.SLOT_HEIGHT, pass.color(RETIRED_OUTLINE));
        } else {
            graphics.fill(x, y, x + width, y + CardGrid.SLOT_HEIGHT, pass.color(SLOT_FILL));
        }

        KitIcon.draw(graphics, entry.kit(), x + 1, y + 1, pass.settings().style.iconSet, pass.alpha());

        String label = entry.rank().label();
        int chipX = x + width - grid.chipWidth + (grid.chipWidth - font.width(label) + 1) / 2;
        int color = entry.retired() ? Theme.withAlpha(rgb, RETIRED_TEXT) : Theme.opaque(rgb);
        graphics.text(font, label, chipX, y + 5, pass.color(color), false);

        if (pass.card().showPeak && entry.peak() != null && !entry.rank().isAtLeast(entry.peak())) {
            drawPeakCorner(pass, x + width, y);
        }
    }

    /** A small stepped gold corner, drawn inside the slot's top right. */
    private static void drawPeakCorner(Pass pass, int right, int top) {
        GuiGraphicsExtractor graphics = pass.graphics();
        for (int step = 0; step < 4; step++) {
            graphics.fill(right - 4 + step, top + step, right, top + step + 1, pass.color(PEAK_GOLD));
        }
    }
}
