import com.rabbitmq.client.*;

public class QueueDelete {
  private final static String QUEUE_NAME = "hello";

  public static void main(String[] argv) throws Exception {

    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");

    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();

    channel.queueDelete(QUEUE_NAME);

    channel.close();
    connection.close();
  }
}
