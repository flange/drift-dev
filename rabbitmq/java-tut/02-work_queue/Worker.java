import com.rabbitmq.client.*;

public class Worker {

  private final static String QUEUE_NAME = "task";

  private static void doWork(String task) throws InterruptedException {

    for (char ch: task.toCharArray())
        if (ch == '.') Thread.sleep(1000);

    return;
  }

  public static void main(String[] argv) throws Exception {

    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");

    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();

    channel.queueDeclare(QUEUE_NAME, false, false, false, null);

    channel.basicQos(1);

    final Consumer consumer = new DefaultConsumer(channel) {
      @Override
      public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws java.io.IOException {
        String message = new String(body, "UTF-8");

        System.out.println("[RX] received: " + message);

        try {
          doWork(message);
        } catch (InterruptedException e) {
          System.out.println("whoops");
        } finally {
          System.out.println("[RX] Done");
        }

      }
    };

    boolean autoAck = true;
    channel.basicConsume(QUEUE_NAME, autoAck, consumer);

    return;
  }
}
