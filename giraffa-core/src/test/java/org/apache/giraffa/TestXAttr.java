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

import org.apache.hadoop.HadoopIllegalArgumentException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.hdfs.server.namenode.FSXAttrBaseTest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.XAttrSetFlag;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.test.GenericTestUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.apache.hadoop.fs.CreateFlag.CREATE;
import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_NAMENODE_MAX_XATTR_SIZE_KEY;
import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_NAMENODE_MAX_XATTRS_PER_INODE_KEY;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;

import mockit.Mock;
import mockit.MockUp;

/**
 * This file is dedicated to test extended attribute(XAttr) related methods
 * of GiraffaFileSystem.
 */
public class TestXAttr extends FSXAttrBaseTest {
  static final Log LOG = LogFactory.getLog(TestXAttr.class);
  private static int pathCount = 8; // work around. TODO, to be deleted

  private static final HBaseTestingUtility UTIL =
    GiraffaTestUtils.getHBaseTestingUtility();
  private static GiraffaFileSystem grfs;
  private Path path1;
  private Path path2;
  private Path noThisPath;
  private String attrName1;
  private String attrName2;
  private String attrName3;
  private String attrName4;
  private byte[] attrValue1;
  private byte[] attrValue2;
  private byte[] attrValue3;
  private byte[] attrValue4;

  private class MockMiniDFSCluster extends MiniDFSCluster {
    MockDistributedFileSystem dfs;

    public MockMiniDFSCluster(GiraffaFileSystem grfs) {
      dfs = new MockDistributedFileSystem(grfs);
    }

    @Override
    public DistributedFileSystem getFileSystem() throws IOException{
      return dfs;
    }
  }

  private class MockDistributedFileSystem extends DistributedFileSystem {
    GiraffaFileSystem grfs;

    public MockDistributedFileSystem(GiraffaFileSystem grfs) {
      this.grfs = grfs;
    }

    @Override
    public byte[] getXAttr(Path path, final String name) throws IOException {
      return grfs.getXAttr(path, name);
    }
  }

  @BeforeClass
  public static void beforeClass() throws Exception {
    System.setProperty(
        HBaseTestingUtility.BASE_TEST_DIRECTORY_KEY,
         GiraffaTestUtils.BASE_TEST_DIRECTORY);
    Configuration hbaseConf = UTIL.getConfiguration();
    hbaseConf.setInt(DFS_NAMENODE_MAX_XATTRS_PER_INODE_KEY, 3);
    hbaseConf.setInt(DFS_NAMENODE_MAX_XATTR_SIZE_KEY, 16);

    UTIL.startMiniCluster(1);

    new MockUp<FSXAttrBaseTest>() {
      @Mock
      void restart(boolean checkpoint) throws Exception {
        // Do nothing.
      }

      @Mock
      void initFileSystem() throws Exception {
        // Do nothing.
      }
    };

//    new MockUp<MiniDFSCluster>() {
//      @Mock
//      DistributedFileSystem getFileSystem() throws Exception{
//        //return this.
//        throw new IOException("in mock 1");
//      }
//    };

//    new MockUp<DistributedFileSystem>() {
//      @Mock
//      byte[] getXAttr(Path path, final String name) throws Exception {
//        return grfs.getXAttr(path, name);
//        //throw new IOException("Say");
//      }
//    };

  }

  @Before
  public void before() throws IOException {
    // File System Initialization
    GiraffaConfiguration conf =
        new GiraffaConfiguration(UTIL.getConfiguration());
    GiraffaTestUtils.setGiraffaURI(conf);
    GiraffaFileSystem.format(conf, false);
    grfs = (GiraffaFileSystem) FileSystem.get(conf);

    createFiles();
    initAttributes();
    fs = grfs;   // replace fs in FSXAttrBaseTest with grfs
    dfsCluster = new MockMiniDFSCluster(grfs);
  }

  @After
  public void after() throws IOException {
    IOUtils.cleanup(LOG, grfs);
  }

  @AfterClass
  public static void afterClass() throws Exception {
    UTIL.shutdownMiniCluster();
  }

  /**
   * setXAttr related Tests
   */
  @Test
  public void testCanAddXAttrToAFile() throws IOException {
    grfs.setXAttr(path1, attrName1, attrValue1);
    List<String> listOfXAttrNames = grfs.listXAttrs(path1);
    assertEquals(1, listOfXAttrNames.size());
    assertTrue(listOfXAttrNames.contains(attrName1));
  }
//
//  @Test
//  public void testCanAddMultipleXAttrToSameFile() throws IOException {
//    grfs.setXAttr(path1, attrName1, attrValue1);
//    grfs.setXAttr(path1, attrName2, attrValue2);
//    List<String> listOfXAttrNames = grfs.listXAttrs(path1);
//    assertEquals(2, listOfXAttrNames.size());
//    assertTrue(listOfXAttrNames.contains(attrName1));
//    assertTrue(listOfXAttrNames.contains(attrName2));
//  }
//
//  @Test
//  public void testCanAddXAttrToDifferentFile() throws IOException {
//    grfs.setXAttr(path1, attrName1, attrValue1);
//    grfs.setXAttr(path2, attrName2, attrValue2);
//    List<String> listOfXAttrNames1 = grfs.listXAttrs(path1);
//    List<String> listOfXAttrNames2 = grfs.listXAttrs(path2);
//    assertEquals(1, listOfXAttrNames1.size());
//    assertTrue(listOfXAttrNames1.contains(attrName1));
//    assertEquals(1, listOfXAttrNames2.size());
//    assertTrue(listOfXAttrNames2.contains(attrName2));
//  }
//
//  @Test (expected = FileNotFoundException.class)
//  public void testCannotAddXAttrToNonExistedFile()
//          throws IOException {
//    grfs.setXAttr(noThisPath, attrName1, attrValue1);
//  }
//
//  @Test (expected = NullPointerException.class)
//  public void testCanNotSetAttrWithNullPath() throws IOException {
//    try {
//      grfs.setXAttr(null, attrName1, attrValue1);
//      assertTrue(false);  // should not come here
//    } finally {
//      List<String> listOfXAttrNames = grfs.listXAttrs(path1);
//      assertEquals(0, listOfXAttrNames.size());
//    }
//  }
//
//  @Test (expected = NullPointerException.class)
//  public void testCanNotSetAttrWithNullAttrName() throws IOException {
//    try {
//      grfs.setXAttr(path1, null, attrValue1);
//      assertTrue(false);  // should not come here
//    } finally {
//      List<String> listOfXAttrNames = grfs.listXAttrs(path1);
//      assertEquals(0, listOfXAttrNames.size());
//    }
//  }
//
//  @Test
//  public void testCanSetAttrWithNullAttrValue() throws IOException {
//    grfs.setXAttr(path1, attrName1, null);
//    List<String> listOfXAttrNames = grfs.listXAttrs(path1);
//    assertEquals(1, listOfXAttrNames.size());
//    assertTrue(listOfXAttrNames.contains(attrName1));
//    assertArrayEquals(new byte[0], grfs.getXAttr(path1, attrName1));
//  }
//
//  @Test
//  public void testCanOverwriteAttrWithoutAdditionalFlag() throws IOException {
//    grfs.setXAttr(path1, attrName1, attrValue1);
//    grfs.setXAttr(path1, attrName1, attrValue2);
//    List<String> listOfXAttrNames = grfs.listXAttrs(path1);
//    assertEquals(1, listOfXAttrNames.size());
//    assertTrue(listOfXAttrNames.contains(attrName1));
//    assertArrayEquals(attrValue2, grfs.getXAttr(path1, attrName1));
//  }
//
//  @Test
//  public void testCanOverwriteAttrWithoutDestroyOtherAttr() throws IOException {
//    grfs.setXAttr(path1, attrName1, attrValue1);
//    grfs.setXAttr(path1, attrName2, attrValue2);
//    grfs.setXAttr(path1, attrName1, attrValue3);
//    List<String> listOfXAttrNames = grfs.listXAttrs(path1);
//    assertEquals(2, listOfXAttrNames.size());
//    assertTrue(listOfXAttrNames.contains(attrName1));
//    assertTrue(listOfXAttrNames.contains(attrName2));
//    assertArrayEquals(attrValue3, grfs.getXAttr(path1, attrName1));
//    assertArrayEquals(attrValue2, grfs.getXAttr(path1, attrName2));
//  }
//
//  @Test
//  public void testAttrPrefixIsCaseInsensitive() throws IOException {
//    String caseInsensitiveName = "uSeR.attr1";
//    grfs.setXAttr(path1, attrName1, attrValue1);
//    grfs.setXAttr(path1, caseInsensitiveName, attrValue2);
//    List<String> listOfXAttrNames = grfs.listXAttrs(path1);
//    assertEquals(1, listOfXAttrNames.size());
//    assertTrue(listOfXAttrNames.contains(attrName1));
//    assertArrayEquals(attrValue2, grfs.getXAttr(path1, caseInsensitiveName));
//  }
//
//  @Test (expected = IOException.class)
//  public void testCanNotOverwriteAttrWithoutReplaceFlag() throws IOException {
//    grfs.setXAttr(path1, attrName1, attrValue1);
//    try {
//      grfs.setXAttr(path1, attrName1, attrValue2,
//              EnumSet.of(XAttrSetFlag.CREATE));
//      assertTrue(false);  // should not come here
//    } finally {
//      List<String> listOfXAttrNames = grfs.listXAttrs(path1);
//      assertEquals(1, listOfXAttrNames.size());
//      assertTrue(listOfXAttrNames.contains(attrName1));
//      assertArrayEquals(attrValue1, grfs.getXAttr(path1, attrName1));
//    }
//  }
//
//  @Test
//  public void testCanAddAttrWithCreateFlagOnly() throws IOException {
//    grfs.setXAttr(path1, attrName1, attrValue2,
//              EnumSet.of(XAttrSetFlag.CREATE));
//    List<String> listOfXAttrNames = grfs.listXAttrs(path1);
//    assertEquals(1, listOfXAttrNames.size());
//    assertTrue(listOfXAttrNames.contains(attrName1));
//  }
//
//  @Test
//  public void testCanOverwriteAttrWithReplaceFlagOnly() throws IOException {
//    grfs.setXAttr(path1, attrName1, attrValue1);
//    grfs.setXAttr(path1, attrName1, attrValue2,
//            EnumSet.of(XAttrSetFlag.REPLACE));
//    List<String> listOfXAttrNames = grfs.listXAttrs(path1);
//    assertEquals(1, listOfXAttrNames.size());
//    assertTrue(listOfXAttrNames.contains(attrName1));
//    assertArrayEquals(attrValue2, grfs.getXAttr(path1, attrName1));
//  }
//
//  @Test (expected = IOException.class)
//  public void testCanNotAddNewAttrWithReplaceFlagOnly() throws IOException {
//    try {
//      grfs.setXAttr(path1, attrName1, attrValue2,
//              EnumSet.of(XAttrSetFlag.REPLACE));
//      assertTrue(false); // should not come here
//    } finally {
//      List<String> listOfXAttrNames = grfs.listXAttrs(path1);
//      assertEquals(0, listOfXAttrNames.size());
//    }
//  }
//
//  @Test (expected = NullPointerException.class)
//  public void testCanNotAddNewAttrWithNullFlag() throws IOException {
//    try {
//      grfs.setXAttr(path1, attrName1, attrValue2, null);
//      assertTrue(false); // should not come here
//    } finally {
//      List<String> listOfXAttrNames = grfs.listXAttrs(path1);
//      assertEquals(0, listOfXAttrNames.size());
//    }
//  }
//
//  @Test (expected = IOException.class)
//  public void testCanNotAddNewAttrWithEmptyFlag() throws IOException {
//    try {
//      grfs.setXAttr(path1, attrName1, attrValue2,
//              EnumSet.noneOf(XAttrSetFlag.class));
//      assertTrue(false); // should not come here
//    } finally {
//      List<String> listOfXAttrNames = grfs.listXAttrs(path1);
//      assertEquals(0, listOfXAttrNames.size());
//    }
//  }
//
//  /**
//   * listXAttr related Tests
//   */
//  @Test
//  public void testCanListEmptyAttrList() throws IOException {
//    List<String> listOfXAttrNames = grfs.listXAttrs(path1);
//    assertEquals(0, listOfXAttrNames.size());
//  }
//
//  @Test
//  public void testCanListOneAttr() throws IOException {
//    grfs.setXAttr(path1, attrName1, attrValue1);
//    List<String> listOfXAttrNames = grfs.listXAttrs(path1);
//    assertEquals(1, listOfXAttrNames.size());
//    assertEquals(attrName1, listOfXAttrNames.get(0));
//  }
//
//  @Test
//  public void testCanListMultipleAttr() throws IOException {
//    grfs.setXAttr(path1, attrName1, attrValue1);
//    grfs.setXAttr(path1, attrName2, attrValue2);
//    List<String> listOfXAttrNames = grfs.listXAttrs(path1);
//    assertEquals(2, listOfXAttrNames.size());
//    assertTrue(listOfXAttrNames.contains(attrName1));
//    assertTrue(listOfXAttrNames.contains(attrName2));
//  }
//
//  @Test (expected = FileNotFoundException.class)
//  public void testCanLisAttrOnNonExistedFile() throws IOException {
//    grfs.listXAttrs(noThisPath);
//  }
//
//  /**
//   * getXAttr related tests
//   */
//  @Test
//  public void testCanGetXAttrValueByPathAndXAttrName() throws IOException {
//    grfs.setXAttr(path1, attrName1, attrValue1);
//    assertArrayEquals(attrValue1, grfs.getXAttr(path1, attrName1));
//  }
//
//  @Test
//  public void testCanGetMapOfXAttrByPath() throws IOException {
//    grfs.setXAttr(path1, attrName1, attrValue1);
//    Map<String, byte[]> map = grfs.getXAttrs(path1);
//    assertEquals(1, map.size());
//    assertTrue(map.containsKey(attrName1));
//    assertArrayEquals(attrValue1, map.get(attrName1));
//  }
//
//  @Test
//  public void testCanGetMapOfXAttrByPathAndSingleElementAttrNameList()
//          throws IOException {
//    // one to one match
//    grfs.setXAttr(path1, attrName1, attrValue1);
//    List<String> attrNameList = Collections.singletonList(attrName1);
//    Map<String, byte[]> map = grfs.getXAttrs(path1, attrNameList);
//    assertEquals(1, map.size());
//    assertTrue(map.containsKey(attrName1));
//    assertArrayEquals(attrValue1, map.get(attrName1));
//
//    grfs.setXAttr(path1, attrName2, attrValue2);
//
//    // now we have two attributes
//    map = grfs.getXAttrs(path1, attrNameList);
//    assertEquals(1, map.size());
//    assertTrue(map.containsKey(attrName1));
//    assertArrayEquals(attrValue1, map.get(attrName1));
//
//    attrNameList = Collections.singletonList(attrName2);
//    map = grfs.getXAttrs(path1, attrNameList);
//    assertEquals(1, map.size());
//    assertTrue(map.containsKey(attrName2));
//    assertArrayEquals(attrValue2, map.get(attrName2));
//  }
//
//  @Test (expected = HadoopIllegalArgumentException.class)
//  public void testCanNotGetMapOfXAttrByPathAndEmptyAttrNameList()
//          throws IOException {
//    grfs.setXAttr(path1, attrName1, attrValue1);
//    List<String> attrNameList = Collections.emptyList();
//    grfs.getXAttrs(path1, attrNameList);
//  }
//
//  @Test (expected = HadoopIllegalArgumentException.class)
//  public void testCanNotGetMapOfXAttrByPathAndNullNameList()
//          throws IOException {
//    grfs.setXAttr(path1, attrName1, attrValue1);
//    grfs.getXAttrs(path1, null);
//  }
//
//  @Test
//  public void testCanGetMapOfXAttrByPathAndMultipleElementsAttrNameList()
//          throws IOException {
//    grfs.setXAttr(path1, attrName1, attrValue1);
//    grfs.setXAttr(path1, attrName2, attrValue2);
//    grfs.setXAttr(path1, attrName3, attrValue3);
//    grfs.setXAttr(path2, attrName4, attrValue4);
//    // check if interfere with each other
//    List<String> attrNameList = Collections.singletonList(attrName4);
//    Map<String, byte[]> map = grfs.getXAttrs(path2, attrNameList);
//    assertEquals(1, map.size());
//    assertTrue(map.containsKey(attrName4));
//    assertArrayEquals(attrValue4, map.get(attrName4));
//
//    // check if partial match
//    attrNameList = Arrays.asList(attrName2, attrName3);
//    map = grfs.getXAttrs(path1, attrNameList);
//    assertEquals(2, map.size());
//    assertTrue(map.containsKey(attrName2));
//    assertArrayEquals(attrValue2, map.get(attrName2));
//    assertTrue(map.containsKey(attrName3));
//    assertArrayEquals(attrValue3, map.get(attrName3));
//
//    // check if full match
//    attrNameList = Arrays.asList(attrName1, attrName2, attrName3);
//    map = grfs.getXAttrs(path1, attrNameList);
//    assertEquals(3, map.size());
//    assertTrue(map.containsKey(attrName1));
//    assertArrayEquals(attrValue1, map.get(attrName1));
//    assertTrue(map.containsKey(attrName2));
//    assertArrayEquals(attrValue2, map.get(attrName2));
//    assertTrue(map.containsKey(attrName3));
//    assertArrayEquals(attrValue3, map.get(attrName3));
//  }
//
//  @Test
//  public void testCanGetXAttrValueMultipleTimes() throws IOException {
//    grfs.setXAttr(path1, attrName1, attrValue1);
//    grfs.setXAttr(path1, attrName2, attrValue2);
//    grfs.setXAttr(path2, attrName1, attrValue3);
//
//    // byte[] getXAttr(String src, String name)
//    assertArrayEquals(attrValue1, grfs.getXAttr(path1, attrName1));
//    assertArrayEquals(attrValue2, grfs.getXAttr(path1, attrName2));
//    assertArrayEquals(attrValue3, grfs.getXAttr(path2, attrName1));
//
//    // Map<String, byte[]> getXAttrs(Path path)
//    Map<String, byte[]> map1 =  grfs.getXAttrs(path1);
//    assertEquals(2, map1.size());
//    assertTrue(map1.containsKey(attrName1));
//    assertTrue(map1.containsKey(attrName2));
//    assertArrayEquals(attrValue1, map1.get(attrName1));
//    assertArrayEquals(attrValue2, map1.get(attrName2));
//    Map<String, byte[]> map2 =  grfs.getXAttrs(path2);
//    assertEquals(1, map2.size());
//    assertTrue(map2.containsKey(attrName1));
//    assertArrayEquals(attrValue3, map2.get(attrName1));
//
//    // Map<String, byte[]> getXAttrs(String src, List<String> names)
//    // It's in testCanGetMapOfXAttrByPathAndMultipleElementsAttrNameList
//  }
//
//  @Test
//  public void testCanNotGetXAttrFromNonExistedFile() throws IOException {
//    try {
//      grfs.getXAttr(noThisPath, attrName1);
//      assertTrue(false); // should never come here
//    } catch (FileNotFoundException e) {}
//
//    try {
//      grfs.getXAttrs(noThisPath);
//      assertTrue(false); // should never come here
//    } catch (FileNotFoundException e) {}
//
//    try {
//      List<String> attrNameList = Collections.singletonList(attrName1);
//      grfs.getXAttrs(noThisPath, attrNameList);
//      assertTrue(false); // should never come here
//    } catch (FileNotFoundException e) {}
//  }
//
//  @Test
//  public void testCanNotGetXAttrWhichNotExisted() throws IOException {
//    try {
//      grfs.getXAttr(path1, attrName1);
//      assertTrue(false); // should never come here
//    } catch (IOException e) {}
//
//    try {
//      List<String> attrNameList = Collections.singletonList(attrName1);
//      grfs.getXAttrs(path1, attrNameList);
//      assertTrue(false); // should never come here
//    } catch (IOException e) {}
//  }
//
//  @Test
//  public void testCanGetEmptyMapOfXAttrByPathIfThereIsNoAttr()
//          throws IOException {
//    assertTrue(grfs.getXAttrs(path1).isEmpty());
//  }
//
//  @Test
//  public void testCanNotGetXAttrWithNullPath() throws IOException {
//    try {
//      grfs.getXAttr(null, attrName1);
//      assertTrue(false); // should not come here
//    } catch (NullPointerException e) {}
//
//    try {
//      grfs.getXAttrs(null);
//      assertTrue(false); // should not come here
//    } catch (NullPointerException e) {}
//
//    try {
//      List<String> attrNameList = Collections.singletonList(attrName1);
//      grfs.getXAttrs(null, attrNameList);
//      assertTrue(false); // should never come here
//    } catch (NullPointerException e) {}
//  }
//
//  @Test (expected = NullPointerException.class)
//  public void testCanNotGetXAttrWithNullAttrName() throws IOException {
//    grfs.getXAttr(path1, null);
//  }
//
//  /**
//   * removeXAttr related tests
//   */
//  @Test
//  public void testCanRemoveAnAttr() throws IOException {
//    grfs.setXAttr(path1, attrName1, attrValue1);
//    grfs.removeXAttr(path1, attrName1);
//    assertEquals(0, grfs.listXAttrs(path1).size());
//  }
//
//  @Test
//  public void testCanRemoveAnAttrWithMultipleVersion() throws IOException {
//    grfs.setXAttr(path1, attrName1, attrValue1);
//    grfs.setXAttr(path1, attrName1, attrValue2);
//    grfs.removeXAttr(path1, attrName1);
//    assertEquals(0, grfs.listXAttrs(path1).size());
//  }
//
//  @Test
//  public void testCanRemoteTwoAttr() throws IOException {
//    grfs.setXAttr(path1, attrName1, attrValue1);
//    grfs.setXAttr(path1, attrName2, attrValue2);
//    grfs.setXAttr(path2, attrName3, attrValue3);
//    grfs.removeXAttr(path1, attrName1);
//    assertEquals(1, grfs.listXAttrs(path1).size());
//    assertEquals(1, grfs.listXAttrs(path2).size()); // not impact another path
//    grfs.removeXAttr(path1, attrName2);
//    assertEquals(0, grfs.listXAttrs(path1).size());
//    assertEquals(1, grfs.listXAttrs(path2).size()); // not impact another path
//    grfs.removeXAttr(path2, attrName3);
//    assertEquals(0, grfs.listXAttrs(path2).size());
//  }
//
//  @Test
//  public void testCanRemoveAnAttrAndThenAddAndThenRemove() throws IOException {
//    grfs.setXAttr(path1, attrName1, attrValue1);
//    grfs.removeXAttr(path1, attrName1);
//    grfs.setXAttr(path1, attrName1, attrValue2);
//    assertEquals(1, grfs.listXAttrs(path1).size());
//    assertArrayEquals(attrValue2, grfs.getXAttr(path1, attrName1));
//    grfs.removeXAttr(path1, attrName1);
//    assertEquals(0, grfs.listXAttrs(path1).size());
//  }
//
//  @Test (expected = NullPointerException.class)
//  public void testCanNotRemoveAnAttrWithNullPath() throws IOException {
//    grfs.removeXAttr(null, attrName1);
//  }
//
//  @Test (expected = FileNotFoundException.class)
//  public void testCanNotRemoveAnAttrOnNonExistedFile() throws IOException {
//    grfs.removeXAttr(noThisPath, attrName1);
//  }
//
//  @Test (expected = NullPointerException.class)
//  public void testCanNotRemoveAnAttrWithNullAttrName() throws IOException {
//    grfs.removeXAttr(path1, null);
//  }
//
//  @Test (expected = IOException.class)
//  public void testCanNotRemoveNonExistedAttr() throws IOException {
//    grfs.removeXAttr(path1, attrName1);
//  }


  /**
   * Helper methods
   */
  private void createFiles() throws IOException {
    // file 1
    path1 = new Path("aaa/bbb/ccc");
    FSDataOutputStream fsOutStream = grfs.create(path1,
            new FsPermission((short)0666), EnumSet.of(CREATE), 4096, (short)3,
            512, null);
    fsOutStream.close();

    // file 2
    path2 = new Path("aaa/bbb/ddd");
    fsOutStream = grfs.create(path2, new FsPermission((short)0666),
            EnumSet.of(CREATE), 4096, (short)3, 512, null);
    fsOutStream.close();

    // non-existed file
    noThisPath = new Path("noThisFile");
  }

  private void initAttributes() {
    final int valueSize = 10;
    // attr1
    attrName1 = "user.attr1";    // there's naming rule
    attrValue1 = new byte[valueSize]; // randomly select a size
    new Random().nextBytes(attrValue1);
    // attr2
    attrName2 = "user.attr2";    // there's naming rule
    attrValue2 = new byte[valueSize]; // randomly select a size
    new Random().nextBytes(attrValue2);
    // attr3
    attrName3 = "user.attr3";    // there's naming rule
    attrValue3 = new byte[valueSize]; // randomly select a size
    new Random().nextBytes(attrValue3);
    // attr4
    attrName4 = "user.attr4";    // there's naming rule
    attrValue4 = new byte[valueSize]; // randomly select a size
    new Random().nextBytes(attrValue4);
  }

  @Override // pass
  public void testCreateXAttr() throws Exception {
  }

  @Override // pass
  public void testReplaceXAttr() throws Exception {}

  @Override // pass
  public void testSetXAttr() throws Exception {

  }

//  @Override
  public void testGetXAttrs() throws Exception {
    FileSystem.mkdirs(this.fs, path, FsPermission.createImmutable((short)488));
    this.fs.setXAttr(path, "user.a1", value1, EnumSet.of(XAttrSetFlag.CREATE));
    this.fs.setXAttr(path, "user.a2", value2, EnumSet.of(XAttrSetFlag.CREATE));

    byte[] user;
    try {
      user = this.fs.getXAttr(path, "user.a3");
      Assert.fail("expected IOException");
    } catch (IOException var11) {
      GenericTestUtils.assertExceptionContains("At least one of the attributes provided was not found.", var11);
    }

    ArrayList user1 = Lists.newArrayList();
    user1.add("user.a1");
    user1.add("user.a2");
    user1.add("user.a3");

    try {
      this.fs.getXAttrs(path, user1);
      Assert.fail("expected IOException");
    } catch (IOException var10) {
      GenericTestUtils.assertExceptionContains("At least one of the attributes provided was not found.", var10);
    }

    this.fs.removeXAttr(path, "user.a1");
    this.fs.removeXAttr(path, "user.a2");

    try {
      user = this.fs.getXAttr(path, "wackynamespace.foo");
      Assert.fail("expected IOException");
    } catch (Exception var9) {
      GenericTestUtils.assertExceptionContains("An XAttr name must be prefixed with user/trusted/security/system, followed by a \'.\'", var9);
    }

    UserGroupInformation
        user2 = UserGroupInformation.createUserForTesting("user", new String[]{"mygroup"});
    this.fs.setXAttr(path, "trusted.foo", "1234".getBytes());

    try {
      user2.doAs(new PrivilegedExceptionAction() {
        public Object run() throws Exception {
          DistributedFileSystem
              userFs = FSXAttrBaseTest.dfsCluster.getFileSystem();
          byte[] xattr = userFs.getXAttr(FSXAttrBaseTest.path, "trusted.foo");
          return null;
        }
      });
      Assert.fail("expected IOException");
    } catch (IOException var8) {
      GenericTestUtils.assertExceptionContains("User doesn\'t have permission", var8);
    }

//    this.fs.setXAttr(path, "user.a1", "1234".getBytes());
//    this.fs.setPermission(path, new FsPermission((short)448));
//
//    try {
//      user2.doAs(new PrivilegedExceptionAction() {
//        public Object run() throws Exception {
//          DistributedFileSystem userFs = FSXAttrBaseTest.dfsCluster.getFileSystem();
//          byte[] xattr = userFs.getXAttr(FSXAttrBaseTest.path, "user.a1");
//          return null;
//        }
//      });
//      Assert.fail("expected IOException");
//    } catch (IOException var7) {
//      GenericTestUtils.assertExceptionContains("Permission denied", var7);
//    }

/*
    final Path childDir = new Path(path, "child" + pathCount);
    FileSystem.mkdirs(this.fs, childDir, FsPermission.createImmutable((short)448));
    this.fs.setXAttr(childDir, "user.a1", "1234".getBytes());

    try {
      user2.doAs(new PrivilegedExceptionAction() {
        public Object run() throws Exception {
          DistributedFileSystem userFs = FSXAttrBaseTest.dfsCluster.getFileSystem();
          userFs.getXAttr(childDir, "user.a1");
          return null;
        }
      });
      Assert.fail("expected IOException");
    } catch (IOException var6) {
      GenericTestUtils.assertExceptionContains("Permission denied", var6);
    }

    this.fs.setPermission(path, new FsPermission((short)452));

    try {
      user2.doAs(new PrivilegedExceptionAction() {
        public Object run() throws Exception {
          DistributedFileSystem userFs = FSXAttrBaseTest.dfsCluster.getFileSystem();
          userFs.getXAttr(childDir, "user.a1");
          return null;
        }
      });
      Assert.fail("expected IOException");
    } catch (IOException var5) {
      GenericTestUtils.assertExceptionContains("Permission denied", var5);
    }

    this.fs.setPermission(path, new FsPermission((short)449));
    this.fs.setPermission(childDir, new FsPermission((short)449));

    try {
      user2.doAs(new PrivilegedExceptionAction() {
        public Object run() throws Exception {
          DistributedFileSystem userFs = FSXAttrBaseTest.dfsCluster.getFileSystem();
          userFs.getXAttr(childDir, "user.a1");
          return null;
        }
      });
      Assert.fail("expected IOException");
    } catch (IOException var4) {
      GenericTestUtils.assertExceptionContains("Permission denied", var4);
    }

    this.fs.setPermission(path, new FsPermission((short)449));
    this.fs.setPermission(childDir, new FsPermission((short)452));
    user2.doAs(new PrivilegedExceptionAction() {
      public Object run() throws Exception {
        DistributedFileSystem userFs = FSXAttrBaseTest.dfsCluster.getFileSystem();
        userFs.getXAttr(childDir, "user.a1");
        return null;
      }
    });
   */
  }

  @Override
  public void testRemoveXAttr() throws Exception {}

  @Override
  public void testRemoveXAttrPermissions() throws Exception {}

  @Override
  public void testRenameFileWithXAttr() throws Exception {}

  @Override
  public void testListXAttrs() throws Exception {}

  @Override
  public void testCleanupXAttrs() throws Exception {}

  @Override
  public void testXAttrAcl() throws Exception {}

  // work around // TODO, delete this method
  void initFileSystem() throws Exception {
    // Do nothing.
  }

  public static void main(String[] args) throws Exception {
    TestXAttr test = new TestXAttr();
    GiraffaConfiguration conf =
      new GiraffaConfiguration(UTIL.getConfiguration());
    GiraffaFileSystem.format(conf, true);
    GiraffaTestUtils.setGiraffaURI(conf);
    grfs = (GiraffaFileSystem) FileSystem.get(conf);
    test.testCanAddXAttrToAFile();
  }
}
