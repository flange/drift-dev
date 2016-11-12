import com.rabbitmq.client.*;

public class Recv {
  private final static String QUEUE_NAME = "hello";

  public static void main(String[] argv) throws Exception {

    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");

    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();

    channel.queueDeclare(QUEUE_NAME, false, false, false, null);
    channel.basicQos(1);



    Consumer consumer = new DefaultConsumer(channel) {
      @Override
      public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws java.io.IOException {
        String message = new String(body, "UTF-8");
        System.out.println("[RX] received: " + message);

        try {
          for (int i = 0; i < 5; i++) {
            System.out.println(i);
            Thread.sleep(1000);
          }

        } catch (Exception e) {
          System.out.println(e);
        }

        System.out.println("ack");
        channel.basicAck(envelope.getDeliveryTag(), false);
      }
    };

    channel.basicConsume(QUEUE_NAME, false, consumer);

    return;
  }
}
