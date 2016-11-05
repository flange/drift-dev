import com.rabbitmq.client.*;

public class Recv {
  private final static String queueName = "hello";

  public static void main(String[] argv) throws Exception {

    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");

    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();

    channel.queueDeclare(queueName, false, false, false, null);


    boolean autoAck = true;
    GetResponse response = channel.basicGet(queueName, autoAck);
    if (response == null) {
        System.out.println("no msg");
    } else {
        byte[] body = response.getBody();
        System.out.println("msg: " + new String(body));
    }


    System.out.println("done");

    channel.close();
    connection.close();

    return;
  }
}
