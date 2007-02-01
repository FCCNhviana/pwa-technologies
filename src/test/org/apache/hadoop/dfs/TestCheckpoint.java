/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.dfs;

import junit.framework.TestCase;
import java.io.*;
import java.util.Random;
import java.net.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSInputStream;
import org.apache.hadoop.fs.FSOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

/**
 * This class tests the creation and validation of a checkpoint.
 * @author Dhruba Borthakur
 */
public class TestCheckpoint extends TestCase {
  static final long seed = 0xDEADBEEFL;
  static final int blockSize = 8192;
  static final int fileSize = 16384;
  static final int numDatanodes = 4;

  private void writeFile(FileSystem fileSys, Path name, int repl)
  throws IOException {
    // create and write a file that contains three blocks of data
    FSOutputStream stm = fileSys.createRaw(name, true, (short)repl,
        (long)blockSize);
    byte[] buffer = new byte[fileSize];
    Random rand = new Random(seed);
    rand.nextBytes(buffer);
    stm.write(buffer);
    stm.close();
  }
  
  
  private void checkFile(FileSystem fileSys, Path name, int repl)
  throws IOException {
    assertTrue(fileSys.exists(name));
    String[][] locations = fileSys.getFileCacheHints(name, 0, fileSize);
    for (int idx = 0; idx < locations.length; idx++) {
      assertEquals("Number of replicas for block" + idx,
          Math.min(numDatanodes, repl), locations[idx].length);  
    }
  }
  
  private void cleanupFile(FileSystem fileSys, Path name)
  throws IOException {
    assertTrue(fileSys.exists(name));
    fileSys.delete(name);
    assertTrue(!fileSys.exists(name));
  }

  /**
   * put back the old namedir
   */
  private void resurrectNameDir(File[] namedirs) 
  throws IOException {
    String parentdir = namedirs[0].getParent();
    String name = namedirs[0].getName();
    File oldname =  new File(parentdir, name + ".old");
    if (!oldname.renameTo(namedirs[0])) {
      assertTrue(false);
    }
  }

  /**
   * remove one namedir
   */
  private void removeOneNameDir(File[] namedirs) 
  throws IOException {
    String parentdir = namedirs[0].getParent();
    String name = namedirs[0].getName();
    File newname =  new File(parentdir, name + ".old");
    if (!namedirs[0].renameTo(newname)) {
      assertTrue(false);
    }
  }

  /*
   * Verify that namenode does not startup if one namedir is bad.
   */
  private void testNamedirError(Configuration conf, File[] namedirs) 
  throws IOException {
    System.out.println("Starting testNamedirError");
    Path file1 = new Path("checkpoint.dat");
    MiniDFSCluster cluster = null;

    if (namedirs.length <= 1) {
      return;
    }
    
    //
    // Remove one namedir & Restart cluster. This should fail.
    //
    removeOneNameDir(namedirs);
    try {
      cluster = new MiniDFSCluster(65312, conf, numDatanodes, 
                                                false, false);
      assertTrue(false);
    } catch (IOException e) {
      // no nothing
    }
    resurrectNameDir(namedirs); // put back namedir
  }

  /*
   * Simulate namenode crashing after rolling edit log.
   */
  private void testSecondaryNamenodeError1(Configuration conf)
  throws IOException {
    System.out.println("Starting testSecondaryNamenodeError 1");
    Path file1 = new Path("checkpoint.dat");
    MiniDFSCluster cluster = new MiniDFSCluster(65312, conf, numDatanodes, 
                                                false, false);
    FileSystem fileSys = cluster.getFileSystem();
    try {
      Thread.sleep(15000L);
    } catch (InterruptedException e) {
      // nothing
    }
    try {
      assertTrue(!fileSys.exists(file1));
      //
      // Make the checkpoint fail after rolling the
      // edit log.
      //
      SecondaryNameNode secondary = new SecondaryNameNode(conf);
      secondary.initializeErrorSimulationEvent(2);
      secondary.setErrorSimulation(0);

      try {
        secondary.doCheckpoint();  // this should fail
        assertTrue(false);      
      } catch (IOException e) {
      }
      secondary.shutdown();

      //
      // Create a new file
      //
      writeFile(fileSys, file1, 3);
      checkFile(fileSys, file1, 3);
    } finally {
      fileSys.close();
      cluster.shutdown();
    }

    //
    // Restart cluster and verify that file exists.
    // Then take another checkpoint to verify that the 
    // namenode restart accounted for the rolled edit logs.
    //
    System.out.println("Starting testSecondaryNamenodeError 2");
    cluster = new MiniDFSCluster(65312, conf, numDatanodes, 
                                 false, false);
    fileSys = cluster.getFileSystem();
    try {
      Thread.sleep(15000L);
    } catch (InterruptedException e) {
      // nothing
    }
    try {
      checkFile(fileSys, file1, 3);
      cleanupFile(fileSys, file1);
      SecondaryNameNode secondary = new SecondaryNameNode(conf);
      secondary.doCheckpoint();
      secondary.shutdown();
    } finally {
      fileSys.close();
      cluster.shutdown();
    }
  }

  /*
   * Simulate a namenode crash after uploading new image
   */
  private void testSecondaryNamenodeError2(Configuration conf)
  throws IOException {
    System.out.println("Starting testSecondaryNamenodeError 21");
    Path file1 = new Path("checkpoint.dat");
    MiniDFSCluster cluster = new MiniDFSCluster(65312, conf, numDatanodes, 
                                                false, false);
    FileSystem fileSys = cluster.getFileSystem();
    try {
      Thread.sleep(15000L);
    } catch (InterruptedException e) {
      // nothing
    }
    try {
      assertTrue(!fileSys.exists(file1));
      //
      // Make the checkpoint fail after rolling the
      // edit log.
      //
      SecondaryNameNode secondary = new SecondaryNameNode(conf);
      secondary.initializeErrorSimulationEvent(2);
      secondary.setErrorSimulation(1);

      try {
        secondary.doCheckpoint();  // this should fail
        assertTrue(false);      
      } catch (IOException e) {
      }
      secondary.shutdown();

      //
      // Create a new file
      //
      writeFile(fileSys, file1, 3);
      checkFile(fileSys, file1, 3);
    } finally {
      fileSys.close();
      cluster.shutdown();
    }

    //
    // Restart cluster and verify that file exists.
    // Then take another checkpoint to verify that the 
    // namenode restart accounted for the rolled edit logs.
    //
    System.out.println("Starting testSecondaryNamenodeError 22");
    cluster = new MiniDFSCluster(65312, conf, numDatanodes, 
                                 false, false);
    fileSys = cluster.getFileSystem();
    try {
      Thread.sleep(15000L);
    } catch (InterruptedException e) {
      // nothing
    }
    try {
      checkFile(fileSys, file1, 3);
      cleanupFile(fileSys, file1);
      SecondaryNameNode secondary = new SecondaryNameNode(conf);
      secondary.doCheckpoint();
      secondary.shutdown();
    } finally {
      fileSys.close();
      cluster.shutdown();
    }
  }

  /**
   * Tests checkpoint in DFS.
   */
  public void testCheckpoint() throws IOException {
    Path file1 = new Path("checkpoint.dat");
    Path file2 = new Path("checkpoint2.dat");
    File[] namedirs = null;

    Configuration conf = new Configuration();
    MiniDFSCluster cluster = new MiniDFSCluster(65312, conf, numDatanodes, false);
    FileSystem fileSys = cluster.getFileSystem();

    // Now wait for 15 seconds to give datanodes chance to register
    // themselves and to report heartbeat
    try {
      Thread.sleep(15000L);
    } catch (InterruptedException e) {
      // nothing
    }
    try {
      //
      // verify that 'format' really blew away all pre-existing files
      //
      assertTrue(!fileSys.exists(file1));
      assertTrue(!fileSys.exists(file2));
      namedirs = cluster.getNameDirs();

      //
      // Create file1
      //
      writeFile(fileSys, file1, 3);
      checkFile(fileSys, file1, 3);

      //
      // Take a checkpoint
      //
      SecondaryNameNode secondary = new SecondaryNameNode(conf);
      secondary.doCheckpoint();
      secondary.shutdown();
    } finally {
      fileSys.close();
      cluster.shutdown();
    }

    //
    // Restart cluster and verify that file1 still exist.
    //
    cluster = new MiniDFSCluster(65312, conf, numDatanodes, false, false);
    fileSys = cluster.getFileSystem();
    try {
      Thread.sleep(15000L);
    } catch (InterruptedException e) {
      // nothing
    }
    try {
      // check that file1 still exists
      checkFile(fileSys, file1, 3);
      cleanupFile(fileSys, file1);

      // create new file file2
      writeFile(fileSys, file2, 3);
      checkFile(fileSys, file2, 3);

      //
      // Take a checkpoint
      //
      SecondaryNameNode secondary = new SecondaryNameNode(conf);
      secondary.doCheckpoint();
      secondary.shutdown();
    } finally {
      fileSys.close();
      cluster.shutdown();
    }

    //
    // Restart cluster and verify that file2 exists and
    // file1 does not exist.
    //
    cluster = new MiniDFSCluster(65312, conf, numDatanodes, false, false);
    fileSys = cluster.getFileSystem();
    try {
      Thread.sleep(15000L);
    } catch (InterruptedException e) {
      // nothing
    }

    assertTrue(!fileSys.exists(file1));

    try {
      // verify that file2 exists
      checkFile(fileSys, file2, 3);
      cleanupFile(fileSys, file2);
    } finally {
      fileSys.close();
      cluster.shutdown();
    }

    testSecondaryNamenodeError1(conf);
    testSecondaryNamenodeError2(conf);
    testNamedirError(conf, namedirs);
  }
}