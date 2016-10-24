import com.rabbitmq.client.*;
import java.util.*;

public class Recv {
  private final static String QUEUE_NAME = "hello";

  public static void main(String[] argv) throws Exception {

    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");

    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();

    channel.queueDeclare(QUEUE_NAME, false, false, false, null);

    Consumer consumer = new DefaultConsumer(channel) {
      @Override
      public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws java.io.IOException {

        String message = new String(body, "UTF-8");
        System.out.println("[RX] received: " + message);

        Map<String, Object> headers = properties.getHeaders();
        System.out.println("[RX] session: " + headers.get("session"));

      }
    };

    channel.basicConsume(QUEUE_NAME, true, consumer);

    return;
  }
}
