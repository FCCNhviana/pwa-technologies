/**
 * Copyright 2007 The Apache Software Foundation
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
package org.apache.hadoop.hbase;

import org.apache.hadoop.io.*;

import java.io.IOException;

/*******************************************************************************
 * Clients interact with the HMasterInterface to gain access to meta-level HBase
 * functionality, like finding an HRegionServer and creating/destroying tables.
 ******************************************************************************/
public interface HMasterInterface {
  public static final long versionID = 1L; // initial version

  //////////////////////////////////////////////////////////////////////////////
  // Admin tools would use these cmds
  //////////////////////////////////////////////////////////////////////////////

  public void createTable(HTableDescriptor desc) throws IOException;
  public void deleteTable(Text tableName) throws IOException;

  //////////////////////////////////////////////////////////////////////////////
  // These are the method calls of last resort when trying to find an HRegion
  //////////////////////////////////////////////////////////////////////////////

  public HServerAddress findRootRegion();
}