package sample.sharding;

import akka.actor.*;
import akka.cluster.sharding.ShardRegion;
import akka.event.*;
import java.util.concurrent.TimeUnit;
import java.util.Random;
import sample.sharding.CustomerMessage.*;
import scala.concurrent.duration.Duration;

public class Customer extends AbstractActor {
  private final Address actorSystemAddress = getContext().system().provider().getDefaultAddress();
  private LoggingAdapter log = Logging.getLogger(getContext().system(), getSelf().path().toStringWithAddress(actorSystemAddress));

  int customerId = Integer.MIN_VALUE;
  String address = null;


  @Override
  public void preStart() throws Exception {
    super.preStart();
    // getSelf().path().name() is the entity identifier (UTF-8 URL-encoded)
    customerId = Integer.parseInt(getSelf().path().name());
    // Read up data here from some store here
    address = "Some Street " + new Random(customerId).nextInt(50);

    log.info("Started Customer {}", customerId);

    // If we receive no messages for some time, then we should tell the shard region
    // to stop us to free up memory
    getContext().setReceiveTimeout(Duration.create(120, TimeUnit.SECONDS));
  }

  @Override
  public void postStop() throws Exception {
    log.info("Shutting down Customer {}", customerId);
    super.postStop();
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
      .match(GetAddress.class, message -> receiveGetAddress(message))
      .matchEquals(ReceiveTimeout.getInstance(), msg -> passivate())
      .build();
  }

  private void receiveGetAddress(GetAddress message) {
    log.info("Returning address {} for customer {}", address, customerId);
    getSender().tell("Customer: " + customerId + "\nAddress: " + address +"\nActor: " +
      getSelf().path().toStringWithAddress(actorSystemAddress) + "\n", getSelf());
  }

  private void passivate() {
    // Tell our shard region that we want to shut down to free up resources
    getContext().getParent().tell(
      new ShardRegion.Passivate(PoisonPill.getInstance()), getSelf());
  }
}
