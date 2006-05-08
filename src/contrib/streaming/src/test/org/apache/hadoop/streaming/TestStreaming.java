/**
 * Copyright 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.streaming;

import junit.framework.TestCase;
import java.io.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

/**
 * This class tests hadoopStreaming in MapReduce local mode.
 * It requires the Unix utilities tr and uniq.
 */
public class TestStreaming extends TestCase
{

  // "map" command: grep -E (red|green|blue)
  // reduce command: uniq
  String INPUT_FILE = "input.txt";
  String OUTPUT_DIR = "out";
  String input = "roses.are.red\nviolets.are.blue\nbunnies.are.pink\n";
  String map = "/usr/bin/tr . \\n"; // split words into lines. Careful with spaces in args
  String reduce = "/usr/bin/uniq";
  String outputExpect = "are\t\nblue\t\nbunnies\t\npink\t\nred\t\nroses\t\nviolets\t\n";

  StreamJob job;

  public TestStreaming() throws IOException
  {
    // trunk/src/contrib/streaming --> trunk/build/contrib/streaming/test/data
    String userDir = System.getProperty("user.dir");
    String antTestDir = System.getProperty("test.build.data", userDir);
    if(! userDir.equals(antTestDir)) {
        // because changes to user.dir are ignored by File
        throw new IllegalStateException("user.dir != test.build.data. The junit Ant task must be forked.");
    }

    boolean fromAntJunit = System.getProperty("test.build.data") != null;
    if(fromAntJunit) {
      new File(antTestDir).mkdirs();
      File outFile = new File(antTestDir, getClass().getName()+".log");
      PrintStream out = new PrintStream(new FileOutputStream(outFile));
      System.setOut(out);
      System.setErr(out);
    }
    System.out.println("test.build.data=" + antTestDir);
  }
  void createInput() throws IOException
  {
    String path = new File(".", INPUT_FILE).getAbsolutePath();// needed from junit forked vm
    DataOutputStream out = new DataOutputStream(new FileOutputStream(path));
    out.writeBytes(input);
    out.close();
  }

  public void testCommandLine()
  {
    try {
      createInput();
      boolean mayExit = false;

      // During tests, the default Configuration will use a local mapred
      // So don't specify -config or -cluster
      String argv[] = new String[] {
          "-input", INPUT_FILE,
          "-output", OUTPUT_DIR,
          "-mapper", map,
          "-reducer", reduce,
          /*"-debug",*/
          "-verbose"
      };

      job = new StreamJob(argv, mayExit);
      job.go();
      File outFile = new File(".", OUTPUT_DIR + "/part-00000").getAbsoluteFile();
      String output = StreamUtil.slurp(outFile);
      System.out.println("outEx=" + outputExpect);
      System.out.println("  out=" + output);
      assertEquals(outputExpect, output);

    } catch(Exception e) {
        failTrace(e);
    }


  }

  void failTrace(Exception e)
  {
    StringWriter sw = new StringWriter();
    e.printStackTrace(new PrintWriter(sw));
    fail(sw.toString());
  }



  public static void main(String[]args) throws Exception
  {
    new TestStreaming().testCommandLine();
  }

}