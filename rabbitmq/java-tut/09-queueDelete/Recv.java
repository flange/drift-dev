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
        String message = new String(body, "UTF-8");
        System.out.println("[RX] received: " + message);

        try {
          System.out.println("blub");

          for (int i = 0; i < 60; i++) {
            System.out.println(i);
            Thread.sleep(1000);
          }


        } catch (Exception e) {}






      }

      public void handleCancel(java.lang.String consumerTag) {
        System.out.println("handleCancel");

        try {
          channel.close();
          connection.close();
        } catch (Exception e) {
          System.out.println("handleCancel");
        }
      }


      public void handleShutdownSignal(java.lang.String consumerTag) {
        System.out.println("handleShutdownSignal");

        try {
          channel.close();
          connection.close();
        } catch (Exception e) {
          System.out.println("handleCancel");
        }
      }


    };

    channel.basicConsume(QUEUE_NAME, false, consumer);

    return;
  }
}
