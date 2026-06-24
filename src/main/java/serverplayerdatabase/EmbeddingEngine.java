package serverplayerdatabase;

import arc.util.Log;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public final class EmbeddingEngine implements AutoCloseable{
    private static final String nativeBundleVersion = "llama-4.1.0";

    private final String modelId;
    private final Object model;
    private final Method embedMethod;
    private final Method closeMethod;
    private final Field contextField;
    private final int dimension;
    private final URLClassLoader runtimeLoader;
    private final Object lifecycleLock = new Object();
    private boolean closed;

    private EmbeddingEngine(String modelId, Object model, Method embedMethod, Method closeMethod, Field contextField, int dimension, URLClassLoader runtimeLoader){
        this.modelId = modelId;
        this.model = model;
        this.embedMethod = embedMethod;
        this.closeMethod = closeMethod;
        this.contextField = contextField;
        this.dimension = dimension;
        this.runtimeLoader = runtimeLoader;
    }

    public static EmbeddingEngine load(File modelFile, File runtimeJarFile, File nativeRootDir, int threads) throws Exception{
        if(runtimeJarFile == null || !runtimeJarFile.isFile() || runtimeJarFile.length() <= 0L){
            throw new IllegalStateException("Semantic search runtime jar is missing.");
        }

        URLClassLoader loader = new URLClassLoader(new URL[]{runtimeJarFile.toURI().toURL()}, EmbeddingEngine.class.getClassLoader());
        boolean success = false;
        try{
            prepareNativeLibrary(nativeRootDir, loader);

            ClassLoader previousContextLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(loader);
            try{
                Class<?> modelParametersClass = Class.forName("de.kherud.llama.ModelParameters", true, loader);
                Class<?> llamaModelClass = Class.forName("de.kherud.llama.LlamaModel", true, loader);

                Object modelParameters = modelParametersClass.getConstructor().newInstance();
                invoke(modelParametersClass, modelParameters, "setModel", new Class[]{String.class}, modelFile.getAbsolutePath());
                invoke(modelParametersClass, modelParameters, "enableEmbedding", new Class[0]);
                invoke(modelParametersClass, modelParameters, "setThreads", new Class[]{int.class}, Math.max(1, threads));
                invoke(modelParametersClass, modelParameters, "setThreadsBatch", new Class[]{int.class}, Math.max(1, threads));
                invoke(modelParametersClass, modelParameters, "setCtxSize", new Class[]{int.class}, 512);
                invoke(modelParametersClass, modelParameters, "setBatchSize", new Class[]{int.class}, 512);
                invoke(modelParametersClass, modelParameters, "setUbatchSize", new Class[]{int.class}, 512);
                invoke(modelParametersClass, modelParameters, "skipWarmup", new Class[0]);

                Object model = llamaModelClass.getConstructor(modelParametersClass).newInstance(modelParameters);
                Method embedMethod = llamaModelClass.getMethod("embed", String.class);
                Method closeMethod = llamaModelClass.getMethod("close");
                Field contextField = llamaModelClass.getDeclaredField("ctx");
                contextField.setAccessible(true);

                long contextHandle = contextField.getLong(model);
                if(contextHandle == 0L){
                    throw new IllegalStateException("Embedding model native context was not initialized.");
                }

                float[] probe = (float[])embedMethod.invoke(model, "测试");
                if(probe == null || probe.length == 0){
                    closeQuietly(closeMethod, model, contextField);
                    throw new IllegalStateException("Embedding model returned an empty vector.");
                }

                EmbeddingEngine engine = new EmbeddingEngine(modelFile.getName(), model, embedMethod, closeMethod, contextField, probe.length, loader);
                success = true;
                return engine;
            }finally{
                Thread.currentThread().setContextClassLoader(previousContextLoader);
            }
        }finally{
            if(!success){
                closeLoaderQuietly(loader);
            }
        }
    }

    public static EmbeddingEngine load(File modelFile, File nativeRootDir, int threads) throws Exception{
        return load(modelFile, null, nativeRootDir, threads);
    }

    public static EmbeddingEngine load(File modelFile, int threads) throws Exception{
        return load(modelFile, null, null, threads);
    }

    public int dimension(){
        return dimension;
    }

    public String modelId(){
        return modelId;
    }

    public float[] embed(String text){
        synchronized(lifecycleLock){
            if(closed){
                throw new IllegalStateException("Embedding model is already closed.");
            }
            try{
                float[] vector = (float[])embedMethod.invoke(model, text == null ? "" : text);
                if(vector == null || vector.length == 0){
                    throw new IllegalStateException("Embedding model returned an empty vector.");
                }
                normalize(vector);
                return vector;
            }catch(RuntimeException e){
                throw e;
            }catch(Exception e){
                throw new RuntimeException("Failed to embed query.", e);
            }
        }
    }

    @Override
    public void close(){
        synchronized(lifecycleLock){
            if(closed) return;
            closed = true;
            closeQuietly(closeMethod, model, contextField);
            closeLoaderQuietly(runtimeLoader);
        }
    }

    private static Object invoke(Class<?> type, Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception{
        Method method = type.getMethod(methodName, parameterTypes);
        return method.invoke(target, args);
    }

    private static void prepareNativeLibrary(File nativeRootDir, ClassLoader runtimeLoader) throws Exception{
        NativeLayout nativeLayout = resolveNativeLayout();
        if(nativeLayout == null) return;

        File root = nativeRootDir == null ? defaultNativeRoot() : nativeRootDir;
        File nativeDir = new File(root, nativeLayout.subdirectory);
        if(!nativeDir.exists() && !nativeDir.mkdirs() && !nativeDir.exists()){
            throw new IllegalStateException("Failed to create native library directory: " + nativeDir.getAbsolutePath());
        }

        extractBundledResource(runtimeLoader, nativeLayout.resourcePath(), new File(nativeDir, nativeLayout.fileName));
        System.setProperty("de.kherud.llama.lib.path", nativeDir.getAbsolutePath());
        System.setProperty("de.kherud.llama.tmpdir", nativeDir.getAbsolutePath());
    }

    private static File defaultNativeRoot(){
        return new File(System.getProperty("java.io.tmpdir"), "spdb-llama/" + nativeBundleVersion);
    }

    private static void extractBundledResource(ClassLoader runtimeLoader, String resourcePath, File target) throws Exception{
        File parent = target.getParentFile();
        if(parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()){
            throw new IllegalStateException("Failed to create native library parent directory: " + parent.getAbsolutePath());
        }
        if(target.isFile() && target.length() > 0L) return;

        try(InputStream input = openBundledResource(runtimeLoader, resourcePath)){
            if(input == null){
                throw new IllegalStateException("Semantic search runtime native library resource missing: " + resourcePath);
            }
            Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static InputStream openBundledResource(ClassLoader runtimeLoader, String resourcePath) throws Exception{
        if(runtimeLoader != null){
            InputStream stream = runtimeLoader.getResourceAsStream(resourcePath);
            if(stream != null) return stream;
        }

        ClassLoader loader = EmbeddingEngine.class.getClassLoader();
        InputStream stream = loader == null ? null : loader.getResourceAsStream(resourcePath);
        if(stream != null) return stream;
        return EmbeddingEngine.class.getResourceAsStream("/" + resourcePath);
    }

    private static NativeLayout resolveNativeLayout(){
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = normalizeArch(System.getProperty("os.arch", ""));
        if(arch == null) return null;

        if(os.contains("win")){
            return new NativeLayout("Windows/" + arch, "jllama.dll");
        }
        if(os.contains("mac") || os.contains("darwin")){
            return new NativeLayout("Mac/" + arch, "libjllama.dylib");
        }
        if(os.contains("linux")){
            return new NativeLayout("Linux/" + arch, "libjllama.so");
        }
        return null;
    }

    private static String normalizeArch(String value){
        String arch = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if("amd64".equals(arch) || "x86_64".equals(arch)) return "x86_64";
        if("x86".equals(arch) || "i386".equals(arch)) return "x86";
        if("aarch64".equals(arch) || "arm64".equals(arch)) return "aarch64";
        return null;
    }

    private static void closeQuietly(Method closeMethod, Object model, Field contextField){
        try{
            if(contextField != null && contextField.getLong(model) == 0L) return;
            closeMethod.invoke(model);
            if(contextField != null) contextField.setLong(model, 0L);
        }catch(Exception e){
            Log.err("SPDB: failed closing embedding model.", e);
        }
    }

    private static void closeLoaderQuietly(URLClassLoader loader){
        if(loader == null) return;
        try{
            loader.close();
        }catch(Exception e){
            Log.err("SPDB: failed closing semantic search runtime loader.", e);
        }
    }

    static void normalize(float[] vector){
        double sum = 0d;
        for(float v : vector){
            sum += (double)v * (double)v;
        }
        if(sum <= 0d) return;

        float scale = (float)(1d / Math.sqrt(sum));
        for(int i = 0; i < vector.length; i++){
            vector[i] *= scale;
        }
    }

    private static final class NativeLayout{
        final String subdirectory;
        final String fileName;

        NativeLayout(String subdirectory, String fileName){
            this.subdirectory = subdirectory;
            this.fileName = fileName;
        }

        String resourcePath(){
            return "de/kherud/llama/" + subdirectory + "/" + fileName;
        }
    }
}
