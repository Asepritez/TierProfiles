package dev.tierprofiles.hud;

import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.net.ProfileService;
import dev.tierprofiles.core.settings.Global;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Official list logos, downloaded once from each list's own website and kept in the
 * config folder. They're never in the mod itself. Every failure is quiet, because the
 * neutral letter mark is a perfectly good fallback.
 */
public final class LogoStore {
    /** Big enough to stay sharp on a large card, small enough to stay cheap. */
    static final int SIZE = 64;
    private static final long REFRESH_MILLIS = 30L * 24 * 60 * 60 * 1000;
    private static final long TOO_BIG_BYTES = 8 * 1024 * 1024;

    /** SubTiers only offers WebP, which Java can't read, so it keeps its letter mark. */
    private static final Map<TierList, String> SOURCES = new EnumMap<>(Map.of(
            TierList.MCTIERS, "https://mctiers.gg/icon.png",
            TierList.MCPVP, "https://www.mcpvp.com/apple-touch-icon.png",
            TierList.PVPTIERS, "https://pvptiers.com/hex-logo.png",
            TierList.PVPHQ, "https://pvphq.com/assets/pvphq-logo.png"));

    private static final Set<TierList> ready = java.util.Collections.synchronizedSet(EnumSet.noneOf(TierList.class));
    private static volatile boolean started;

    private LogoStore() {
    }

    public static Path file(String listId) {
        return FabricLoader.getInstance().getConfigDir().resolve("tierprofiles").resolve("logos").resolve(listId + ".png");
    }

    static boolean isReady(TierList list) {
        return ready.contains(list);
    }

    static void markBroken(TierList list) {
        ready.remove(list);
    }

    /** Runs once, in the background, after the privacy notice has been seen. */
    public static void downloadIfNeeded() {
        Global global = TierProfiles.config().global;
        if (started || !global.officialLogos || !ProfileService.isAllowed(global)) {
            return;
        }
        started = true;

        Thread thread = new Thread(LogoStore::downloadAll, "TierProfiles logos");
        thread.setDaemon(true);
        thread.start();
    }

    private static void downloadAll() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        for (Map.Entry<TierList, String> source : SOURCES.entrySet()) {
            TierList list = source.getKey();
            Path target = file(list.id());
            try {
                if (isFresh(target) || download(client, source.getValue(), target)) {
                    ready.add(list);
                }
            } catch (IOException | RuntimeException e) {
                ready.remove(list);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static boolean isFresh(Path target) throws IOException {
        return Files.isRegularFile(target)
                && System.currentTimeMillis() - Files.getLastModifiedTime(target).toMillis() < REFRESH_MILLIS;
    }

    private static boolean download(HttpClient client, String url, Path target) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "TierProfiles (+https://github.com/Asepritez/TierProfiles)")
                .GET()
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200 || response.body().length > TOO_BIG_BYTES) {
            return false;
        }

        BufferedImage source = ImageIO.read(new ByteArrayInputStream(response.body()));
        if (source == null) {
            return false;
        }
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        ImageIO.write(fitted(source), "png", temporary.toFile());
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    /** Shrinks to a square, keeping the shape, so a tall logo isn't stretched. */
    private static BufferedImage fitted(BufferedImage source) {
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        float shrink = (float) SIZE / Math.max(source.getWidth(), source.getHeight());
        int width = Math.round(source.getWidth() * shrink);
        int height = Math.round(source.getHeight() * shrink);
        Graphics2D graphics = out.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, (SIZE - width) / 2, (SIZE - height) / 2, width, height, null);
        graphics.dispose();
        return out;
    }
}
