package cpw.mods.forge.serverpacklocator.client;

import com.electronwill.nightconfig.core.ConfigFormat;
import cpw.mods.forge.serverpacklocator.ServerManifest;
import cpw.mods.forge.serverpacklocator.SidedPackHandler;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class ClientSidedPackHandler extends SidedPackHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private SimpleHttpClient clientDownloader;
    @Nullable
    private ServerManifest manifest;

    public ClientSidedPackHandler(final Path serverModsDir) {
        super(serverModsDir);
    }

    @Override
    protected boolean handleMissing(final Path path, final ConfigFormat<?> configFormat) throws IOException {
        Files.copy(Objects.requireNonNull(getClass().getResourceAsStream("/defaultclientconfig.toml")), path);
        return true;
    }

    @Override
    protected boolean validateConfig() {
        final Optional<String> remoteServer = getConfig().getOptional("client.remoteServer");
        if (remoteServer.isEmpty()) {
            LOGGER.fatal("Invalid configuration file {} found. Could not locate remove server address. " +
                    "Repair or delete this file to continue", getConfig().getNioPath().toString());
            throw new IllegalStateException("Invalid configuation file found, please delete or correct");
        }

        return true;
    }

    @Override
    protected List<IModFile> processModList(List<IModFile> scannedMods) {
        if (manifest == null) {
            return List.of();
        }
        final Set<String> manifestFileList = manifest.files()
                .stream()
                .map(ServerManifest.ModFileData::fileName)
                .collect(Collectors.toSet());
        return scannedMods.stream()
                .filter(f-> manifestFileList.contains(f.getFileName()))
                .collect(Collectors.toList());
    }

    @Override
    protected boolean waitForDownload() {
        if (!isValid()) return false;

        manifest = clientDownloader.waitForResult();
        if (manifest == null) {
            LOGGER.info("There was a problem with the connection, there will not be any server mods");
            return false;
        }
        return true;
    }

    @Override
    public void initialize(final IModLocator dirLocator) {
        List<String> excludedModIds = getConfig().<List<String>>getOptional("client.excludedModIds").orElse(List.of());
        clientDownloader = new SimpleHttpClient(this, Set.copyOf(excludedModIds));
    }
}
