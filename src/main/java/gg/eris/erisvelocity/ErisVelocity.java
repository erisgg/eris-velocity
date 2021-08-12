package gg.eris.erisvelocity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import gg.eris.commons.core.impl.redis.RedisWrapperImpl;
import gg.eris.commons.core.redis.RedisWrapper;
import gg.eris.erisvelocity.game.UhcGame;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

@Plugin(
    id = "erisvelocity",
    name = "Eris Velocity",
    version = "1.0",
    url = "https://eris.gg",
    description = "Eris Velocity Plugin",
    authors = {"Alfie Smith"}
)
public class ErisVelocity {

  private static final int MAX_SERVER_COUNT = 5;

  private static final int[] PORTS = new int[]{
      25510,
      25511,
      25512,
      25513,
      25514
  };

  private final ProxyServer server;
  private final Logger logger;
  private final RedisWrapper redisWrapper;
  private final ObjectMapper mapper;
  private final List<UhcGame> games;

  @Inject
  public ErisVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
    this.server = server;
    this.logger = logger;

    try {
      Files.createDirectories(dataDirectory);
    } catch (IOException err) {
      err.printStackTrace();
    }

    File configFile = getConfigFile(dataDirectory);

    JsonNode data;
    try {
      data = new ObjectMapper().readTree(configFile);
    } catch (IOException err) {
      this.redisWrapper = null;
      this.mapper = null;
      this.games = null;
      err.printStackTrace();
      return;
    }

    JsonNode redis = data.get("redis");

    this.redisWrapper = new RedisWrapperImpl(
        redis.get("password").asText(),
        redis.get("host").asText(),
        redis.get("port").asInt()
    );

    this.mapper = new ObjectMapper();
    this.games = Lists.newArrayList();
  }

  @Subscribe
  public void onProxyInitialization(ProxyInitializeEvent event) {
    this.server.getScheduler()
        .buildTask(this, () -> this.redisWrapper.set("playercount", this.mapper.createObjectNode()
            .put("count", this.server.getPlayerCount()))).repeat(10, TimeUnit.MILLISECONDS)
        .schedule();

    for (int i = 0; i < MAX_SERVER_COUNT; i++) {
      try {
        int port = PORTS[i];
        UhcGame game = new UhcGame(i, port);
        game.copyFiles();
        game.startServer();
        this.server.registerServer(new ServerInfo("uhc-" + i, new InetSocketAddress("localhost", port)));
        this.games.add(game);
      } catch (IOException err) {
        err.printStackTrace();
      }
    }
  }

  private File getConfigFile(Path path) {
    // If it doesn't exist, write all the contents of the resource (in the internal jarfile)
    // to config.json and save it.
    // Will use relative path (so where the jarfile is executed from)
    File file = new File(path.toFile().getPath() + File.separator + "config.json");
    if (file.exists()) {
      return file;
    }

    if (!file.exists()) {
      try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.json")) {
        Files.copy(Objects.requireNonNull(in), file.toPath());
      } catch (IOException err) {
        err.printStackTrace();
      }
    }

    return file;
  }

}
