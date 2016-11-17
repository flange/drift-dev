import java.io.*;
import java.util.*;

import java.nio.file.*;
import java.nio.charset.*;

import java.security.MessageDigest;

import com.rabbitmq.client.*;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.*;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.*;
import org.apache.kafka.common.errors.*;


public class CmdLineWorker {


  public final static String TASK_QUEUE_NAME = "task";
  public final static String FAIL_QUEUE_NAME = "fail";


  public final static String NONAME = "anonymous";
  public final static String FS_GLOBAL = "fs/global/";

  public CmdInfo cmdInfo = null;
  public static ServiceRegistry serviceRegistry = new ServiceRegistry();

  public String cmd = null;
  public String resHash = null;
  public String[] cmdScript = null;

  public Properties consumerProps, producerProps;

  public CmdLineWorker() {

    consumerProps = new Properties();
    consumerProps.put("bootstrap.servers", "localhost:9092");
    consumerProps.put("max.poll.records", "1");
    consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");

    producerProps = new Properties();
    producerProps.put("bootstrap.servers", "localhost:9092");
    producerProps.put("acks", "all");
    producerProps.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    producerProps.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
  }

  public void normalProcessing() {

    ServiceInfo si = cmdInfo.services.get(0);
    System.out.println("normalProcessing() about to exec: " + si.binary);

    ServiceWrapper sw = serviceRegistry.db.get(si.binary);
    int ret = sw.run(si.args, si.argNames, resHash);

    if (ret > 0)
      sendFail();

    return;
  }

  public void deleteTopic(String topic) {

    try {

      //kafka-configs.sh --zookeeper localhost:2181 --alter --entity-type topics --entity-name <topic>  --add-config retention.ms=1000

      String[] cmd1 = new String[]{"/bin/sh", "-c", "kafka-configs.sh --zookeeper localhost:2181 --alter --entity-type topics --entity-name " + topic + " --add-config retention.ms=1000"};
      Process proc = Runtime.getRuntime().exec(cmd1);
      proc.waitFor();


      System.out.println("waiting for delete to propagate ...");
      for (int i = 0; i < 5; i++) {
        System.out.println(i);
        Thread.sleep(1000);

        System.out.print(String.format("\033[%dA",1)); // Move up
        System.out.print("\033[2K");
      }


      String[] cmd2 = new String[]{"/bin/sh", "-c", "kafka-configs.sh --zookeeper localhost:2181 --alter --entity-type topics --entity-name " + topic + " --add-config retention.ms=" + "604800000"};
      proc = Runtime.getRuntime().exec(cmd2);
      proc.waitFor();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public void execNameCmd() {

    String resultQueue = cmdInfo.servicesHash();

    try {

      ZooKeeperConnection zkConn = new ZooKeeperConnection();
      ZooKeeper zk = zkConn.connect("localhost");


      // declare signal q
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();
      channel.queueDeclare(resultQueue, false, false, false, null);


      // test for exisiting result
      String zkResultPath = "/" + resultQueue;
      Stat stat = zk.exists(zkResultPath, true);

      if (stat == null) {
        System.out.println("resultQ does not exists. create zk entry");
        zk.create(zkResultPath, "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

        normalProcessing();

        return;
      }

      System.out.println("result exisits: restart");

      // clean up and start fresh
      channel.queueDelete(resultQueue);
      deleteTopic(resultQueue);
      channel.queueDelete(resultQueue);

      normalProcessing();

      zkConn.close();
      channel.close();
      connection.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public void deleteTopics(ArrayList<String> topics) {

    //kafka-configs.sh --zookeeper localhost:2181 --alter --entity-type topics --entity-name <topic>  --add-config retention.ms=1000

    Process proc;
    String[] cmd1, cmd2;


    try {

      for (String topic : topics) {
        System.out.println(" --deleting: " + topic);

        cmd1 = new String[]{"/bin/sh", "-c", "kafka-configs.sh --zookeeper localhost:2181 --alter --entity-type topics --entity-name " + topic + " --add-config retention.ms=1000"};

        proc = Runtime.getRuntime().exec(cmd1);
        proc.waitFor();
      }

      System.out.println("waiting for delete to propagate ...");
      for (int i = 0; i < 5; i++) {
        System.out.println(i);
        Thread.sleep(1000);

        System.out.print(String.format("\033[%dA",1)); // Move up
        System.out.print("\033[2K");
      }


      for (String topic : topics) {
        cmd2 = new String[]{"/bin/sh", "-c", "kafka-configs.sh --zookeeper localhost:2181 --alter --entity-type topics --entity-name " + topic + " --add-config retention.ms=" + "604800000"};

        proc = Runtime.getRuntime().exec(cmd2);
        proc.waitFor();
      }

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }


  public void execNamespaceCmd() {

    String resultQueue = cmdInfo.servicesHash() + "-";

    try {

      ZooKeeperConnection zkConn = new ZooKeeperConnection();
      ZooKeeper zk = zkConn.connect("localhost");


      // declare signal q
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();
      channel.queueDeclare(resultQueue, false, false, false, null);


      // test for exisiting result
      String zkResultPath = "/" + resultQueue;
      Stat stat = zk.exists(zkResultPath, true);

      if (stat == null) {
        System.out.println("resultQ does not exists. create zk entry");
        zk.create(zkResultPath, "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

        normalProcessing();

        return;
      }

      System.out.println("result exisits: restart");

      ArrayList<String> unfinished = getUnfinishedResultQueues(resultQueue);
      unfinished.remove(resultQueue);

      // clean up and start fresh
      channel.queueDelete(resultQueue);
      deleteTopics(unfinished);
      channel.queueDelete(resultQueue);

      normalProcessing();

      zkConn.close();
      channel.close();
      connection.close();

    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public boolean hasEof(String queueName) {

    TopicPartition partition0 = new TopicPartition(queueName, 0);
    List<TopicPartition> topic_list = Arrays.asList(partition0);


    KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
    consumer.assign(topic_list);
    consumer.seekToEnd(topic_list);

    if (consumer.position(partition0) == 0)
      return false;

    consumer.seek(partition0, consumer.position(partition0) - 1);

    ConsumerRecords<String, String> records = consumer.poll(1000);

    for (ConsumerRecord<String, String> record : records) {
      if (record.key().equals("eof")) {
        consumer.close();
        return true;
      }

    }

    consumer.close();

    return false;
  }

  public ArrayList<String> getUnfinishedResultQueues(String queueName) {

    ArrayList<String> unfinished = new ArrayList<String>();
    ArrayList<String> resultQueues = new ArrayList<String>();

    org.apache.kafka.clients.consumer.KafkaConsumer<String, String> consumer;
    consumer = new KafkaConsumer<>(consumerProps);

    Map<String,List<PartitionInfo>> topicList = consumer.listTopics();

    for (String k : topicList.keySet()) {
      if (k.contains(queueName))
        resultQueues.add(k);
    }

    for (String rq : resultQueues) {
      if (!hasEof(rq))
        unfinished.add(rq);
    }

    consumer.close();

    return unfinished;
  }

  public void sendFail() {

    String resName = cmdInfo.servicesHash();

    if (cmdInfo.resultIsNamespace)
      resName += "-";

    if (cmdInfo.targetName == null && cmdInfo.targetNamespace == null)
      resName = "anonym";

    System.out.println("sending fail for: " + resName);


    try {

      Producer<String, byte[]> producer = new KafkaProducer<>(producerProps);
      producer.send(new ProducerRecord<String, byte[]>(FAIL_QUEUE_NAME, 0, "err", resName.getBytes())).get();
      producer.close();

    } catch (Exception e) {
      System.out.println(e);
    }

  }

  public void handleNameCmd() {
    execNameCmd();
    //sendNameResponse();
  }

  public void handleNamespaceCmd() {
    execNamespaceCmd();
    //sendNamespaceResponse();
  }

  public void handleAnonymousCmd() {
    execNameCmd();
    //sendAnonymousResponse();
  }

  public void handleCmd() {
    System.out.println("handleCmd()");
    cmdInfo.print();

    cmd     = cmdInfo.servicesString();
    resHash = cmdInfo.servicesHash();
    cmdScript = new String[]{"/bin/sh", "-c", cmd.toLowerCase()};

    System.out.println("  cmd:  " + cmd);
    System.out.println("  hash: " + resHash);

    if (cmdInfo.resultIsNamespace) {
      handleNamespaceCmd();
      return;
    }

    if (cmdInfo.targetName != null) {
      handleNameCmd();
      return;
    }

    handleAnonymousCmd();
    return;
  }

  public static CmdInfo retrieveCmd(byte[] body) throws Exception {

    CmdInfo cmdInfo = new CmdInfo();
    cmdInfo.fromByteArray(body);

    return cmdInfo;
  }

  public static void createDir(String path) {

    try {
      Path dir = Paths.get(path);

      if (Files.notExists(dir))
        Files.createDirectory(dir);
    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public static void initServiceRegistry() {

    ServiceWrapper cat = new Cat(false, false);
    serviceRegistry.db.put(cat.name, cat);

    ServiceWrapper square = new Square(false, false);
    serviceRegistry.db.put(square.name, square);

    ServiceWrapper add = new Add(false, false);
    serviceRegistry.db.put(add.name, add);

    ServiceWrapper emitter = new Emitter(false, false);
    serviceRegistry.db.put(emitter.name, emitter);

    ServiceWrapper emitter2 = new Emitter2(false, false);
    serviceRegistry.db.put(emitter2.name, emitter2);

    ServiceWrapper untar = new Untar(false, true);
    serviceRegistry.db.put(untar.name, untar);

    ServiceWrapper err = new Err(false, false);
    serviceRegistry.db.put(err.name, err);

    ServiceWrapper errEmitter = new ErrEmitter(false, false);
    serviceRegistry.db.put(errEmitter.name, errEmitter);

    ServiceWrapper dup = new Dup(false, true);
    serviceRegistry.db.put(dup.name, dup);
  }







  public static void main(String[] argv) throws Exception {

    initServiceRegistry();

    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");

    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();

    channel.queueDeclare(TASK_QUEUE_NAME, false, false, false, null);
    channel.basicQos(1);

    CmdLineWorker cmdLineWorker = new CmdLineWorker();

    com.rabbitmq.client.Consumer consumer = new DefaultConsumer(channel) {
      @Override
      public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws java.io.IOException {

        try{
          cmdLineWorker.cmdInfo = retrieveCmd(body);
        } catch (Exception e) {
          System.out.println("Command error");
        }

        cmdLineWorker.handleCmd();
        channel.basicAck(envelope.getDeliveryTag(), false);

        System.out.println("waiting for new tasks\n\n\n\n");
      }
    };

    channel.basicConsume(TASK_QUEUE_NAME, false, consumer);

    return;
  }
}
