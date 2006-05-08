/**
 * Copyright 2005 The Apache Software Foundation
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

package org.apache.hadoop.record.test;

import java.io.*;
import java.util.*;
import junit.framework.TestCase;
import java.util.logging.*;

import org.apache.hadoop.fs.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.conf.*;
import org.apache.hadoop.mapred.FileSplit;
import org.apache.hadoop.mapred.InputFormat;
import org.apache.hadoop.mapred.InputFormatBase;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.RecordReader;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.SequenceFileInputFormat;

public class TestWritable extends TestCase {
  private static final Logger LOG = InputFormatBase.LOG;

  private static int MAX_LENGTH = 10000;
  private static Configuration conf = new Configuration();

  public void testFormat() throws Exception {
    JobConf job = new JobConf(conf);
    FileSystem fs = FileSystem.getNamed("local", conf);
    Path dir = new Path(System.getProperty("test.build.data",".") + "/mapred");
    Path file = new Path(dir, "test.seq");
    
    Reporter reporter = new Reporter() {
        public void setStatus(String status) throws IOException {}
      };
    
    int seed = new Random().nextInt();
    //LOG.info("seed = "+seed);
    Random random = new Random(seed);

    fs.delete(dir);

    job.setInputPath(dir);

    // for a variety of lengths
    for (int length = 0; length < MAX_LENGTH;
         length+= random.nextInt(MAX_LENGTH/10)+1) {

      //LOG.info("creating; entries = " + length);

      // create a file with length entries
      SequenceFile.Writer writer =
        new SequenceFile.Writer(fs, file,
                                RecInt.class, RecBuffer.class);
      try {
        for (int i = 0; i < length; i++) {
          RecInt key = new RecInt();
          key.setData(i);
          byte[] data = new byte[random.nextInt(10)];
          random.nextBytes(data);
          RecBuffer value = new RecBuffer();
          ByteArrayOutputStream strm = new ByteArrayOutputStream(data.length);
          strm.write(data);
          value.setData(strm);
          writer.append(key, value);
        }
      } finally {
        writer.close();
      }

      // try splitting the file in a variety of sizes
      InputFormat format = new SequenceFileInputFormat();
      RecInt key = new RecInt();
      RecBuffer value = new RecBuffer();
      for (int i = 0; i < 3; i++) {
        int numSplits =
          random.nextInt(MAX_LENGTH/(SequenceFile.SYNC_INTERVAL/20))+1;
        //LOG.info("splitting: requesting = " + numSplits);
        FileSplit[] splits = format.getSplits(fs, job, numSplits);
        //LOG.info("splitting: got =        " + splits.length);

        // check each split
        BitSet bits = new BitSet(length);
        for (int j = 0; j < splits.length; j++) {
          RecordReader reader =
            format.getRecordReader(fs, splits[j], job, reporter);
          try {
            int count = 0;
            while (reader.next(key, value)) {
              // if (bits.get(key.get())) {
              // LOG.info("splits["+j+"]="+splits[j]+" : " + key.get());
              // LOG.info("@"+reader.getPos());
              // }
              assertFalse("Key in multiple partitions.", bits.get(key.getData()));
              bits.set(key.getData());
              count++;
            }
            //LOG.info("splits["+j+"]="+splits[j]+" count=" + count);
          } finally {
            reader.close();
          }
        }
        assertEquals("Some keys in no partition.", length, bits.cardinality());
      }

    }
  }

  public static void main(String[] args) throws Exception {
    new TestWritable().testFormat();
  }
}