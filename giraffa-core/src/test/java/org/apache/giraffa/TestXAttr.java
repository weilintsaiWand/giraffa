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
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.apache.hadoop.security.UserGroupInformation.createUserForTesting;
import static org.apache.hadoop.fs.CreateFlag.CREATE;
import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_NAMENODE_MAX_XATTR_SIZE_KEY;
import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_NAMENODE_MAX_XATTRS_PER_INODE_KEY;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import mockit.Mock;
import mockit.MockUp;

/**
 * This file is dedicated to test extended attribute(XAttr) related methods
 * of GiraffaFileSystem.
 * Leverage legacy tests in FSXAttrBaseTest except testXAttrAcl since ACL
 * related methods are not supported now
 */
public class TestXAttr extends FSXAttrBaseTest {
  static final Log LOG = LogFactory.getLog(TestXAttr.class);

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
  // need another user since default one is super user
  private UserGroupInformation user1;
  private Path user1Path;

  private class MockMiniDFSCluster extends MiniDFSCluster {
    private MockDistributedFileSystem dfs;

    public MockMiniDFSCluster() {
      dfs = new MockDistributedFileSystem();
    }

    @Override
    public DistributedFileSystem getFileSystem() throws IOException{
      return dfs;
    }
  }

  private class MockDistributedFileSystem extends DistributedFileSystem {
/*
    @Override
    public void setXAttr(Path path, String name, byte[] value)
        throws IOException {
      getFS().setXAttr(path, name, value);
    }
*/
    @Override
    public byte[] getXAttr(Path path, final String name) throws IOException {
      return getFS().getXAttr(path, name);
    }

    @Override
    public void removeXAttr(Path path, final String name) throws IOException {
      getFS().removeXAttr(path, name);
    }
    @Override
    public List<String> listXAttrs(Path path) throws IOException {
      return getFS().listXAttrs(path);
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
    setupForOtherUsers();
    initAttributes();
    fs = grfs;   // replace fs in FSXAttrBaseTest with grfs
    dfsCluster = new  MockMiniDFSCluster(); //
  }

  @After
  public void after() throws IOException {
    dfsCluster = null;
    IOUtils.cleanup(LOG, grfs);
  }

  @AfterClass
  public static void afterClass() throws Exception {
    UTIL.shutdownMiniCluster();
  }

  @Override
  public void testXAttrAcl() throws Exception {
    // does not support ACL related methods so replace old one with empty test
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

  @Test
  public void testCanAddMultipleXAttrToSameFile() throws IOException {
    grfs.setXAttr(path1, attrName1, attrValue1);
    grfs.setXAttr(path1, attrName2, attrValue2);
    List<String> listOfXAttrNames = grfs.listXAttrs(path1);
    assertEquals(2, listOfXAttrNames.size());
    assertTrue(listOfXAttrNames.contains(attrName1));
    assertTrue(listOfXAttrNames.contains(attrName2));
  }

  @Test
  public void testCanAddXAttrToDifferentFile() throws IOException {
    grfs.setXAttr(path1, attrName1, attrValue1);
    grfs.setXAttr(path2, attrName2, attrValue2);
    List<String> listOfXAttrNames1 = grfs.listXAttrs(path1);
    List<String> listOfXAttrNames2 = grfs.listXAttrs(path2);
    assertEquals(1, listOfXAttrNames1.size());
    assertTrue(listOfXAttrNames1.contains(attrName1));
    assertEquals(1, listOfXAttrNames2.size());
    assertTrue(listOfXAttrNames2.contains(attrName2));
  }

  @Test (expected = FileNotFoundException.class)
  public void testCannotAddXAttrToNonExistedFile()
          throws IOException {
    grfs.setXAttr(noThisPath, attrName1, attrValue1);
  }

  @Test (expected = NullPointerException.class)
  public void testCanNotSetAttrWithNullPath() throws IOException {
    try {
      grfs.setXAttr(null, attrName1, attrValue1);
      assertTrue(false);  // should not come here
    } finally {
      List<String> listOfXAttrNames = grfs.listXAttrs(path1);
      assertEquals(0, listOfXAttrNames.size());
    }
  }

  @Test (expected = NullPointerException.class)
  public void testCanNotSetAttrWithNullAttrName() throws IOException {
    try {
      grfs.setXAttr(path1, null, attrValue1);
      assertTrue(false);  // should not come here
    } finally {
      List<String> listOfXAttrNames = grfs.listXAttrs(path1);
      assertEquals(0, listOfXAttrNames.size());
    }
  }

  @Test
  public void testCanSetAttrWithNullAttrValue() throws IOException {
    grfs.setXAttr(path1, attrName1, null);
    List<String> listOfXAttrNames = grfs.listXAttrs(path1);
    assertEquals(1, listOfXAttrNames.size());
    assertTrue(listOfXAttrNames.contains(attrName1));
    assertArrayEquals(new byte[0], grfs.getXAttr(path1, attrName1));
  }

  @Test
  public void testCanOverwriteAttrWithoutAdditionalFlag() throws IOException {
    grfs.setXAttr(path1, attrName1, attrValue1);
    grfs.setXAttr(path1, attrName1, attrValue2);
    List<String> listOfXAttrNames = grfs.listXAttrs(path1);
    assertEquals(1, listOfXAttrNames.size());
    assertTrue(listOfXAttrNames.contains(attrName1));
    assertArrayEquals(attrValue2, grfs.getXAttr(path1, attrName1));
  }

  @Test
  public void testCanOverwriteAttrWithoutDestroyOtherAttr() throws IOException {
    grfs.setXAttr(path1, attrName1, attrValue1);
    grfs.setXAttr(path1, attrName2, attrValue2);
    grfs.setXAttr(path1, attrName1, attrValue3);
    List<String> listOfXAttrNames = grfs.listXAttrs(path1);
    assertEquals(2, listOfXAttrNames.size());
    assertTrue(listOfXAttrNames.contains(attrName1));
    assertTrue(listOfXAttrNames.contains(attrName2));
    assertArrayEquals(attrValue3, grfs.getXAttr(path1, attrName1));
    assertArrayEquals(attrValue2, grfs.getXAttr(path1, attrName2));
  }

  @Test
  public void testAttrPrefixIsCaseInsensitive() throws IOException {
    String caseInsensitiveName = "uSeR.attr1";
    grfs.setXAttr(path1, attrName1, attrValue1);
    grfs.setXAttr(path1, caseInsensitiveName, attrValue2);
    List<String> listOfXAttrNames = grfs.listXAttrs(path1);
    assertEquals(1, listOfXAttrNames.size());
    assertTrue(listOfXAttrNames.contains(attrName1));
    assertArrayEquals(attrValue2, grfs.getXAttr(path1, caseInsensitiveName));
  }

  @Test (expected = IOException.class)
  public void testCanNotOverwriteAttrWithoutReplaceFlag() throws IOException {
    grfs.setXAttr(path1, attrName1, attrValue1);
    try {
      grfs.setXAttr(path1, attrName1, attrValue2,
              EnumSet.of(XAttrSetFlag.CREATE));
      assertTrue(false);  // should not come here
    } finally {
      List<String> listOfXAttrNames = grfs.listXAttrs(path1);
      assertEquals(1, listOfXAttrNames.size());
      assertTrue(listOfXAttrNames.contains(attrName1));
      assertArrayEquals(attrValue1, grfs.getXAttr(path1, attrName1));
    }
  }

  @Test
  public void testCanAddAttrWithCreateFlagOnly() throws IOException {
    grfs.setXAttr(path1, attrName1, attrValue2,
              EnumSet.of(XAttrSetFlag.CREATE));
    List<String> listOfXAttrNames = grfs.listXAttrs(path1);
    assertEquals(1, listOfXAttrNames.size());
    assertTrue(listOfXAttrNames.contains(attrName1));
  }

  @Test
  public void testCanOverwriteAttrWithReplaceFlagOnly() throws IOException {
    grfs.setXAttr(path1, attrName1, attrValue1);
    grfs.setXAttr(path1, attrName1, attrValue2,
            EnumSet.of(XAttrSetFlag.REPLACE));
    List<String> listOfXAttrNames = grfs.listXAttrs(path1);
    assertEquals(1, listOfXAttrNames.size());
    assertTrue(listOfXAttrNames.contains(attrName1));
    assertArrayEquals(attrValue2, grfs.getXAttr(path1, attrName1));
  }

  @Test (expected = IOException.class)
  public void testCanNotAddNewAttrWithReplaceFlagOnly() throws IOException {
    try {
      grfs.setXAttr(path1, attrName1, attrValue2,
              EnumSet.of(XAttrSetFlag.REPLACE));
      assertTrue(false); // should not come here
    } finally {
      List<String> listOfXAttrNames = grfs.listXAttrs(path1);
      assertEquals(0, listOfXAttrNames.size());
    }
  }

  @Test (expected = NullPointerException.class)
  public void testCanNotAddNewAttrWithNullFlag() throws IOException {
    try {
      grfs.setXAttr(path1, attrName1, attrValue2, null);
      assertTrue(false); // should not come here
    } finally {
      List<String> listOfXAttrNames = grfs.listXAttrs(path1);
      assertEquals(0, listOfXAttrNames.size());
    }
  }

  @Test (expected = IOException.class)
  public void testCanNotAddNewAttrWithEmptyFlag() throws IOException {
    try {
      grfs.setXAttr(path1, attrName1, attrValue2,
              EnumSet.noneOf(XAttrSetFlag.class));
      assertTrue(false); // should not come here
    } finally {
      List<String> listOfXAttrNames = grfs.listXAttrs(path1);
      assertEquals(0, listOfXAttrNames.size());
    }
  }

  /**
   * listXAttr related Tests
   */
  @Test
  public void testCanListEmptyAttrList() throws IOException {
    List<String> listOfXAttrNames = grfs.listXAttrs(path1);
    assertEquals(0, listOfXAttrNames.size());
  }

  @Test
  public void testCanListOneAttr() throws IOException {
    grfs.setXAttr(path1, attrName1, attrValue1);
    List<String> listOfXAttrNames = grfs.listXAttrs(path1);
    assertEquals(1, listOfXAttrNames.size());
    assertEquals(attrName1, listOfXAttrNames.get(0));
  }

  @Test
  public void testCanListMultipleAttr() throws IOException {
    grfs.setXAttr(path1, attrName1, attrValue1);
    grfs.setXAttr(path1, attrName2, attrValue2);
    List<String> listOfXAttrNames = grfs.listXAttrs(path1);
    assertEquals(2, listOfXAttrNames.size());
    assertTrue(listOfXAttrNames.contains(attrName1));
    assertTrue(listOfXAttrNames.contains(attrName2));
  }

  @Test (expected = FileNotFoundException.class)
  public void testCanLisAttrOnNonExistedFile() throws IOException {
    grfs.listXAttrs(noThisPath);
  }

  /**
   * getXAttr related tests
   */
  @Test
  public void testCanGetXAttrValueByPathAndXAttrName() throws IOException {
    grfs.setXAttr(path1, attrName1, attrValue1);
    assertArrayEquals(attrValue1, grfs.getXAttr(path1, attrName1));
  }

  @Test
  public void testCanGetMapOfXAttrByPath() throws IOException {
    grfs.setXAttr(path1, attrName1, attrValue1);
    Map<String, byte[]> map = grfs.getXAttrs(path1);
    assertEquals(1, map.size());
    assertTrue(map.containsKey(attrName1));
    assertArrayEquals(attrValue1, map.get(attrName1));
  }

  @Test
  public void testCanGetMapOfXAttrByPathAndSingleElementAttrNameList()
          throws IOException {
    // one to one match
    grfs.setXAttr(path1, attrName1, attrValue1);
    List<String> attrNameList = Collections.singletonList(attrName1);
    Map<String, byte[]> map = grfs.getXAttrs(path1, attrNameList);
    assertEquals(1, map.size());
    assertTrue(map.containsKey(attrName1));
    assertArrayEquals(attrValue1, map.get(attrName1));

    grfs.setXAttr(path1, attrName2, attrValue2);

    // now we have two attributes
    map = grfs.getXAttrs(path1, attrNameList);
    assertEquals(1, map.size());
    assertTrue(map.containsKey(attrName1));
    assertArrayEquals(attrValue1, map.get(attrName1));

    attrNameList = Collections.singletonList(attrName2);
    map = grfs.getXAttrs(path1, attrNameList);
    assertEquals(1, map.size());
    assertTrue(map.containsKey(attrName2));
    assertArrayEquals(attrValue2, map.get(attrName2));
  }

  @Test (expected = HadoopIllegalArgumentException.class)
  public void testCanNotGetMapOfXAttrByPathAndEmptyAttrNameList()
          throws IOException {
    grfs.setXAttr(path1, attrName1, attrValue1);
    List<String> attrNameList = Collections.emptyList();
    grfs.getXAttrs(path1, attrNameList);
  }

  @Test (expected = HadoopIllegalArgumentException.class)
  public void testCanNotGetMapOfXAttrByPathAndNullNameList()
          throws IOException {
    grfs.setXAttr(path1, attrName1, attrValue1);
    grfs.getXAttrs(path1, null);
  }

  @Test
  public void testCanGetMapOfXAttrByPathAndMultipleElementsAttrNameList()
          throws IOException {
    grfs.setXAttr(path1, attrName1, attrValue1);
    grfs.setXAttr(path1, attrName2, attrValue2);
    grfs.setXAttr(path1, attrName3, attrValue3);
    grfs.setXAttr(path2, attrName4, attrValue4);
    // check if interfere with each other
    List<String> attrNameList = Collections.singletonList(attrName4);
    Map<String, byte[]> map = grfs.getXAttrs(path2, attrNameList);
    assertEquals(1, map.size());
    assertTrue(map.containsKey(attrName4));
    assertArrayEquals(attrValue4, map.get(attrName4));

    // check if partial match
    attrNameList = Arrays.asList(attrName2, attrName3);
    map = grfs.getXAttrs(path1, attrNameList);
    assertEquals(2, map.size());
    assertTrue(map.containsKey(attrName2));
    assertArrayEquals(attrValue2, map.get(attrName2));
    assertTrue(map.containsKey(attrName3));
    assertArrayEquals(attrValue3, map.get(attrName3));

    // check if full match
    attrNameList = Arrays.asList(attrName1, attrName2, attrName3);
    map = grfs.getXAttrs(path1, attrNameList);
    assertEquals(3, map.size());
    assertTrue(map.containsKey(attrName1));
    assertArrayEquals(attrValue1, map.get(attrName1));
    assertTrue(map.containsKey(attrName2));
    assertArrayEquals(attrValue2, map.get(attrName2));
    assertTrue(map.containsKey(attrName3));
    assertArrayEquals(attrValue3, map.get(attrName3));
  }

  @Test
  public void testCanGetXAttrValueMultipleTimes() throws IOException {
    grfs.setXAttr(path1, attrName1, attrValue1);
    grfs.setXAttr(path1, attrName2, attrValue2);
    grfs.setXAttr(path2, attrName1, attrValue3);

    // byte[] getXAttr(String src, String name)
    assertArrayEquals(attrValue1, grfs.getXAttr(path1, attrName1));
    assertArrayEquals(attrValue2, grfs.getXAttr(path1, attrName2));
    assertArrayEquals(attrValue3, grfs.getXAttr(path2, attrName1));

    // Map<String, byte[]> getXAttrs(Path path)
    Map<String, byte[]> map1 =  grfs.getXAttrs(path1);
    assertEquals(2, map1.size());
    assertTrue(map1.containsKey(attrName1));
    assertTrue(map1.containsKey(attrName2));
    assertArrayEquals(attrValue1, map1.get(attrName1));
    assertArrayEquals(attrValue2, map1.get(attrName2));
    Map<String, byte[]> map2 =  grfs.getXAttrs(path2);
    assertEquals(1, map2.size());
    assertTrue(map2.containsKey(attrName1));
    assertArrayEquals(attrValue3, map2.get(attrName1));

    // Map<String, byte[]> getXAttrs(String src, List<String> names)
    // It's in testCanGetMapOfXAttrByPathAndMultipleElementsAttrNameList
  }

  @Test
  public void testCanNotGetXAttrFromNonExistedFile() throws IOException {
    try {
      grfs.getXAttr(noThisPath, attrName1);
      assertTrue(false); // should never come here
    } catch (FileNotFoundException e) {}

    try {
      grfs.getXAttrs(noThisPath);
      assertTrue(false); // should never come here
    } catch (FileNotFoundException e) {}

    try {
      List<String> attrNameList = Collections.singletonList(attrName1);
      grfs.getXAttrs(noThisPath, attrNameList);
      assertTrue(false); // should never come here
    } catch (FileNotFoundException e) {}
  }

  @Test
  public void testCanNotGetXAttrWhichNotExisted() throws IOException {
    try {
      grfs.getXAttr(path1, attrName1);
      assertTrue(false); // should never come here
    } catch (IOException e) {}

    try {
      List<String> attrNameList = Collections.singletonList(attrName1);
      grfs.getXAttrs(path1, attrNameList);
      assertTrue(false); // should never come here
    } catch (IOException e) {}
  }

  @Test
  public void testCanGetEmptyMapOfXAttrByPathIfThereIsNoAttr()
          throws IOException {
    assertTrue(grfs.getXAttrs(path1).isEmpty());
  }

  @Test
  public void testCanNotGetXAttrWithNullPath() throws IOException {
    try {
      grfs.getXAttr(null, attrName1);
      assertTrue(false); // should not come here
    } catch (NullPointerException e) {}

    try {
      grfs.getXAttrs(null);
      assertTrue(false); // should not come here
    } catch (NullPointerException e) {}

    try {
      List<String> attrNameList = Collections.singletonList(attrName1);
      grfs.getXAttrs(null, attrNameList);
      assertTrue(false); // should never come here
    } catch (NullPointerException e) {}
  }

  @Test (expected = NullPointerException.class)
  public void testCanNotGetXAttrWithNullAttrName() throws IOException {
    grfs.getXAttr(path1, null);
  }

  /**
   * removeXAttr related tests
   */
  @Test
  public void testCanRemoveAnAttr() throws IOException {
    grfs.setXAttr(path1, attrName1, attrValue1);
    grfs.removeXAttr(path1, attrName1);
    assertEquals(0, grfs.listXAttrs(path1).size());
  }

  @Test
  public void testCanRemoveAnAttrWithMultipleVersion() throws IOException {
    grfs.setXAttr(path1, attrName1, attrValue1);
    grfs.setXAttr(path1, attrName1, attrValue2);
    grfs.removeXAttr(path1, attrName1);
    assertEquals(0, grfs.listXAttrs(path1).size());
  }

  @Test
  public void testCanRemoteTwoAttr() throws IOException {
    grfs.setXAttr(path1, attrName1, attrValue1);
    grfs.setXAttr(path1, attrName2, attrValue2);
    grfs.setXAttr(path2, attrName3, attrValue3);
    grfs.removeXAttr(path1, attrName1);
    assertEquals(1, grfs.listXAttrs(path1).size());
    assertEquals(1, grfs.listXAttrs(path2).size()); // not impact another path
    grfs.removeXAttr(path1, attrName2);
    assertEquals(0, grfs.listXAttrs(path1).size());
    assertEquals(1, grfs.listXAttrs(path2).size()); // not impact another path
    grfs.removeXAttr(path2, attrName3);
    assertEquals(0, grfs.listXAttrs(path2).size());
  }

  @Test
  public void testCanRemoveAnAttrAndThenAddAndThenRemove() throws IOException {
    grfs.setXAttr(path1, attrName1, attrValue1);
    grfs.removeXAttr(path1, attrName1);
    grfs.setXAttr(path1, attrName1, attrValue2);
    assertEquals(1, grfs.listXAttrs(path1).size());
    assertArrayEquals(attrValue2, grfs.getXAttr(path1, attrName1));
    grfs.removeXAttr(path1, attrName1);
    assertEquals(0, grfs.listXAttrs(path1).size());
  }

  @Test (expected = NullPointerException.class)
  public void testCanNotRemoveAnAttrWithNullPath() throws IOException {
    grfs.removeXAttr(null, attrName1);
  }

  @Test (expected = FileNotFoundException.class)
  public void testCanNotRemoveAnAttrOnNonExistedFile() throws IOException {
    grfs.removeXAttr(noThisPath, attrName1);
  }

  @Test (expected = NullPointerException.class)
  public void testCanNotRemoveAnAttrWithNullAttrName() throws IOException {
    grfs.removeXAttr(path1, null);
  }

  @Test (expected = IOException.class)
  public void testCanNotRemoveNonExistedAttr() throws IOException {
    grfs.removeXAttr(path1, attrName1);
  }

  /**
   * permission related Tests
   */
  @Test
  public void testOnlySuperUserCanSetTRUSTEDXAttr() throws Exception {
    grfs.setXAttr(path2, "trusted.a2", attrValue2);
    assertEquals(1, grfs.listXAttrs(path2).size());

    user1.doAs(new PrivilegedExceptionAction() {
      public Object run() throws Exception {
        GiraffaFileSystem userFs = getFS();
        createEmptyFile(userFs, path1);
        try {
          userFs.setXAttr(path1, "trusted.a1", attrValue2);
          Assert.fail("expected IOException");
        } catch (IOException e) {
          GenericTestUtils.
              assertExceptionContains("User doesn\'t have permission", e);
        }
        assertEquals(0, userFs.listXAttrs(path1).size());
        return null;
      }
    });
  }

  @Test
  public void testNoUserCanSetSYSTEMXAttr() throws Exception {
    try {
      grfs.setXAttr(path2, "system.a2", attrValue2);
      Assert.fail("expected IOException");
    } catch (IOException e) {
      GenericTestUtils.
          assertExceptionContains("User doesn\'t have permission", e);
    }
    assertEquals(0, grfs.listXAttrs(path2).size());

    user1.doAs(new PrivilegedExceptionAction() {
      public Object run() throws Exception {
        GiraffaFileSystem userFs = getFS();
        createEmptyFile(userFs, path1);
        try {
          userFs.setXAttr(path1, "system.a1", attrValue1);
          Assert.fail("expected IOException");
        } catch (IOException e) {
          GenericTestUtils.
              assertExceptionContains("User doesn\'t have permission", e);
        }
        assertEquals(0, userFs.listXAttrs(path1).size());
        return null;
      }
    });
  }

  @Test
  public void testNoUserCanSetSECURITYAttr() throws Exception {
    try {
      grfs.setXAttr(path2, "security.a2", attrValue2);
      Assert.fail("expected IOException");
    } catch (IOException e) {
      GenericTestUtils.
          assertExceptionContains("User doesn\'t have permission", e);
    }
    assertEquals(0, grfs.listXAttrs(path2).size());

    user1.doAs(new PrivilegedExceptionAction() {
      public Object run() throws Exception {
        GiraffaFileSystem userFs = getFS();
        createEmptyFile(userFs, path1);
        try {
          userFs.setXAttr(path1, "security.a1", attrValue1);
          Assert.fail("expected IOException");
        } catch (IOException e) {
          GenericTestUtils.
              assertExceptionContains("User doesn\'t have permission", e);
        }
        assertEquals(0, userFs.listXAttrs(path1).size());
        return null;
      }
    });
  }

  @Test
  public void testCanNotSetXAttrWithoutWPermission() throws Exception {
    user1.doAs(new PrivilegedExceptionAction() {
      public Object run() throws Exception {
        GiraffaFileSystem userFs = getFS();
        createEmptyFile(userFs, path1);
        userFs.setPermission(path1,
                             FsPermission.createImmutable((short) 352));
        try {
          userFs.setXAttr(path1, attrName1, attrValue1);
          Assert.fail("expected IOException");
        } catch (IOException e) {
          GenericTestUtils.assertExceptionContains("Permission denied", e);
        }

        return null;
      }
    });
    assertEquals(0, grfs.listXAttrs(user1Path).size());
  }

    @Test
  public void testCanNotSetXAttrWithoutParentXPermission() throws Exception {
    user1.doAs(new PrivilegedExceptionAction() {
      public Object run() throws Exception {
        GiraffaFileSystem userFs = getFS();
        createEmptyFile(userFs, path1);

        // remove parent node's X permission
        Path path1Parent = new Path("aaa/bbb");
        userFs.setPermission(path1Parent,
                             FsPermission.createImmutable((short) 384));
        try {
          userFs.setXAttr(path1, attrName1, attrValue1);
          Assert.fail("expected IOException");
        } catch (IOException e) {
          GenericTestUtils.assertExceptionContains("Permission denied", e);
        }
        return null;
      }
    });
    assertEquals(0, grfs.listXAttrs(user1Path).size());
  }


  @Test
  public void testOnlySuperUserCanGetOrListTRUSTEDXAttr() throws Exception {
    user1.doAs(new PrivilegedExceptionAction() {
      public Object run() throws Exception {
        GiraffaFileSystem userFs = getFS();
        createEmptyFile(userFs, path1);
        userFs.setXAttr(path1, attrName1, attrValue1);
        assertEquals(1, userFs.getXAttrs(path1).size());
        assertEquals(1, userFs.listXAttrs(path1).size());
        return null;
      }
    });
    // use super user to set trusted xattr
    grfs.setXAttr(user1Path, "trusted.a1", attrValue2);

    user1.doAs(new PrivilegedExceptionAction() {
      public Object run() throws Exception {
        assertEquals(1, getFS().getXAttrs(path1).size());
        assertEquals(1, getFS().listXAttrs(path1).size());
        return null;
      }
    });

    assertEquals(2, grfs.getXAttrs(user1Path).size());
    assertEquals(2, grfs.listXAttrs(user1Path).size());
  }

  @Test
  public void testCanNotRemoveXAttrWithoutWPermission() throws Exception {
    user1.doAs(new PrivilegedExceptionAction() {
      public Object run() throws Exception {
        GiraffaFileSystem userFs = getFS();
        createEmptyFile(userFs, path1);
        userFs.setXAttr(path1, attrName1, attrValue1);
        userFs.setPermission(path1,
                             FsPermission.createImmutable((short) 352));
        try {
          userFs.removeXAttr(path1, attrName1);
          Assert.fail("expected IOException");
        } catch (IOException e) {
          GenericTestUtils.assertExceptionContains("Permission denied", e);
        }

        return null;
      }
    });
    assertEquals(1, grfs.listXAttrs(user1Path).size());
  }

  @Test
  public void testCanNotRemoveXAttrWithoutParentXPermission() throws Exception {
    user1.doAs(new PrivilegedExceptionAction() {
      public Object run() throws Exception {
        GiraffaFileSystem userFs = getFS();
        createEmptyFile(userFs, path1);
        userFs.setXAttr(path1, attrName1, attrValue1);

        // remove parent node's X permission
        Path path1Parent = new Path("aaa/bbb");
        userFs.setPermission(path1Parent,
                             FsPermission.createImmutable((short) 384));
        try {
          userFs.removeXAttr(path1, attrName1);
          Assert.fail("expected IOException");
        } catch (IOException e) {
          GenericTestUtils.assertExceptionContains("Permission denied", e);
        }
        return null;
      }
    });
    assertEquals(1, grfs.listXAttrs(user1Path).size());
  }

  /**
   * Helper methods
   */
  private void createFiles() throws IOException {
    final short permissionVal = 480; // 740
    // file 1
    path1 = new Path("aaa/bbb/ccc");
    createEmptyFile(grfs, path1);

    // file 2
    path2 = new Path("aaa/bbb/ddd");
    createEmptyFile(grfs, path2);

    // non-existed file
    noThisPath = new Path("noThisFile");
  }

  private void createEmptyFile(FileSystem fs, Path path) throws IOException {
    final short permissionVal = 480; // 740
    FSDataOutputStream fsOutStream = fs.create(path,
                   new FsPermission(permissionVal), EnumSet.of(CREATE),
                                                 4096, (short)3, 512, null);
    fsOutStream.close();
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

  private void setupForOtherUsers() throws IOException {
    user1 = createUserForTesting("user1", new String[]{"mygroup"});
    user1Path = new Path("/user/user1/" + path1.toUri().getPath());
    Path user1Root = new Path ("/user/user1");
    grfs.mkdirs(user1Root);
    grfs.setOwner(user1Root, "user1", "mygroup");
  }

  private GiraffaFileSystem getFS() throws IOException {
    GiraffaConfiguration tmpConf =
        new GiraffaConfiguration(UTIL.getConfiguration());
    GiraffaTestUtils.setGiraffaURI(tmpConf);
    return (GiraffaFileSystem) FileSystem.get(tmpConf);
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
