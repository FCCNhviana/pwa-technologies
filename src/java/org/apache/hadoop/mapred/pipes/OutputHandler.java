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

package org.apache.hadoop.mapred.pipes;

import java.io.IOException;

import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;

/**
 * Handles the upward (C++ to Java) messages from the application.
 */
class OutputHandler implements UpwardProtocol {
  private Reporter reporter;
  private OutputCollector collector;
  private float progressValue = 0.0f;
  private boolean done = false;
  private Throwable exception = null;
  
  /**
   * Create a handler that will handle any records output from the application.
   * @param collector the "real" collector that takes the output
   * @param reporter the reporter for reporting progress
   */
  public OutputHandler(OutputCollector collector, Reporter reporter) {
    this.reporter = reporter;
    this.collector = collector;
  }

  /**
   * The task output a normal record.
   */
  public void output(WritableComparable key, 
                     Writable value) throws IOException {
    collector.collect(key, value);
  }

  /**
   * The task output a record with a partition number attached.
   */
  public void partitionedOutput(int reduce, WritableComparable key, 
                                Writable value) throws IOException {
    PipesPartitioner.setNextPartition(reduce);
    collector.collect(key, value);
  }

  /**
   * Update the status message for the task.
   */
  public void status(String msg) throws IOException {
    reporter.setStatus(msg);
  }

  /**
   * Update the amount done and call progress on the reporter.
   */
  public void progress(float progress) throws IOException {
    progressValue = progress;
    reporter.progress();
  }

  /**
   * The task finished successfully.
   */
  public void done() throws IOException {
    synchronized (this) {
      done = true;
      notify();
    }
  }

  /**
   * Get the current amount done.
   * @return a float between 0.0 and 1.0
   */
  public float getProgress() {
    return progressValue;
  }

  /**
   * The task failed with an exception.
   */
  public void failed(Throwable e) {
    synchronized (this) {
      exception = e;
      notify();
    }
  }

  /**
   * Wait for the task to finish or abort.
   * @return did the task finish correctly?
   * @throws Throwable
   */
  public synchronized boolean waitForFinish() throws Throwable {
    while (!done && exception == null) {
      wait();
    }
    if (exception != null) {
      throw exception;
    }
    return done;
  }
}