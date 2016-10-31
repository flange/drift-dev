import java.io.*;
import java.util.*;
import com.rabbitmq.client.*;

public class Send {
  private final static String QUEUE_NAME = "hello";
  private final static String QUEUE_FAKE_NAME = "bla";


  public static boolean hasQueue(String queueName) {

    String[] cmdScript = new String[]{"/bin/sh", "-c", "sudo rabbitmqctl list_queues | tail -n +2 | awk '{print $1}'"};

    try {
      Process proc = Runtime.getRuntime().exec(cmdScript);
      Reader r = new InputStreamReader(proc.getInputStream());
      BufferedReader in = new BufferedReader(r);

      String outputLine;

      while((outputLine = in.readLine()) != null) {

        if (outputLine.equals(queueName)) {
          in.close();
          return true;
        }

      }

      in.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return false;
  }

  public static void main(String[] argv) throws Exception {

    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");

    Connection connection = factory.newConnection();

    Channel channel = connection.createChannel();
    channel.queueDeclare(QUEUE_NAME, false, false, false, null);

    System.out.println("queue " + QUEUE_NAME + ": " + hasQueue(QUEUE_NAME));
    System.out.println("queue " + QUEUE_FAKE_NAME + ": " + hasQueue(QUEUE_FAKE_NAME));


    channel.close();
    connection.close();

    return;
  }
}

