package com.wurmonline.womconverter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.HashMap;

public class MatReporter implements AutoCloseable {
    private PrintStream output;
    private HashMap<String, String> mats = new HashMap<>();

    public MatReporter(File outFile) {
        try {
            output = new PrintStream(outFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void addMat(String mat, String file) {
        mats.put(mat, file);
    }

    public void reportFile(String file) {
        output.println(file);
        mats.forEach((m, f) -> output.println(String.format("- %s -> %s", m, f)));
        mats.clear();
    }

    @Override
    public void close() {
        output.close();
    }
}
