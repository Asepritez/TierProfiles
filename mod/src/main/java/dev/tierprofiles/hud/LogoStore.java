package dev.tierprofiles.hud;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import dev.tierprofiles.TierProfiles;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Official logos and title badges, downloaded once from each list's own website and kept
 * in the config folder. They're never in the mod itself. Every failure is quiet, because
 * the fallbacks are perfectly good.
 */
public final class LogoStore {
    /** Big enough to stay sharp on a large card, small enough to stay cheap. */
    static final int SIZE = 64;
    private static final long REFRESH_MILLIS = 30L * 24 * 60 * 60 * 1000;
    private static final long TOO_BIG_BYTES = 8 * 1024 * 1024;

    private static final Map<String, String> SOURCES = new LinkedHashMap<>();

    static {
        SOURCES.put("mcpvp", "https://www.mcpvp.com/apple-touch-icon.png");
        SOURCES.put("pvptiers", "https://pvptiers.com/hex-logo.png");
        SOURCES.put("subtiers", "https://subtiers.net/subtiers-icon.webp");
        SOURCES.put("pvphq", "https://pvphq.com/assets/pvphq-logo.png");
    }

    private static final Set<String> ready = Collections.synchronizedSet(new HashSet<>());
    private static volatile boolean started;

    private LogoStore() {
    }

    public static Path file(String id) {
        return FabricLoader.getInstance().getConfigDir().resolve("tierprofiles").resolve("logos").resolve(id + ".png");
    }

    static boolean isReady(String id) {
        return ready.contains(id);
    }

    static void markBroken(String id) {
        ready.remove(id);
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
        for (Map.Entry<String, String> source : SOURCES.entrySet()) {
            String id = source.getKey();
            Path target = file(id);
            try {
                if (isFresh(target) || download(client, source.getValue(), target)) {
                    ready.add(id);
                }
            } catch (IOException | RuntimeException e) {
                if (Files.isRegularFile(target)) {
                    ready.add(id);
                }
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

        BufferedImage source = decode(response.body());
        if (source == null) {
            return false;
        }
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        ImageIO.write(fitted(source), "png", temporary.toFile());
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    /** Java reads PNG itself. Some lists only publish WebP, which needs the decoder. */
    private static BufferedImage decode(byte[] bytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image != null) {
            return image;
        }
        ImageReader reader = new WebPImageReaderSpi().createReaderInstance();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(in);
            return reader.read(0);
        } finally {
            reader.dispose();
        }
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
