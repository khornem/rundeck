/*
 * Copyright 2016 SimplifyOps, Inc. (http://simplifyops.com)
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

/*
* TestDirPluginScanner.java
* 
* User: Greg Schueler <a href="mailto:greg@dtosolutions.com">greg@dtosolutions.com</a>
* Created: 8/8/11 6:32 PM
* 
*/
package com.dtolabs.rundeck.core.plugins;

import com.dtolabs.rundeck.core.common.FrameworkSupportService;
import com.dtolabs.rundeck.core.execution.service.ProviderLoaderException;
import com.dtolabs.rundeck.core.utils.FileUtils;
import com.dtolabs.rundeck.core.utils.cache.FileCache;
import junit.framework.TestCase;
import spock.lang.Ignore;

import java.io.*;
import java.util.*;

/**
 * TestDirPluginScanner is ...
 *
 * @author Greg Schueler <a href="mailto:greg@dtosolutions.com">greg@dtosolutions.com</a>
 */
public class TestDirPluginScanner extends TestCase {
    File testdir;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        testdir = new File("build/TestDirPluginScanner");
        FileUtils.deleteDir(testdir);
        testdir.mkdirs();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        FileUtils.deleteDir(testdir);
    }

    public static class ManualTriggerDirPluginProvider implements PluginDirProvider {

        private final File extdir;
        private PluginDirChangeEventListener changeEventListener = null;

        ManualTriggerDirPluginProvider(File extdir) {
            this.extdir = extdir;
        }

        @Override
        public File getPluginDir() {
            return extdir;
        }

        @Override
        public void registerDirChangeEventListener(final PluginDirChangeEventListener changeEventListener) {
            this.changeEventListener = changeEventListener;
        }

        public void fireChange() {
            if(this.changeEventListener != null) this.changeEventListener.onDirChangeEvent(new PluginDirChangeEvent("ignore", PluginDirChangeType.UPDATE));
        }
    }

    public static class test extends DirPluginScanner {
        Map<File, String> versions;
        public ManualTriggerDirPluginProvider pluginDirProvider;
        test(ManualTriggerDirPluginProvider pluginDirProvider, FileCache<ProviderLoader> filecache, long rescanIntervalMs) {
            super(pluginDirProvider, filecache);
            this.pluginDirProvider = pluginDirProvider;
        }

        @Override
        public boolean isValidPluginFile(File file) {
            return file.getName().contains("_");
        }

        @Override
        public FileFilter getFileFilter() {
            return null;
        }

        public ProviderLoader createLoader(File file) {
            return new loader(file);
        }


        public ProviderLoader createCacheItemForFile(File file) {
            return new loader(file);
        }

        @Override
        protected String getVersionForFile(File file) {
            if(null!=versions) {
                return versions.get(file);
            }
            return null;
        }
    }

    static class testitem {

    }

    /**
     * Test loader. each file name of the form a_b-c_d is considered a provider for idents (a,b) and (c,d)
     */
    public static class loader implements ProviderLoader {
        File file;
        String[] names;
        HashSet<ProviderIdent> idents;

        public loader(File file) {
            this.file = file;
            names = file.getName().split("-");
            idents = new HashSet<ProviderIdent>();
            for (final String name : names) {
                String[] ab = name.split("_", 2);
                if (ab.length > 1) {
                    idents.add(new ProviderIdent(ab[0], ab[1]));
                }
            }
        }

        @Override
        public boolean canLoadForService(final FrameworkSupportService service) {
            return true;
        }

        public <T> T load(PluggableService<T> service, String providerName) throws ProviderLoaderException {
            return null;
        }

        @Override
        public <T> CloseableProvider<T> loadCloseable(final PluggableService<T> service, final String providerName)
                throws ProviderLoaderException
        {
            return null;
        }

        public boolean isLoaderFor(ProviderIdent ident) {


            return idents.contains(ident);
        }

        public List<ProviderIdent> listProviders() {
            return new ArrayList<ProviderIdent>(idents);
        }
    }

    public void testscanForFile() throws Exception {
        File basedir = new File(testdir, "testscanForFile");
        final FileCache<ProviderLoader> loaderFileCache = new FileCache<ProviderLoader>();
        test scanner = new test(new ManualTriggerDirPluginProvider(basedir), loaderFileCache, 0);

        assertFalse(basedir.isDirectory());
        assertFalse(basedir.exists());
        //directory dne
        final File file = scanner.scanForFile(new ProviderIdent("test1", "test"));

        assertNull(file);
        assertTrue(basedir.mkdirs());
        
        assertTrue("basedir does not exist: "+ basedir.getAbsolutePath(), basedir.isDirectory());
        assertTrue(basedir.exists());

        //empty directory
        final File file2 = scanner.scanForFile(new ProviderIdent("test1", "test"));
        assertNull(file2);

        //create test file
        File testfile1 = new File(basedir, "service_provider");
        assertTrue(testfile1.createNewFile());
        scanner.pluginDirProvider.fireChange();

        //file not matched
        final File test3 = scanner.scanForFile(new ProviderIdent("test1", "test"));
        assertNull(test3);

        assertTrue(testfile1.exists());
        assertTrue(testfile1.isFile());

        //matched file
        final File test4 = scanner.scanForFile(new ProviderIdent("service", "provider"));
        assertNotNull(test4);
        assertEquals(testfile1, test4);
        testfile1.delete();
        scanner.pluginDirProvider.fireChange();

        //delete file
        assertNull(scanner.scanForFile(new ProviderIdent("service", "provider")));


        //create invalid file
        File testfile2 = new File(basedir, "service-provider");
        assertTrue(testfile2.createNewFile());
        scanner.pluginDirProvider.fireChange();
        assertNull(scanner.scanForFile(new ProviderIdent("service", "provider")));

    }

    public void testlistProviders() throws Exception {

        File basedir = new File(testdir, "testlistProviders");
        basedir.mkdirs();
        final FileCache<ProviderLoader> loaderFileCache = new FileCache<ProviderLoader>();
        test scanner = new test(new ManualTriggerDirPluginProvider(basedir), loaderFileCache, 0);

        final List<ProviderIdent> providerIdents = scanner.listProviders();
        assertNotNull(providerIdents);
        assertEquals(0, providerIdents.size());

        File testfile1 = new File(basedir, "service_provider");
        assertTrue(testfile1.createNewFile());
        scanner.pluginDirProvider.fireChange();
        final List<ProviderIdent> idents2 = scanner.listProviders();
        assertNotNull(idents2);
        assertEquals(1, idents2.size());
        assertTrue(idents2.contains(new ProviderIdent("service", "provider")));


        File testfile2 = new File(basedir, "a_b-c_d");
        assertTrue(testfile2.createNewFile());
        scanner.pluginDirProvider.fireChange();

        final List<ProviderIdent> idents3 = scanner.listProviders();
        assertNotNull(idents3);
        assertEquals(3, idents3.size());
        assertTrue(idents3.contains(new ProviderIdent("service", "provider")));
        assertTrue(idents3.contains(new ProviderIdent("a", "b")));
        assertTrue(idents3.contains(new ProviderIdent("c", "d")));

        testfile1.delete();
        testfile2.delete();
        scanner.pluginDirProvider.fireChange();
        //create invalid file
        final List<ProviderIdent> idents4 = scanner.listProviders();
        assertNotNull(idents4);
        assertEquals(0, idents4.size());

        //create invalid file
        File testfile3 = new File(basedir, "service-provider");
        assertTrue(testfile3.createNewFile());
        scanner.pluginDirProvider.fireChange();
        final List<ProviderIdent> idents5 = scanner.listProviders();
        assertNotNull(idents5);
        assertEquals(0, idents5.size());

    }

    public void testIsExpired() throws Exception {
        //test that removing a file, or modifying it, expires the cached item
        File basedir = new File(testdir, "testIsExpired");
        basedir.mkdirs();
        final FileCache<ProviderLoader> loaderFileCache = new FileCache<ProviderLoader>();
        test scanner = new test(new ManualTriggerDirPluginProvider(basedir), loaderFileCache, 0);

        File testfile1 = new File(basedir, "service_provider");
        assertTrue(testfile1.createNewFile());
        File testfile2 = new File(basedir, "service2_provider");
        assertTrue(testfile2.createNewFile());

        final ProviderIdent ident = new ProviderIdent("service", "provider");
        final ProviderIdent ident2 = new ProviderIdent("service2", "provider");

        assertEquals(testfile1, scanner.scanForFile(ident));
        assertEquals(testfile2, scanner.scanForFile(ident2));

        assertFalse(scanner.isExpired(ident, testfile1));
        assertFalse(scanner.isExpired(ident2, testfile2));

        //test delete
        assertTrue(testfile1.delete());
        assertTrue(scanner.isExpired(ident, testfile1));
        assertFalse(scanner.isExpired(ident2, testfile2));

        //modify file
        final FileOutputStream fileOutputStream = new FileOutputStream(testfile2);
        fileOutputStream.write("blah".getBytes());
        fileOutputStream.close();

        assertTrue(scanner.isExpired(ident2, testfile2));
        assertEquals(testfile2, scanner.scanForFile(ident2));
        assertFalse(scanner.isExpired(ident2, testfile2));
    }





    public void testScanForResolveConflict() throws Exception {

        //set scan interval to 60 seconds, shouldRescan should now return false
        File basedir = new File(testdir, "testScanForResolveConflict");
        basedir.mkdirs();
        final FileCache<ProviderLoader> loaderFileCache = new FileCache<ProviderLoader>();

        //scan interval set to 60 seconds
        test scanner = new test(new ManualTriggerDirPluginProvider(basedir), loaderFileCache, 60 * 1000);

        final Map<File, String> versions = new HashMap<File, String>();

        File testfile1 = touchFile(basedir, "test1-service_provider1");
        File testfile2 = touchFile(basedir, "test2-service_provider1");
        File testfile3 = touchFile(basedir, "test3-service_provider1");
        versions.put(testfile1, "1.6");
        versions.put(testfile2, "1.22");
        versions.put(testfile3, "1.8");

        ArrayList<File> arr = new ArrayList<File>();
        arr.add(testfile1);
        arr.add(testfile2);
        scanner.versions = versions;

        final File file = scanner.scanForFile(new ProviderIdent("service", "provider1"));
        assertEquals(testfile2, file);
    }
    public void testScanForConflict() throws Exception {

        //set scan interval to 60 seconds, shouldRescan should now return false
        File basedir = new File(testdir, "testScanForResolveConflict");
        basedir.mkdirs();
        final FileCache<ProviderLoader> loaderFileCache = new FileCache<ProviderLoader>();

        //scan interval set to 60 seconds
        test scanner = new test(new ManualTriggerDirPluginProvider(basedir), loaderFileCache, 60 * 1000);

        final Map<File, String> versions = new HashMap<File, String>();

        File testfile1 = touchFile(basedir, "test1-service_provider1");
        File testfile2 = touchFile(basedir, "test2-service_provider1");
        File testfile3 = touchFile(basedir, "test3-service_provider1");
        File testfile4 = touchFile(basedir, "test4-service_provider1");
        versions.put(testfile1, "1.6");
        versions.put(testfile2, "1.22");
        versions.put(testfile3, "1.22");
        versions.put(testfile4, "1.8");

        scanner.versions = versions;

        final File file = scanner.scanForFile(new ProviderIdent("service", "provider1"));
        assertEquals(testfile3, file);
    }

    private File touchFile(final File basedir, final String name) throws IOException {
        File file = new File(basedir, name);
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write("test".getBytes());
        }
        file.deleteOnExit();
        return file;
    }

}
