package com.rusel.RCTBluetoothSerial;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

// String testdata = 'testdata';
// InputStream stream = new ByteArrayInputStream(exampleString.getBytes(StandardCharsets.UTF_8))
interface IBluetoothInputStreamProcessor {

    void onConnected(InputStream inputStream) throws IOException;
}

public class BluetoothFileSaver implements IBluetoothInputStreamProcessor {

    private final String mFileName;
    private InputStream mInputStream = null;
    private OutputStream mOutputStream = null;
    private RCTBluetoothSerialModule mModule = null;
    private long mFileSize;
    private int mFileSizeLoaded;
    private final int mHeadersSize = 4;
    private double mLastFileLoadPercent = 0;

    BluetoothFileSaver(String name, RCTBluetoothSerialModule module) {
        this.mModule = module;
        this.mFileName = name;
    }

    @Override
    public void onConnected(InputStream inputStream) throws IOException {
        this.mInputStream = inputStream;
        byte[] buffer = new byte[1024];
        do {
            int bufferSize = mInputStream.read(buffer);
            int offset = 0;

            if (mOutputStream == null) {
                File file = new File(mFileName);
                if (!file.exists()) {
                    file.createNewFile();
                }
                mOutputStream = new FileOutputStream(file, false);
                offset = mHeadersSize;
                readHeaders(buffer);
            }

            mFileSizeLoaded += bufferSize - offset;
            mOutputStream.write(buffer, offset, bufferSize - offset);
            double currentFileLoadPercent = getFileLoadPercent();
            boolean isLoadedMorePercent = currentFileLoadPercent - mLastFileLoadPercent > 1;

            if (mModule != null && isLoadedMorePercent) {
                this.mLastFileLoadPercent = currentFileLoadPercent;
                mModule.onFileChunkLoaded(currentFileLoadPercent);
            }
        } while (mFileSize > mFileSizeLoaded);
        mOutputStream.flush();
        mOutputStream.close();
        if (mModule != null) {
            mModule.onFileLoaded();
        }
    }

    private double getFileLoadPercent() {
        return mFileSizeLoaded > 0 ? mFileSizeLoaded * 100 / mFileSize : 0;
    }

    private void readHeaders(byte[] headers) {
        parseFileSize(headers);
    }

    private void parseFileSize(byte[] headers) {
        byte[] sizeBuffer = Arrays.copyOf(headers, mHeadersSize);
        this.mFileSize = bytesToInt(sizeBuffer);
    }

    private long bytesToInt(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(mHeadersSize);
        buffer.put(bytes);
        buffer.flip();
        return buffer.getInt();
    }
}

