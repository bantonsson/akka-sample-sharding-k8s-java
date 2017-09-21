package sample.sharding;

import akka.actor.*;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.cluster.sharding.ShardRegion;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.ServerBinding;
import akka.NotUsed;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import com.typesafe.config.Config;
import java.util.concurrent.CompletionStage;
import scala.compat.java8.FutureConverters;
import scala.concurrent.ExecutionContext;
import static akka.http.javadsl.server.PathMatchers.*;


public class MainActor extends AbstractActor {

  private static final ShardRegion.MessageExtractor CUSTOMER_MESSAGE_EXTRACTOR = new ShardRegion.MessageExtractor() {

    // This method will map an incoming message to an entity identifier
    @Override
    public String entityId(Object message) {
      if (message instanceof CustomerMessage)
        return String.valueOf(((CustomerMessage) message).customerId);
      else
        return null;
    }

    // This method will map an incoming message to the message that should be sent to the entity
    @Override
    public Object entityMessage(Object message) {
      if (message instanceof CustomerMessage)
        return message;
      else
        return message;
    }

    // This method will map an incoming message to a shard identifier
    @Override
    public String shardId(Object message) {
      int numberOfShards = 100;
      if (message instanceof CustomerMessage) {
        long id = ((CustomerMessage) message).customerId;
        return String.valueOf(id % numberOfShards);
        // Needed if you want to use 'remember entities', that will restart entities if a shard is rebalanced,
        // otherwise entities are restarted on the first message they receive.
        //
        // Think carefully about using 'remember entities' and the implications of starting all entities
        // in the rebalanced shard, since it can put more load on your persistence mechanism, maybe at a time
        // when it is already under load, or the network is under load.
        //
        // } else if (message instanceof ShardRegion.StartEntity) {
        //   long id = ((ShardRegion.StartEntity) message).id;
        //   return String.valueOf(id % numberOfShards)
      } else {
        return null;
      }
    }
  };

  // In order to access all directives the code needs to be inside an instance of a class that extends AllDirectives
  private static class Routes extends AllDirectives {
    public Route createRoute(ActorRef mainActor, ActorRef shardRegion, ExecutionContext ec, String hostname, int port) {
      final String host = hostname + ":" + port;
      return route(
        path(segment("customer").slash(integerSegment()).slash("address"), id ->
          onSuccess(() -> FutureConverters.toJava(
            // Send the ask to the shard region that will route it to the correct actor
            Patterns.ask(shardRegion, new CustomerMessage.GetAddress(id), 3000).map(response ->
              "Server " + host + " replying:\n" + response, ec)), this::complete)),
        path("stop", () ->
          get(() -> {
            mainActor.tell(Stop.INSTANCE, ActorRef.noSender());
            return complete("Server " + host + " is shutting Down\n");
          })
        )
      );
    }
  }

  private enum Stop {
    INSTANCE
  }

  private final ActorContext context = getContext();
  private final ActorSystem system = context.getSystem();
  private final LoggingAdapter log = Logging.getLogger(system, this);
  private final CompletionStage<ServerBinding> binding; // The HTTP server binding

  public MainActor() {
    // Set up and start Cluster Sharding
    ClusterShardingSettings settings = ClusterShardingSettings.create(system);
    ActorRef customerRegion = ClusterSharding.get(system)
      .start(
        "Customer",
        Props.create(Customer.class),
        settings,
        CUSTOMER_MESSAGE_EXTRACTOR);

    // Set up and start the HTTP server
    Http http = Http.get(system);
    ActorMaterializer materializer = ActorMaterializer.create(system);
    // In order to access all directives we need an instance where the routes are defined
    Routes routes = new Routes();
    Config config = system.settings().config();
    String hostname = config.getString("sample.http.hostname");
    int port = config.getInt("sample.http.port");
    Flow<HttpRequest, HttpResponse, NotUsed> routeFlow =
      routes.createRoute(context.self(), customerRegion, system.dispatcher(), hostname, port)
        .flow(system, materializer);
    binding = http.bindAndHandle(routeFlow, ConnectHttp.toHost(hostname, port), materializer);
    log.info("Server online at http://{}:{}/", hostname, port);
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .match(Stop.class, stop -> {
        log.info("Shutting down!");
        binding
          .thenCompose(ServerBinding::unbind) // Trigger HTTP server unbinding from the port
          .thenAccept(unbound -> CoordinatedShutdown.get(system).run()); // and shutdown when done
      })
      .build();
  }
}
