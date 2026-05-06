package com.zhijing.ai;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class TFLiteEngine {

    private Interpreter interpreter;
    private final Context context;
    private boolean isInitialized = false;

    public TFLiteEngine(Context context) {
        this.context = context;
    }

    public void init(String modelFileName) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(2);
        interpreter = new Interpreter(loadModelFile(modelFileName), options);
        isInitialized = true;
    }

    private MappedByteBuffer loadModelFile(String modelFileName) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd("models/" + modelFileName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public float[] runInference(float[] inputData, int outputSize) {
        if (!isInitialized) throw new IllegalStateException("TFLiteEngine not initialized");
        float[][] input = {inputData};
        float[][] output = new float[1][outputSize];
        interpreter.run(input, output);
        return output[0];
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
            isInitialized = false;
        }
    }
}
