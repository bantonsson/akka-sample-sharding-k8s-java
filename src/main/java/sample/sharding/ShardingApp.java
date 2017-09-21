package sample.sharding;

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class ShardingApp {

  public static void main(String[] args) {
    Config baseConfig = ConfigFactory.load();
    String actorSystemName = System.getProperty("actorSystemName");
    if (actorSystemName == null) {
      System.out.println("[WARNING] Actor System name not set by the actorSystemName property! Using default name 'ShardingSample'");
      actorSystemName = "ShardingSample";
    }

    // If we have added ports on the command line, then override them in the config and start multiple actor systems
    if (args.length > 0) {
      // Check that we have an even number of ports, one remoting and one http for each actor system
      if (args.length % 2 == 1) {
        System.out.println("[ERROR] Need an even number of ports! One remoting and one HTTP port for each actor system.");
        System.exit(1);
      }
      for (int i = 0; i < args.length; i += 2) {
        String remoting = args[i];
        String http = args[i + 1];
        // Override the configuration of the port
        Config config = ConfigFactory.parseString(
          "akka.remote.netty.tcp.port = " + remoting + "\n" +
          "sample.http.port = " + http).withFallback(baseConfig);
        createAndStartActorSystem(actorSystemName, config);
      }
    }
    else {
      createAndStartActorSystem(actorSystemName, baseConfig);
    }
  }

  private static void createAndStartActorSystem(String name, Config config) {
    // Create an Akka system
    ActorSystem system = ActorSystem.create(name, config);

    // Create an actor that starts the sharding and the HTTP server
    system.actorOf(Props.create(MainActor.class));
  }
}
