import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;

public class Send {

  private static final String EXCHANGE_NAME = "result";

  public static void main(String[] argv) throws Exception {

    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");
    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();

    channel.exchangeDeclare(EXCHANGE_NAME, "fanout");


    String queueName = EXCHANGE_NAME;
    channel.queueDeclare(queueName, false, false, false, null);

    channel.queueBind(queueName, EXCHANGE_NAME, "");


    String message = "hi";

    channel.basicPublish(EXCHANGE_NAME, "", null, message.getBytes("UTF-8"));
    System.out.println(" [x] Sent '" + message + "'" + " to exchange: " + EXCHANGE_NAME);

    channel.close();
    connection.close();
  }

}
