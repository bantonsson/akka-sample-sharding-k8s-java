package sample.sharding;

import java.io.Serializable;

public class CustomerMessage implements Serializable {
    public final int customerId;


    public CustomerMessage(int customerId) {
        this.customerId = customerId;
    }

    public static class GetAddress extends CustomerMessage {
        public GetAddress(int customerId) {
            super(customerId);
        }
    }
}
