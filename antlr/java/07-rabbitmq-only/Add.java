import java.util.*;
import java.io.*;
import com.rabbitmq.client.*;

public class Add extends ServiceWrapper {

  public Add(boolean nsIn, boolean nsOut) {
    super(nsIn, nsOut);

    this.name = "Add";
  }

  public void sendMsg(Channel channel, String msg, String queueName) {

    System.out.println("Add.sendMsg() msg:\n" + msg + "  queueName: " + queueName);

    try {

      Map<String, Object> headers = new HashMap<String, Object>();
      headers.put("eof",  false);

      channel.basicPublish("", queueName.toString(), new AMQP.BasicProperties.Builder()
        .headers(headers)
        .build(),
        msg.getBytes());

    channel.waitForConfirms();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public void sendEof(Channel channel, String queueName) {


    System.out.println("Add.sendEof() queueName: " + queueName);

    try {
      Map<String, Object> headers = new HashMap<String, Object>();
      headers.put("eof", true);

      channel.basicPublish("", queueName.toString(), new AMQP.BasicProperties.Builder()
        .headers(headers)
        .build(),
        "".getBytes());

    channel.waitForConfirms();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public void singleQueueConsume(String inQ, String resultQueue) {

    System.out.println("singleQueueConsume()");

    try {

      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();
      channel.confirmSelect();

      channel.queueDeclare(inQ, false, false, false, null);

      QueueingConsumer consumer = new QueueingConsumer(channel);
      channel.basicConsume(inQ, false, consumer);

      while (true) {

        QueueingConsumer.Delivery delivery = consumer.nextDelivery();
        Map<String, Object> headers = delivery.getProperties().getHeaders();

        if ((boolean) headers.get("eof"))
          break;

        int numA = Integer.parseInt(new String(delivery.getBody()));

        sendMsg(channel, Integer.toString(numA + numA), resultQueue);
      }

      sendEof(channel, resultQueue);

      channel.close();
      connection.close();

    } catch (Exception e) {
      System.out.println(e);
    }

  }

  public void run(ArrayList<String> args, ArrayList<String> argNames, String resultQueue) {

    String inQ_A = argNames.get(0);
    String inQ_B = argNames.get(1);

    System.out.println("Add.run()");
    System.out.println("  in A: " + inQ_A);
    System.out.println("  in B: " + inQ_B);
    System.out.println("  out:  " + resultQueue);

    if (inQ_A.equals(inQ_B)) {
      singleQueueConsume(inQ_A, resultQueue);
      return;
    }

    try {

      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();
      channel.confirmSelect();

      channel.queueDeclare(inQ_A, false, false, false, null);
      channel.queueDeclare(inQ_B, false, false, false, null);

      QueueingConsumer consumerA = new QueueingConsumer(channel);
      channel.basicConsume(inQ_A, false, consumerA);

      QueueingConsumer consumerB = new QueueingConsumer(channel);
      channel.basicConsume(inQ_B, false, consumerB);

      while (true) {

        QueueingConsumer.Delivery deliveryA = consumerA.nextDelivery();
        Map<String, Object> headersA = deliveryA.getProperties().getHeaders();

        if ((boolean) headersA.get("eof"))
          break;

        int numA = Integer.parseInt(new String(deliveryA.getBody()));
        System.out.println("rxd A: " + numA);


        QueueingConsumer.Delivery deliveryB = consumerB.nextDelivery();
        Map<String, Object> headersB = deliveryB.getProperties().getHeaders();

        if ((boolean) headersB.get("eof"))
          break;

        int numB = Integer.parseInt(new String(deliveryB.getBody()));
        System.out.println("rxd B: " + numB);



        sendMsg(channel, Integer.toString(numA + numB), resultQueue);
      }

      sendEof(channel, resultQueue);

      channel.close();
      connection.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }



}
