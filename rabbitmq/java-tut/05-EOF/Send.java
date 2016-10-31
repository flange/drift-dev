import java.util.*;
import com.rabbitmq.client.*;

public class Send {
  private final static String OUTPUT_QUEUE = "output";
  private final static String EOF_QUEUE = "eof";


  public static void main(String[] argv) throws Exception {

    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");

    Connection connection = factory.newConnection();

    Channel channel = connection.createChannel();
    channel.queueDeclare(OUTPUT_QUEUE, false, false, false, null);
    channel.queueDeclare(EOF_QUEUE, false, false, false, null);

    Map<String, Object> headers = new HashMap<String, Object>();
    headers.put("eof",  false);

    for (int i = 0; i < 20; i++) {

    channel.basicPublish("", OUTPUT_QUEUE,
             new AMQP.BasicProperties.Builder()
               .headers(headers)
               .build(),
               Integer.toString(i).getBytes());

      System.out.println("sent: " + Integer.toString(i));
      Thread.sleep(1000);
    }

    headers.put("eof", true);

    channel.basicPublish("", OUTPUT_QUEUE,
             new AMQP.BasicProperties.Builder()
               .headers(headers)
               .build(),
               "".getBytes());

    System.out.println("sent eof");

    channel.close();
    connection.close();

    return;
  }
}

