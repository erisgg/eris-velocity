package gg.eris.erisvelocity.game;

import gg.eris.commons.core.util.RandomUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.Getter;
import org.apache.commons.io.FileUtils;

@Getter
public final class UhcGame {

  private static final int WORLDS_GENERATED = 10;

  private static final String BASE = "/home/unprivileged/eris/uhc_base/";
  private static final Path BASE_PATH = Path.of(BASE);

  private static final String PROPERTIES = "/home/unprivileged/eris/uhc_base_properties/";
  private static final Path PROPERTIES_PATH = Path.of(PROPERTIES);

  private static final String SERVERS = "/home/unprivileged/eris/uhc_servers/";
  private static final Path SERVERS_PATH = Path.of(SERVERS);

  private static final String OVERWORLDS = "/home/unprivileged/eris/worlds/overworlds/";
  private static final Path OVERWORLDS_PATH = Path.of(OVERWORLDS);

  private final int index;
  private final int port;
  private final Path path;

  public UhcGame(int index, int port) {
    this.index = index;
    this.port = port;
    this.path = Path.of(SERVERS).resolve("uhc-" + this.index + "/");
  }

  public void copyFiles() throws IOException {
    // Create servers folder if not present
    if (!Files.isDirectory(SERVERS_PATH)) {
      Files.createDirectory(Path.of(SERVERS));
    }

    // Delete the server if it is already there
    if (Files.isDirectory(this.path)) {
      FileUtils.deleteDirectory(this.path.toFile());
    }

    // Copy over raw server files
    FileUtils.copyDirectory(BASE_PATH.toFile(), this.path.toFile());

    // Copy over a world
    int world = RandomUtil.randomInt(WORLDS_GENERATED);
    Path worldPath = OVERWORLDS_PATH.resolve("" + world);
    FileUtils.copyDirectory(worldPath.toFile(), this.path.resolve("world").toFile());

    // Copy over server.properties for that server
    FileUtils.copyFileToDirectory(PROPERTIES_PATH.resolve(this.index + ".properties").toFile(),
            this.path.toFile());
    FileUtils.moveFile(this.path.resolve(this.index + ".properties").toFile(),
        this.path.resolve("server.properties").toFile());
  }

  public void startServer() throws IOException {
    Runtime.getRuntime().exec(this.path + "/start uhc-" + this.index);
  }

  public void killServer() throws IOException {
    Runtime.getRuntime().exec("tmux kill-session -t uhc-" + this.index);
  }

}
