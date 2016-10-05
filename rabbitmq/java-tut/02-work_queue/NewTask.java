import com.rabbitmq.client.*;

public class NewTask {

  private final static String QUEUE_NAME = "task";

  private static String joinStrings(String[] strings, String delimiter) {

    int length = strings.length;

    if (length == 0)
      return "";

    StringBuilder words = new StringBuilder(strings[0]);

    for (int i = 1; i < length; i++)
        words.append(delimiter).append(strings[i]);

    return words.toString();
}

  private static String getMessage(String[] strings) {

    if (strings.length < 1)
      return "Hello, World!";

    return joinStrings(strings, " ");
  }

  public static void main(String[] argv) throws Exception {

    String msg = getMessage(argv);

    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");

    Connection connection = factory.newConnection();

    Channel channel = connection.createChannel();
    channel.queueDeclare(QUEUE_NAME, false, false, false, null);
    channel.basicPublish("", QUEUE_NAME, null, msg.getBytes());

    System.out.println("[TX] sent message");

    channel.close();
    connection.close();

    return;
  }
}
