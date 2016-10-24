import com.rabbitmq.client.*;
import java.util.*;

public class Send {
  private final static String QUEUE_NAME = "hello";


  public static void main(String[] argv) throws Exception {

    String msg = "Hi from Frank";


    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");

    Connection connection = factory.newConnection();

    Channel channel = connection.createChannel();
    channel.queueDeclare(QUEUE_NAME, false, false, false, null);


    Map<String, Object> headers = new HashMap<String, Object>();
    headers.put("session",  1234);

    channel.basicPublish("", QUEUE_NAME,
             new AMQP.BasicProperties.Builder()
               .headers(headers)
               .build(),
               msg.getBytes());

    System.out.println("[TX] sent message");

    channel.close();
    connection.close();

    return;
  }
}

