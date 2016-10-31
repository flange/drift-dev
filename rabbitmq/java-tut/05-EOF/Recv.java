import java.util.*;
import com.rabbitmq.client.*;

public class Recv {
  private final static String OUTPUT_QUEUE = "output";
  private final static String EOF_QUEUE = "eof";

  public static void main(String[] argv) throws Exception {

    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");

    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();

    channel.queueDeclare(OUTPUT_QUEUE, false, false, false, null);


/*
    Consumer outputConsumer = new DefaultConsumer(channel) {
      @Override
      public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws java.io.IOException {

        Map<String, Object> headers = properties.getHeaders();

        System.out.println((boolean) headers.get("eof"));

        if ((boolean) headers.get("eof") == true) {
          System.out.println("[OC] received EOF");
          channel.basicCancel(consumerTag);
          return;
        }


        String message = new String(body, "UTF-8");
        System.out.println("[OC] " + message);
      }

      @Override
      public void handleCancelOk(String consumerTag)  {
        System.out.println("[OC] handleCancelOk");

        try {
        channel.close();
        connection.close();
        } catch (Exception e) {
          System.out.println(e);
        }
      }

    };

    String outputConsumerTag = channel.basicConsume(OUTPUT_QUEUE, false, outputConsumer);
*/



    QueueingConsumer eofConsumer = new QueueingConsumer(channel);
    channel.basicConsume(OUTPUT_QUEUE, false, eofConsumer);



    while (true) {
      QueueingConsumer.Delivery delivery = eofConsumer.nextDelivery();

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
