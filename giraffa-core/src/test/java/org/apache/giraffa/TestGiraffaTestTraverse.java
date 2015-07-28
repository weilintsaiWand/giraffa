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
package org.apache.giraffa;

import static org.apache.giraffa.GiraffaTestUtils.printFileStatus;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.FileAlreadyExistsException;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathIsNotEmptyDirectoryException;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.io.IOUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

/**
 * Test common file system use cases.
 */
public class TestGiraffaTestTraverse {
  static final Log LOG = LogFactory.getLog(TestGiraffaTestTraverse.class);

  private static final HBaseTestingUtility UTIL =
    GiraffaTestUtils.getHBaseTestingUtility();
  private static GiraffaFileSystem grfs;

  @BeforeClass
  public static void beforeClass() throws Exception {
    System.setProperty(
        HBaseTestingUtility.BASE_TEST_DIRECTORY_KEY, GiraffaTestUtils.BASE_TEST_DIRECTORY);
    UTIL.startMiniCluster(1);
  }

  @Before
  public void before() throws IOException {
    GiraffaConfiguration conf =
        new GiraffaConfiguration(UTIL.getConfiguration());
    GiraffaTestUtils.setGiraffaURI(conf);
    GiraffaFileSystem.format(conf, false);
    grfs = (GiraffaFileSystem) FileSystem.get(conf);
  }

  @After
  public void after() throws IOException {
    IOUtils.cleanup(LOG, grfs);
  }

  @AfterClass
  public static void afterClass() throws Exception {
    UTIL.shutdownMiniCluster();
  }

  @Test
  public void testSimpleGetFileInfo() throws IOException {
    grfs.mkdirs(new Path("folder2"));
    grfs.create(new Path("folder2/folder2"));
    FileStatus fileStat = grfs.getFileStatus(new Path("folder2"));
    printFileStatus(fileStat);
    assertEquals("folder2", fileStat.getPath().getName());
    fileStat = grfs.getFileStatus(new Path("folder2/folder2"));
    printFileStatus(fileStat);
    assertEquals("folder2", fileStat.getPath().getName());
  }

  @Test
  public void testDoNothing() {

  }

  @Test
  public void testCreateSpecialDirName() throws IOException {
    // '\u0000' does count
    grfs.mkdirs(new Path("\u0000"));
    assertEquals(1, grfs.listStatus(new Path(".")).length);
    // '~' does count
    grfs.mkdirs(new Path("\u007e"));
    assertEquals(2, grfs.listStatus(new Path(".")).length);
    // \u007f does not count
    grfs.mkdirs(new Path("\u007f"));
    assertEquals(2, grfs.listStatus(new Path(".")).length);
    // \u00ff overflow to negative value, does not count
    grfs.mkdirs(new Path("\u00ff"));
    assertEquals(2, grfs.listStatus(new Path(".")).length);
  }
}
