package gg.eris.erisvelocity.game;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.Getter;
import org.apache.commons.io.FileUtils;

@Getter
public final class UhcGame {

  private static final String BASE = "/home/unprivileged/eris/uhc_base/";
  private static final Path BASE_PATH = Path.of(BASE);

  private static final String PROPERTIES = "/home/unprivileged/eris/uhc_base_properties/";
  private static final Path PROPERTIES_PATH = Path.of(PROPERTIES);

  private static final String SERVERS = "/home/unprivileged/eris/uhc_servers/";
  private static final Path SERVERS_PATH = Path.of(SERVERS);


  private final int index;
  private final int port;
  private final Path path;

  public UhcGame(int index, int port) {
    this.index = index;
    this.port = port;
    this.path = Path.of(SERVERS).resolve("uhc-" + this.index + "/");
  }

  public void copyFiles() throws IOException {
    if (!Files.isDirectory(SERVERS_PATH)) {
      Files.createDirectory(Path.of(SERVERS));
    }

    if (Files.isDirectory(this.path)) {
      FileUtils.deleteDirectory(this.path.toFile());
    }

    FileUtils.copyDirectory(BASE_PATH.toFile(), this.path.toFile());
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
