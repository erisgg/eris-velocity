package gg.eris.erisvelocity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.velocitypowered.api.event.ResultedEvent.ComponentResult;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.permission.PermissionsSetupEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.permission.PermissionFunction;
import com.velocitypowered.api.permission.PermissionProvider;
import com.velocitypowered.api.permission.PermissionSubject;
import com.velocitypowered.api.permission.Tristate;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
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
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextDecoration.State;
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

  private static final Duration START_TIME = Duration.of(60, ChronoUnit.SECONDS);

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
  private final Map<String, UhcGame> games;

  private final Set<String> started;
  private final Cache<String, Boolean> starting;

  private static final Set<String> SERVERS = Set.of(
      "alfie",
      "aifle",
      "actness",
      "nonhotdog",
      "ieonphie",
      "twoclicked",
      "devuls",
      "appleciderisgud",
      "olimxn",
      "hughjph"
  );

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
      this.started = null;
      this.starting = null;
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
    this.games = Maps.newHashMap();
    this.started = Sets.newConcurrentHashSet();
    this.starting = CacheBuilder.newBuilder()
        .expireAfterWrite(START_TIME)
        .concurrencyLevel(1)
        .build();
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
        this.server
            .registerServer(new ServerInfo("uhc-" + i, new InetSocketAddress("localhost", port)));
        this.games.put("uhc-" + i, game);
      } catch (IOException err) {
        err.printStackTrace();
      }
    }

    this.server.getScheduler()
        .buildTask(this, () -> {
          for (RegisteredServer server : this.server.getAllServers()) {
            ServerInfo info = server.getServerInfo();
            if (!info.getName().startsWith("uhc-")) {
              continue;
            }

            server.ping().orTimeout(5, TimeUnit.SECONDS)
                .whenComplete((serverPing, throwable) -> {
                  if (throwable != null) {
                    if (!this.started.contains(info.getName())) {
                      return;
                    }

                    Boolean restarting = this.starting.getIfPresent(info.getName());
                    if (restarting != null) {
                      return;
                    }

                    UhcGame game = this.games.get(info.getName());
                    try {
                      this.starting.put(info.getName(), true);
                      game.killServer();
                      game.copyFiles();
                      game.startServer();
                      this.logger.info("Restarting " + info.getName());
                    } catch (IOException err) {
                      err.printStackTrace();
                    }
                  } else {
                    if (this.started.add(info.getName())) {
                      this.logger.info("Loaded " + info.getName());
                    } else {
                      if (this.starting.getIfPresent(info.getName()) != null) {
                        this.starting.invalidate(info.getName());
                        this.logger.info("Restarted " + info.getName());
                      }
                    }
                  }
                });
          }
        })
        .repeat(15, TimeUnit.SECONDS)
        .schedule();

  }

  @Subscribe
  public void onPlayerJoin(LoginEvent event) {
    if (this.started.size() != MAX_SERVER_COUNT) {
      event.setResult(ComponentResult.denied(
          Component.text("(!)")
              .color(TextColor.color(NamedTextColor.GOLD))
              .decoration(TextDecoration.BOLD, State.TRUE)
              .append(Component.text(" Eris is still starting")
                  .color(TextColor.color(NamedTextColor.YELLOW)))
      ));
      this.logger.warn("Player tried to join when only " + this.started.size()
          + " servers are loaded.");
    }
  }

  @Subscribe
  public void onPermissionCalculate(PermissionsSetupEvent event) {
    if (event.getSubject() instanceof Player) {
      event.setProvider(new PermissionProvider() {
        @Override
        public PermissionFunction createFunction(PermissionSubject permissionSubject) {
          return new PermissionFunction() {
            @Override
            public Tristate getPermissionValue(String s) {
              if (s.equals("velocity.command.server")) {
                return SERVERS.contains(((Player) event.getSubject()).getUsername().toLowerCase(
                    Locale.ROOT)) ? Tristate.TRUE : Tristate.FALSE;
              } else {
                return Tristate.FALSE;
              }
            }
          };
        }
      });
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
