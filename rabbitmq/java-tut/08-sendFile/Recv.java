import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.charset.*;

import java.io.*;

import com.rabbitmq.client.*;

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

        Path path = Paths.get("res/bar.tar");
        Files.write(path, body);

        try {
          channel.close();
          connection.close();
        } catch (Exception e) {
          System.out.println(e);
        }

      }
    };

    channel.basicConsume(QUEUE_NAME, false, consumer);

    return;
  }
}
