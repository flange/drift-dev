import java.util.*;
import com.rabbitmq.client.*;

public class Recv {
  private final static String QUEUE_NAME = "hello";

  public static void main(String[] argv) throws Exception {

    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");

    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();

    channel.queueDeclare(QUEUE_NAME, false, false, false, null);



    QueueingConsumer eofConsumer = new QueueingConsumer(channel);
    channel.basicConsume(QUEUE_NAME, false, eofConsumer);



    while (true) {

      QueueingConsumer.Delivery delivery;

      try {
        delivery = eofConsumer.nextDelivery();
      } catch(ConsumerCancelledException e) {
        System.out.println(e);
        break;
      }

      Map<String, Object> headers = delivery.getProperties().getHeaders();

      System.out.println((boolean) headers.get("eof"));

      if ((boolean) headers.get("eof") == false) {
        System.out.println("[OC] "+ new String(delivery.getBody()));
        continue;
      }

      System.out.println("[OC] received EOF");
      break;
    }

    System.out.println("main done");

    channel.close();
    connection.close();

    return;
  }
}
