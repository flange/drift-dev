import com.rabbitmq.client.*;

public class Recv {
  private final static String QUEUE_NAME = "hello";

  public static void main(String[] argv) throws Exception {

    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");

    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();

    channel.queueDeclare(QUEUE_NAME, false, false, false, null);

    QueueingConsumer qconsumer = new QueueingConsumer(channel);
    channel.basicConsume(QUEUE_NAME, false, qconsumer);


    System.out.println("waiting...");
    QueueingConsumer.Delivery delivery = qconsumer.nextDelivery();
    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);

    System.out.println("done");

    channel.close();
    connection.close();


    return;
  }
}
