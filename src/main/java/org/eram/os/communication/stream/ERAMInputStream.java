package org.eram.os.communication.stream;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import dalvik.system.DexClassLoader;


/**
 * ERAM Object input stream file to load dynamically classess.  This class is used to receive object
 * from Android dex files.
 **/
public class ERAMInputStream extends ObjectInputStream {
    private static final String TAG = "ERAM-InputStream";

    private ClassLoader classLoader;
    private DexClassLoader dexClassLoader;

    public ERAMInputStream(InputStream in) throws IOException {
        super(in);
    }

    public void setClassLoaders(ClassLoader classLoader, DexClassLoader dexClassLoader) {
        this.classLoader = classLoader;
        this.dexClassLoader = dexClassLoader;
    }


    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc)
            throws IOException, ClassNotFoundException {
        try {
            try {
                return classLoader.loadClass(desc.getName());
            } catch (ClassNotFoundException e) {
                return dexClassLoader.loadClass(desc.getName());
            }
        } catch (ClassNotFoundException e) {
            return super.resolveClass(desc);
        } catch (NullPointerException e) {
            return super.resolveClass(desc);
        }
    }


    public DexClassLoader addDex(final File apkFile) {

        if (dexClassLoader == null) {
            dexClassLoader = new DexClassLoader(apkFile.getAbsolutePath(),
                    apkFile.getParentFile().getAbsolutePath(), null, classLoader);
        } else {
            dexClassLoader = new DexClassLoader(apkFile.getAbsolutePath(),
                    apkFile.getParentFile().getAbsolutePath(), null, dexClassLoader);
        }

        return dexClassLoader;
    }
}
