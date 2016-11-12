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


  public final  int MAX_EXEC_COUNT = 2;

  public final  String TASK_QUEUE_NAME = "task";
  public final  String DONE_QUEUE_NAME = "done";
  public final  String FAIL_QUEUE_NAME = "fail";
  public final  String QUERY_QUEUE_NAME = "query";

  public final  String NONAME = "anonymous";
  public final  String FS_GLOBAL = "fs/global/";

  public CmdInfo cmdInfo = null;
  public  ServiceRegistry serviceRegistry = new ServiceRegistry();

  public String cmd = null;
  public String resHash = null;
  public String[] cmdScript = null;

  public String DEFAULT_RETENTION = "604800000";

  public Properties consumerProps, producerProps;

  public ZooKeeperConnection zkConn;
  public ZooKeeper zk;

  public ConnectionFactory factory;

  public Connection connection;
  public Channel channel;

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

    try {
      // RabbitMQ setup
      factory = new ConnectionFactory();
      factory.setHost("localhost");

      connection = factory.newConnection();
      channel = connection.createChannel();
      channel.basicQos(1);

      channel.queueDeclare(TASK_QUEUE_NAME, false, false, false, null);
      channel.queueDeclare(DONE_QUEUE_NAME, false, false, false, null);
      channel.queueDeclare(FAIL_QUEUE_NAME, false, false, false, null);
      channel.queueDeclare(QUERY_QUEUE_NAME, false, false, false, null);


      // Zookeeper setup
      zkConn = new ZooKeeperConnection();
      zk = zkConn.connect("localhost");

    } catch (Exception e) {
      System.out.println(e);
    }
  }



  public void deleteTopic(String topic) {

    try {

      //kafka-configs.sh --zookeeper localhost:2181 --alter --entity-type topics --entity-name <topic>  --add-config retention.ms=1000

      String[] cmd1 = new String[]{"/bin/sh", "-c", "kafka-configs.sh --zookeeper localhost:2181 --alter --entity-type topics --entity-name " + topic + " --add-config retention.ms=1000"};

      Process proc = Runtime.getRuntime().exec(cmd1);
      proc.waitFor();


      System.out.println("waiting for delete to propagate ...");
      for (int i = 0; i < 10; i++) {
        System.out.println(i);
        Thread.sleep(3000);

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

  public void normalProcessing() {
    try {


      String resultQueue = cmdInfo.servicesHash();

      System.out.println("normal processing");
      channel.queueDeclare(resultQueue, false, false, false, null);


      ServiceInfo si = cmdInfo.services.get(0);
      System.out.println("execNameCmd() about to exec: " + si.binary);

      ServiceWrapper sw = serviceRegistry.db.get(si.binary);
      sw.run(si.args, si.argNames, resHash);

    } catch (Exception e) {
      System.out.println(e);
    }

  }

  public boolean execNameCmd() {

    System.out.println("execNameCmd()");

    try {
      String resultQueue = cmdInfo.servicesHash();

      channel.queueDeclare(resultQueue, false, false, false, null);


      // test for exisiting result
      String zkResultPath = "/" + resultQueue;
      Stat stat = zk.exists(zkResultPath, true);

      if (stat == null) {
        System.out.println("resultQ does not exists. create zk entry");
        zk.create(zkResultPath, "1".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

        normalProcessing();
        return true;
      }

      int execCnt = Integer.parseInt(new String(zk.getData(zkResultPath, null, null)));
      System.out.println("resultQ exists with count: " + execCnt);

      // reset and start again
      if (execCnt < MAX_EXEC_COUNT) {
        System.out.println("start reset");

        System.out.println("signal queue 1");
        channel.queueDelete(resultQueue);

        deleteTopic(resultQueue);

        System.out.println("increase exec count");
        execCnt++;
        zk.setData(zkResultPath, Integer.toString(execCnt).getBytes(), -1);

        System.out.println("signal queue 2");
        channel.queueDelete(resultQueue);

        normalProcessing();

        return true;
      }


      // fail -> clean up
      if (execCnt == MAX_EXEC_COUNT) {

        System.out.println("max retry count reached. cleaning up");

        System.out.println("signal queue 1");
        channel.queueDelete(resultQueue);

        deleteTopic(resultQueue);

        System.out.println("set exec count to 4");
        zk.setData(zkResultPath, Integer.toString(MAX_EXEC_COUNT + 1).getBytes(), -1);

        System.out.println("signal queue 2");
        channel.queueDelete(resultQueue);

        return false;
      }






    } catch (Exception e) {
      System.out.println(e);
    }

    return true;
  }

  public void deleteTopics(ArrayList<String> topics) {

    //kafka-configs.sh --zookeeper localhost:2181 --alter --entity-type topics --entity-name <topic>  --add-config retention.ms=1000

    Process proc;
    String[] cmd1, cmd2;


    try {

      for (String topic : topics) {
        cmd1 = new String[]{"/bin/sh", "-c", "kafka-configs.sh --zookeeper localhost:2181 --alter --entity-type topics --entity-name " + topic + " --add-config retention.ms=1000"};

        proc = Runtime.getRuntime().exec(cmd1);
        proc.waitFor();
      }

      System.out.println("waiting for delete to propagate ...");
      for (int i = 0; i < 10; i++) {
        System.out.println(i);
        Thread.sleep(12000);

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

  public void wrapUpUnfinishedNamespace(String queueName) {

    //System.out.println(

    System.out.println("wrapUpUnfinishedNamespace()");

    Producer<String, byte[]> producer = new KafkaProducer<>(producerProps);


    try {
    // RabbitMQ Setup
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");

    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();


    ArrayList<String> finished = getFinishedResultQueues(queueName);
    ArrayList<String> published = getPublishedResultQueues(queueName);
    ArrayList<String> unfinished = getUnfinishedResultQueues(queueName);


    System.out.println("finished:");
    for (String f : finished)
      System.out.println("  " + f);

    System.out.println("published:");
    for (String p : published)
      System.out.println("  " + p);



    // wrap up namespace Q
    for (String fq : finished) {

      String publishedName = Paths.get(fq.replaceAll("-", "/")).getFileName().toString();
      System.out.println("finished: " + fq + "  published as: " + publishedName);

      if (published.contains(publishedName)) {
        System.out.println("already published");
        continue;
      }

      System.out.println("publishing name: " + publishedName);
      producer.send(new ProducerRecord<String, byte[]>(queueName, 0, Integer.toString(0), publishedName.getBytes())).get();
    }

    producer.send(new ProducerRecord<String, byte[]>(queueName, 0, "eof", "".getBytes())).get();
    System.out.println("send eof to queue " + queueName);


    for (String rq : unfinished)
      channel.queueDelete(rq);

    deleteTopics(unfinished);


    String zkResultPath = "/" + queueName;
    int execCnt = Integer.parseInt(new String(zk.getData(zkResultPath, null, null)));

    System.out.println("increase exec count");
    execCnt++;
    zk.setData(zkResultPath, Integer.toString(execCnt).getBytes(), -1);


    channel.close();
    connection.close();
    producer.close();

    } catch (Exception e) {
      System.out.println(e);
    }



    System.out.println("wrap up done");

    return;
  }



  public boolean execNamespaceCmd() {

    try {
      String resultQueue = cmdInfo.servicesHash() + "-";

      // RabbitMQ Setup
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();


      // test for exisiting result
      String zkResultPath = "/" + resultQueue;
      Stat stat = zk.exists(zkResultPath, true);

      if(stat != null) {

        System.out.println("start clean up process");
        ArrayList<String> resultQs = getResultQueues(resultQueue);

        for (String rq : resultQs)
          channel.queueDelete(rq);

        deleteTopics(resultQs);

      } else {
        System.out.println("resultQ does not exists. create zk entry under: " + zkResultPath);
        zk.create(zkResultPath, "1".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
      }

      zkConn.close();

      System.out.println("normal processing");

      // Declare Namespace signal queue
      channel.queueDeclare(resultQueue, false, false, false, null);

      channel.close();
      connection.close();

      // Start service
      ServiceInfo si = cmdInfo.services.get(0);
      System.out.println("execNamespaceCmd() about to exec: " + si.binary);

      ServiceWrapper sw = serviceRegistry.db.get(si.binary);
      boolean success = sw.run(si.args, si.argNames, resHash);

      return success;

    } catch (Exception e) {
      System.out.println(e);
    }

    return false;
  }

  public boolean hasEof(String queueName) {

    TopicPartition partition0 = new TopicPartition(queueName, 0);
    List<TopicPartition> topic_list = Arrays.asList(partition0);


    KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
    consumer.assign(topic_list);
    consumer.seekToEnd(topic_list);

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


  public ArrayList<String> getPublishedResultQueues(String queueName) {

    ArrayList<String> published = new ArrayList<String>();

    Properties props = new Properties();
    props.put("bootstrap.servers", "localhost:9092");
    props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    props.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");

    TopicPartition partition0 = new TopicPartition(queueName, 0);
    List<TopicPartition> topic_list = Arrays.asList(partition0);

    KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props);
    consumer.assign(topic_list);
    consumer.seekToBeginning(topic_list);

    ConsumerRecords<String, byte[]> records = consumer.poll(1000);

    for (ConsumerRecord<String, byte[]> record : records) {
      if (!record.key().equals("eof")) {
        published.add(new String(record.value()));
      }
    }

    consumer.close();

    return published;
  }

  public ArrayList<String> getFinishedResultQueues(String queueName) {

    ArrayList<String> finished = new ArrayList<String>();
    ArrayList<String> resultQueues = new ArrayList<String>();

    org.apache.kafka.clients.consumer.KafkaConsumer<String, String> consumer;
    consumer = new KafkaConsumer<>(consumerProps);

    Map<String,List<PartitionInfo>> topicList = consumer.listTopics();

    for (String k : topicList.keySet()) {
      if (k.contains(queueName))
        resultQueues.add(k);
    }

    for (String rq : resultQueues) {
      if (hasEof(rq))
        finished.add(rq);
    }

    consumer.close();

    return finished;
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

  public ArrayList<String> getResultQueues(String queueName) {

    ArrayList<String> resultQueues = new ArrayList<String>();

    org.apache.kafka.clients.consumer.KafkaConsumer<String, String> consumer;
    consumer = new KafkaConsumer<>(consumerProps);

    Map<String,List<PartitionInfo>> topicList = consumer.listTopics();

    for (String k : topicList.keySet()) {
      if (k.contains(queueName))
        resultQueues.add(k);
    }

    return resultQueues;
  }

  public void sendResponse(String queueName) {

    System.out.println("sendResponse() to queue: " + queueName);

    try {
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost("localhost");

      Connection connection = factory.newConnection();
      Channel channel = connection.createChannel();
      channel.queueDeclare(queueName, false, false, false, null);

      channel.basicPublish("", queueName, null, cmdInfo.toByteArray());

      channel.close();
      connection.close();
    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public void sendNameResponse() {
    String queueName = cmdInfo.targetName + "-" + cmdInfo.sessionId;
    sendResponse(queueName);
    return;
  }

  public void sendNamespaceResponse() {
    String queueName = cmdInfo.targetNamespace + "-" + cmdInfo.sessionId;
    sendResponse(queueName);
    return;
  }

  public void sendAnonymousResponse() {
    String queueName = NONAME + "-" + cmdInfo.sessionId;
    sendResponse(queueName);
    return;
  }

  public boolean handleNameCmd() {
    return execNameCmd();
    //sendNameResponse();
  }

  public boolean handleNamespaceCmd() {
    return execNamespaceCmd();
    //sendNamespaceResponse();
  }

  public boolean handleAnonymousCmd() {
    return execNameCmd();
    //sendAnonymousResponse();
  }

  public boolean handleCmd() {
    System.out.println("handleCmd()");
    cmdInfo.print();

    cmd     = cmdInfo.servicesString();
    resHash = cmdInfo.servicesHash();
    cmdScript = new String[]{"/bin/sh", "-c", cmd.toLowerCase()};

    System.out.println("  cmd:  " + cmd);
    System.out.println("  hash: " + resHash);

    if (cmdInfo.resultIsNamespace)
      return handleNamespaceCmd();

    if (cmdInfo.targetName != null)
      return handleNameCmd();

    return handleAnonymousCmd();
  }

  public  CmdInfo retrieveCmd(byte[] body) throws Exception {

    CmdInfo cmdInfo = new CmdInfo();
    cmdInfo.fromByteArray(body);

    return cmdInfo;
  }

  public  void createDir(String path) {

    try {
      Path dir = Paths.get(path);

      if (Files.notExists(dir))
        Files.createDirectory(dir);
    } catch (Exception e) {
      System.out.println(e);
    }

    return;
  }

  public void initServiceRegistry() {

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

  }

  public void sendDone() {

    try {

      if (cmdInfo.targetName != null) {
        String targetName = Paths.get(cmdInfo.targetName).getFileName().toString();
        channel.basicPublish("", DONE_QUEUE_NAME, null, targetName.getBytes());


        System.out.println("sending done for: " + targetName);
      }

      if (cmdInfo.targetNamespace != null) {
        String targetNamespace = Paths.get(cmdInfo.targetNamespace).getFileName().toString() + "/";
        channel.basicPublish("", DONE_QUEUE_NAME, null, targetNamespace.getBytes());


        System.out.println("sending done for: " + targetNamespace);
      }

    } catch (Exception e) {
      System.out.println(e);
    }

  }

  public void sendFail() {

    try {

      channel.queueDelete(QUERY_QUEUE_NAME);

      if (cmdInfo.targetName == null && cmdInfo.targetNamespace == null) {
        channel.basicPublish("", FAIL_QUEUE_NAME, null, "anonym".getBytes());
        System.out.println("sending fail for anonym");
        return;
      }

      if (cmdInfo.targetNamespace != null) {
        channel.basicPublish("", FAIL_QUEUE_NAME, null, (cmdInfo.servicesHash() + "-").getBytes());
        System.out.println("sending fail for: " + cmdInfo.servicesHash() + "-");
        return;
      }

      channel.basicPublish("", FAIL_QUEUE_NAME, null, cmdInfo.servicesHash().getBytes());
      System.out.println("sending fail for: " + cmdInfo.servicesHash());

    } catch (Exception e) {
      System.out.println(e);
    }
  }

  public void run() {

    try {

      initServiceRegistry();

      final com.rabbitmq.client.Consumer consumer = new DefaultConsumer(channel) {
        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws java.io.IOException {

          System.out.println("handle delivery");

          try{
            cmdInfo = retrieveCmd(body);
          } catch (Exception e) {
            System.out.println("Command error");
          }

          boolean success = handleCmd();
          channel.basicAck(envelope.getDeliveryTag(), false);

          System.out.println("success: " + success);

          if (success)
            sendDone();
          else
            sendFail();


          System.out.println("waiting for new tasks\n\n\n\n");
        }
      };

      channel.basicConsume(TASK_QUEUE_NAME, false, consumer);

    } catch (Exception e) {
      System.out.println(e);
    }

  }


  public static void main(String[] argv) throws Exception {

    CmdLineWorker cmdLineWorker = new CmdLineWorker();

    Runtime.getRuntime().addShutdownHook(new Thread()
    {
        @Override
        public void run()
        {
          try {

            cmdLineWorker.channel.close();
            cmdLineWorker.connection.close();

            cmdLineWorker.zkConn.close();

          } catch (Exception e) {
            System.out.println(e);
          }
        }
    });

    cmdLineWorker.run();

    return;
  }
}
