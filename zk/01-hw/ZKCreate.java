import java.io.IOException;

import org.apache.zookeeper.*;

public class ZKCreate {

   public static ZooKeeper zk;
   public static ZooKeeperConnection conn;

   // Method to create znode in zookeeper ensemble
   public static void create(String path, byte[] data) throws KeeperException,InterruptedException {
      zk.create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE,
      CreateMode.PERSISTENT);
   }

   public static void main(String[] args) {

      // znode path
      String path = "/test-"; // Assign path to znode

      // data in byte array
      byte[] data = "My first zookeeper".getBytes(); // Declare data

      try {
         conn = new ZooKeeperConnection();
         zk = conn.connect("localhost");


         zk.create(path, "hello".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);





/*

         create(path, data); // Create the data to the specified path

*/
         conn.close();


      } catch (Exception e) {
         System.out.println(e.getMessage()); //Catch error message
      }
   }
}
