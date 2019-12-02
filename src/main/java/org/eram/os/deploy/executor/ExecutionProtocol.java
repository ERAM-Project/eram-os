package org.eram.os.deploy.executor;

import android.content.Context;
import android.util.Log;

import org.eram.common.Messages;
import org.eram.common.ResultContainer;
import org.eram.common.Utils;
import org.eram.common.settings.Constants;
import org.eram.core.app.Task;
import org.eram.os.communication.stream.ERAMInputStream;
import org.eram.os.deploy.ServiceHandler;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dalvik.system.DexClassLoader;

public class ExecutionProtocol implements OSProtocol{

    private static final  String TAG = "OS-Executor";

    private Context context;

    private String appName; // the app name sent by the phone
    private int appLength; // the app length in bytes sent by the phone
    private String apkFilePath; // the path where the apk is installed

    private static Map<String, CountDownLatch> apkMapSemaphore = new ConcurrentHashMap<>(); // appName, latch
    private static Map<String, Integer> apkMap = new ConcurrentHashMap<>(); // appName, apkSize


    private static final AtomicInteger nrTasksCurrentlyBeingExecuted = new AtomicInteger(0);
    private static final Object syncLibrariesExtractObject = new Object(); // sync libraries extraction
    private static AtomicBoolean migrationInProgress = new AtomicBoolean(false);

    private List<File> libraries = new LinkedList<>();
    private static Map<String, Map<String, Integer>> librariesIndex = new ConcurrentHashMap<>();


    private DexClassLoader currentDexLoader;

    private ExecutorService executor;

    public ExecutionProtocol(Context context) {

        this.context = context;

        this.executor = Executors.newFixedThreadPool(1);
    }

    @Override
    public void recieveCodeSource(ERAMInputStream input, ObjectOutputStream output) {

        try {
            Log.d(TAG, "Registering apk");

            appName = (String) input.readObject();
            Log.i(TAG, "apk name: " + appName);

            appLength = input.readInt();
            apkFilePath = context.getFilesDir().getAbsolutePath() + "/" + appName + ".apk";
            Log.d(TAG, "Registering apk: " + appName + " of size: " + appLength + " bytes");
            if (checkSourceCodeAPK(apkFilePath, appName, appLength)) {
                Log.d(TAG, "APK present");
                output.write(Messages.I_HAVE_SOURCE_CODE);
            } else {
                Log.d(TAG, "Request APK");
                output.write(Messages.I_NEED_SOURCE_CODE);
                output.flush();
                // Receive the apk file from the client
                receiveSourceCodeAPK(input, apkFilePath);

                // Delete the old .dex file of this apk to avoid the crash due to dexopt:
                String oldDexFilePath =
                        context.getFilesDir().getAbsolutePath() + "/" + appName + ".dex";

                Log.e(this.getClass().getName(), oldDexFilePath);
                if (!new File(oldDexFilePath).delete()) {
                    Log.v(TAG, "Could not delete the old Dex file: " + oldDexFilePath);
                }
                apkMapSemaphore.get(appName).countDown();
            }

            // Wait for the file to be written on the disk
            apkMapSemaphore.get(appName).await();
            // Create the new (if needed) dex file and load the .dex file
            File dexFile = new File(apkFilePath);
            Log.d(TAG, "APK file size on disk: " + dexFile.length());
            addLibraries(dexFile);
            currentDexLoader = input.addDex(dexFile);
            Log.d(TAG, "DEX file added.");
        }catch (IOException | ClassNotFoundException | InterruptedException e){
            Log.e(TAG, e.toString());
        }
    }

    @Override
    public void execute(ERAMInputStream input, ObjectOutputStream output) {

        Log.v(TAG, "Got a new request for execution");
        synchronized (nrTasksCurrentlyBeingExecuted) {
            if (migrationInProgress.get()) {
                Log.w(TAG, "VM upgrade in progress, cannot accept new tasks");
                // Simply closing the connection will force the client to run tasks locally
                //closeConnection();

            }

            nrTasksCurrentlyBeingExecuted.incrementAndGet();
            Log.v(TAG, "The new task is accepted for execution, total nr of tasks: "
                    + nrTasksCurrentlyBeingExecuted.get());
        }

        // Start profiling on remote side
        // SystemProfiler devProfiler = new SystemProfiler("","");
        //devProfiler.startProfiler();

        // Log.d(TAG, "Execute request - " + request);
        Object result = retrieveAndExecute(input);


        //devProfiler.stopProfiler();

        try {
            // Send back over the socket connection

            // RapidUtils.sendAnimationMsg(config, RapidMessages.AC_RESULT_REMOTE);
            output.writeObject(result);
            // Clear ObjectOutputCache - Java caching unsuitable in this case
            output.flush();
            output.reset();

            Log.d(TAG, "Result successfully sent");

        } catch (IOException e) {
            Log.d(TAG, "Connection failed");
            e.printStackTrace();

        } finally {
            synchronized (nrTasksCurrentlyBeingExecuted) {
                nrTasksCurrentlyBeingExecuted.decrementAndGet();
                nrTasksCurrentlyBeingExecuted.notifyAll();
            }
        }
    }

    @Override
    public void finishConnection(ERAMInputStream input, ObjectOutputStream output, Socket client) {
        this.close(input, output, client);
        this.deleteLibs();

        Log.e(TAG, "Finishing the connection");
        //this.executor.shutdown();

    }

    /**
     * Private methods used by the implemented methods.
     */

    private synchronized boolean checkSourceCodeAPK(String fileName, String appName, int appLength) {

        File apkFile = new File(fileName);

        if (apkFile.exists() && apkFile.length() == appLength) {
            apkMapSemaphore.put(appName, new CountDownLatch(0));
            return true;
        }

        if (apkMap.get(appName) == null || apkMap.get(appName) != appLength) {
            apkMap.put(appName, appLength);
            apkMapSemaphore.put(appName, new CountDownLatch(1));
            return false;
        }

        return true;
    }

    private void receiveSourceCodeAPK(ERAMInputStream input, String apkFilePath) {
        // Receiving the apk file
        // Get the length of the file receiving
        try {
            // Write it to the filesystem
            File apkFile = new File(apkFilePath);
            FileOutputStream fout = new FileOutputStream(apkFile);
            BufferedOutputStream bout = new BufferedOutputStream(fout, Constants.BUFFER_SIZE_APK);

            // Get the apk file
            Log.d(TAG, "Starting reading apk file of size: " + appLength + " bytes");
            byte[] tempArray = new byte[Constants.BUFFER_SIZE_APK];
            int read;
            int totalRead = 0;
            int prevPerc = 0;
            int currPerc;
            while (totalRead < appLength) {
                read = input.read(tempArray);
                totalRead += read;
                bout.write(tempArray, 0, read);
                bout.flush();

                currPerc = (int) (((double) totalRead / appLength) * 100);
                //if (currPerc / 10 > prevPerc / 10) {
                Log.d(TAG, "Got: " + currPerc + " % of the apk.");
                Log.d(TAG, "TotalRead: " + totalRead + " of " + appLength + " bytes");
                prevPerc = currPerc;
                //}
            }

            bout.close();

        } catch (IOException e) {
            Log.d(TAG, e.toString());
        }
    }


    /**
     * Extract native libraries for the x86/x86_64 platform included in the .apk file (which is actually a
     * zip file).
     * <p>
     * The x86/x86_64 shared libraries are: lib/[x86\x86_64]/library.so inside the apk file. We extract them from the
     * apk and save in the /data/data/eu.rapid.project.as/files folder. Initially we used to save them
     * with the same name as the original (library.so) but this caused many problems related to
     * classloaders. When an app was offloaded for the first time and used the library, the library
     * was loaded in the jvm. If the client disconnected, the classloader that loaded the library was
     * not unloaded, which means that also the library was not unloaded from the jvm. On consequent
     * offloads of the same app, the classloader is different, meaning that the library could not be
     * loaded anymore due to the fact that was already loaded by another classloader. But it could not
     * even be used, due to the fact that the classloaders differ.<br>
     * <br>
     * To solve this problem we save the library within a new folder, increasing a sequence number
     * each time the same app is offloaded. So, the library.so file will be saved as
     * library-1/library.so, library-2/library.so, and so on.
     *
     * @param dexFile the apk file
     */
    private void addLibraries(File dexFile) {

        synchronized (syncLibrariesExtractObject) {


            Long startTime = System.nanoTime();

            ZipFile apkFile;

            try {
                apkFile = new ZipFile(dexFile);
                Enumeration<ZipEntry> entries = (Enumeration<ZipEntry>) apkFile.entries();
                ZipEntry entry;
                while (entries.hasMoreElements()) {
                    entry = entries.nextElement();
                    // Zip entry for a lib file is in the form of
                    // lib/platform/library.so
                    // But only load x86/x86_64 libraries on the server side
                    if (entry.getName().matches("lib/" + ServiceHandler.arch + "/(.*).so")) {
                        Log.d(TAG, "Matching APK entry - " + entry.getName());
                        // Unzip the lib file from apk
                        BufferedInputStream is = new BufferedInputStream(apkFile.getInputStream(entry));

                        // Folder where to put the libraries (usually this will resolve to:
                        // /data/data/eu.rapid.project.as/files)
                        File libFolder = new File(context.getFilesDir().getAbsolutePath());

                        // Get the library name without the .so extension
                        final String libName = entry.getName().replace("lib/" +
                                ServiceHandler.arch + "/", "").replace(".so", "");

                        if (!librariesIndex.containsKey(appName)) {
                            librariesIndex.put(appName, new HashMap<String, Integer>());
                        }

                        // The sequence number to append to the library name
                        int libSeqNr = 0;
                        if (librariesIndex.get(appName).containsKey(libName)) {
                            libSeqNr = librariesIndex.get(appName).get(libName);
                        } else {
                            for (File f : libFolder.listFiles(new FilenameFilter() {
                                @Override
                                public boolean accept(File file, String s) {
                                    return s.matches(libName + "-\\d+");
                                }
                            })) {

                                // Scan all the previously created folder libraries
                                int lastIndexDash = f.getName().lastIndexOf("-");
                                if (lastIndexDash != -1) {
                                    try {
                                        libSeqNr = Math.max(libSeqNr, Integer.parseInt(f.getName().substring(lastIndexDash + 1)));
                                    } catch (Exception e) {
                                        Log.w(TAG,
                                                "Library file does not contain any number in the name, maybe is not written by us!");
                                    }
                                }
                            }
                        }

                        libSeqNr++;
                        librariesIndex.get(appName).put(libName, libSeqNr);

                        File currLibFolder =
                                new File(libFolder.getAbsolutePath() + File.separator + libName + "-" + libSeqNr);
                        if (!currLibFolder.mkdir()) {
                            Log.e(TAG, "Could not create folder: " + currLibFolder);
                        }

                        File libFile =
                                new File(currLibFolder.getAbsolutePath() + File.separator + libName + ".so");
                        if (!libFile.createNewFile()) {
                            Log.e(TAG, "Could not create file: " + libFile);
                        } else {
                            Log.d(TAG, "Writing lib file to " + libFile.getAbsolutePath());
                            FileOutputStream fos = new FileOutputStream(libFile);
                            BufferedOutputStream dest = new BufferedOutputStream(fos, Constants.BUFFER_SIZE_SMALL);

                            byte data[] = new byte[Constants.BUFFER_SIZE_SMALL];
                            int count;
                            while ((count = is.read(data, 0, Constants.BUFFER_SIZE_SMALL)) != -1) {
                                dest.write(data, 0, count);
                            }
                            dest.flush();
                            dest.close();
                            is.close();

                            // Store the library on the map
                            libraries.add(libFile);
                        }
                    }
                }
            } catch (IOException e) {
                Log.d(TAG, "ERROR: File unzipping error " + e);
            }
            Log.d(TAG,
                    "Duration of unzipping libraries - " + ((System.nanoTime() - startTime) / 1000000) + "ms");
        }
    }


    /**
     *   Execution.
     */

    private Object retrieveAndExecute(ERAMInputStream input) {

        Long getObjectDuration = -1L;
        Long startTime = System.nanoTime();
        // Read the object in for execution
        Log.d(TAG, "Read Object");
        try {


            Task task = (Task) input.readObject();

            getObjectDuration = System.nanoTime() - startTime;

            Log.d(TAG, "Done Reading Object: " + task.toString()+ " in "
                    + (getObjectDuration / 1000000000.0) + " seconds");

            // Set up server-side DFE for the object


            // Run the method on this VM
            long startExecTime = System.nanoTime();
            // Run the method and retrieve the result
            Object result = executTask(task);
            long execDuration = System.nanoTime() - startExecTime;

            Log.d(TAG, task.toString() + ": retrieveAndExecute time - "
                    + ((System.nanoTime() - startTime) / 1000000) + "ms");


            return new ResultContainer(null, result, getObjectDuration, execDuration);


        } catch (Exception e) {
            // catch and return any exception since we do not know how to handle
            // them on the server side
            e.printStackTrace();
            return new ResultContainer(e, getObjectDuration);
        }
    }

    private Object executTask(Task task) throws InterruptedException, ExecutionException {

        return task.run();

    }

    // Finish Connection.

    private void close(ERAMInputStream input, ObjectOutputStream output, Socket client){
        Utils.close(input);
        Utils.close(output);
        Utils.close(client);
    }

    private void deleteLibs() {

        for (File lib : libraries) {
            File f = lib.getParentFile();
            if (f.isDirectory()) {
                for (File libFile : f.listFiles()) {
                    if (!libFile.delete()) {
                        Log.v(TAG, "Not possible to delete library file: " + libFile);
                    }
                }
            }
        }
    }
}
