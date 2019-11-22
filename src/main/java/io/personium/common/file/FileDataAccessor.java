/**
 * Personium
 * Copyright 2014-2019 Personium Project
 * - FUJITSU LIMITED
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
package io.personium.common.file;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SyncFailedException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Accessor class that performs WebDAV File input / output against File System.
 */
public class FileDataAccessor {

    private static Logger logger = LoggerFactory.getLogger(FileDataAccessor.class);

    /**
     * Davファイルの読み書き時、ハードリンク作成/ファイル名改変時の最大リトライ回数.
     * ※本クラスは、Coreに含まれないため、personnium-unit-config.propertiesを参照できないものと考え、
     * システムプロパティで処理を行うものとする
     */
    private static int maxRetryCount = Integer.parseInt(System.getProperty(
            "io.personium.core.binaryData.dav.retry.count", "100"));

    /**
     * Davファイルの読み書き時、ハードリンク作成/ファイル名改変時のリトライ間隔(msec).
     * ※本クラスは、Coreに含まれないため、personium-unit-config.propertiesを参照できないものと考え、
     * システムプロパティで処理を行うものとする
     */
    private static long retryInterval = Long.parseLong(System.getProperty(
            "io.personium.core.binaryData.dav.retry.interval", "50"));

    private static final int FILE_BUFFER_SIZE = 1024;
    private String baseDir;
    private String unitUserName;
    private boolean isPhysicalDeleteMode = false;
    private boolean fsyncEnabled = false;

    /**
     * Constructor.
     * @param path base directory
     * @param unitUserName unit user name
     * @param fsyncEnabled flag for enabling / disabling fsync on writing file. (true: enabled, false: disabled)
     */
    public FileDataAccessor(String path, String unitUserName, boolean fsyncEnabled) {
        this.baseDir = path;
        if (!this.baseDir.endsWith("/")) {
            this.baseDir += "/";
        }
        this.unitUserName = unitUserName;
        this.fsyncEnabled = fsyncEnabled;
    }

    /**
     * Constructor.
     * @param path base directory
     * @param unitUserName unit user name
     * @param isPhysicalDeleteMode ファイル削除時に物理削除するか（true: 物理削除, false: 論理削除）
     * @param fsyncEnabled ファイル書き込み時にfsyncを有効にするか否か（true: 有効, false: 無効）
     */
    public FileDataAccessor(String path, String unitUserName, boolean isPhysicalDeleteMode, boolean fsyncEnabled) {
        this.baseDir = path;
        if (!this.baseDir.endsWith("/")) {
            this.baseDir += "/";
        }
        this.unitUserName = unitUserName;
        this.isPhysicalDeleteMode = isPhysicalDeleteMode;
        this.fsyncEnabled = fsyncEnabled;
    }

    /**
     * Copies a file onto an outputstream.
     * @param filename File name
     * @param outputStream target OutputStream
     * @throws FileDataAccessException ファイル入出力で異常が発生した場合にスローする
     * @return byte size copied
     */
    public long copy(String filename, OutputStream outputStream) throws FileDataAccessException {
        String fullPathName = getFilePath(filename);
        if (!exists(fullPathName)) {
            throw new FileDataNotFoundException(fullPathName);
        }
        return writeToStream(fullPathName, outputStream);
    }

    /**
     * Deletes a file. 設定に従い、論理削除(デフォルト)／物理削除を行う 対象ファイルが存在しない場合は何もしない
     * @param filename File Name
     * @throws FileDataAccessException ファイル入出力で異常が発生した場合にスローする
     */
    public void delete(String filename) throws FileDataAccessException {
        String fullPathName = getFilePath(filename);
        deleteWithFullPath(fullPathName);
    }

    /**
     * Deletes a file (using full path). 設定に従い、論理削除(デフォルト)／物理削除を行う 対象ファイルが存在しない場合は何もしない
     * @param filepath ファイルパス
     * @throws FileDataAccessException ファイル入出力で異常が発生した場合にスローする
     */
    public void deleteWithFullPath(String filepath) throws FileDataAccessException {
        if (exists(filepath)) {
            if (this.isPhysicalDeleteMode) {
                deletePhysicalFileWithFullPath(filepath);
            } else {
                deleteFile(filepath);
            }
        }
    }

    /**
     * ファイルを物理削除する.
     * @param filepath ファイル名(フルパス)
     * @throws FileDataAccessException ファイル入出力で異常が発生した場合にスローする
     */
    private void deletePhysicalFileWithFullPath(String filepath) throws FileDataAccessException {
        Path file = new File(filepath).toPath();
        for (int i = 0; i < maxRetryCount; i++) {
            try {
                synchronized (filepath) {
                    Files.delete(file);
                }
                // 処理成功すれば、その場で復帰する。
                return;
            } catch (IOException e) {
                logger.debug("Failed to delete file: " + filepath + ".  Will retry again.");
                try {
                    Thread.sleep(retryInterval);
                } catch (InterruptedException e2) {
                    logger.debug("Thread interrupted.");
                }
            }
        }
        throw new FileDataAccessException("Failed to delete file: " + filepath);
    }

    /**
     * gets full path for a given file name.
     * @param filename file name
     * @return full path for the file
     */
    public String getFilePath(String filename) {
        String directory = getSubDirectoryName(filename);
        String fullPathName = this.baseDir + directory + filename;
        return fullPathName;
    }

    private static final int SUBDIR_NAME_LEN = 2;

    private boolean exists(String fullPathFilename) {
        File file = new File(fullPathFilename);
        return file.exists();
    }

    private String getSubDirectoryName(String filename) {
        StringBuilder sb = new StringBuilder("");
        if (this.unitUserName != null) {
            sb.append(this.unitUserName);
            sb.append("/");
        }
        sb.append(splitDirectoryName(filename, 0));
        sb.append("/");
        sb.append(splitDirectoryName(filename, SUBDIR_NAME_LEN));
        sb.append("/");
        return sb.toString();
    }

    private String splitDirectoryName(String filename, int index) {
        return filename.substring(index, index + SUBDIR_NAME_LEN);
    }

    private long writeToStream(String fullPathName, OutputStream outputStream) throws FileDataAccessException {
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(fullPathName);
            return copyStream(inputStream, outputStream);
        } catch (FileNotFoundException e) {
            throw new FileDataNotFoundException(fullPathName);
        } catch (FileDataAccessException ex) {
            throw new FileDataAccessException("WriteToStreamFailed:" + fullPathName, ex);
        } finally {
            closeInputStream(inputStream);
        }
    }

    private long copyStream(InputStream inputStream, OutputStream outputStream) throws FileDataAccessException {
        BufferedInputStream bufferedInput = null;
        BufferedOutputStream bufferedOutput = null;
        try {
            bufferedInput = new BufferedInputStream(inputStream);
            bufferedOutput = new BufferedOutputStream(outputStream);
            byte[] buf = new byte[FILE_BUFFER_SIZE];
            long totalBytes = 0L;
            int len;
            while ((len = bufferedInput.read(buf)) != -1) {
                bufferedOutput.write(buf, 0, len);
                totalBytes += len;
            }
            return totalBytes;
        } catch (IOException ex) {
            throw new FileDataAccessException("CopyStreamFailed.", ex);
        } finally {
            closeOutputStream(bufferedOutput);
            closeInputStream(bufferedInput);
        }
    }

    private void closeInputStream(InputStream inputStream) {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException ex) {
            logger.debug("StreamCloseFailed:" + ex.getMessage());
        }
    }

    private void closeOutputStream(OutputStream outputStream) {
        try {
            if (outputStream != null) {
                outputStream.flush();
                if (this.fsyncEnabled) {
                    fsyncIfFileOutputStream(outputStream);
                }
                outputStream.close();
            }
        } catch (IOException ex) {
            logger.debug("StreamCloseFailed:" + ex.getMessage());
        }
    }

    /**
     * syncing a file descriptor.
     * @param fd file descriptor
     * @exception SyncFailedException when failed to sync
     */
    public void sync(FileDescriptor fd) throws SyncFailedException {
        fd.sync();
    }

    private void fsyncIfFileOutputStream(OutputStream outputStream) throws IOException {
        if (outputStream instanceof FileOutputStream) {
            FileDescriptor desc = ((FileOutputStream) outputStream).getFD();
            if (null != desc && desc.valid()) {
                sync(desc);
            }
        } else if (outputStream instanceof FilterOutputStream) {
            // FilterOutputStream の場合には、"out"field から FileOutputStream を取り出してfsyncする
            fsyncIfFileOutputStream(getInternalOutputStream((FilterOutputStream) outputStream));
        }
    }

    private OutputStream getInternalOutputStream(FilterOutputStream sourceOutputStream) {
        if (null != sourceOutputStream) {
            try {
                Field internalOut;
                internalOut = FilterOutputStream.class.getDeclaredField("out");
                internalOut.setAccessible(true);
                Object out = internalOut.get(sourceOutputStream);
                if (out instanceof OutputStream) {
                    return (OutputStream) out;
                }
            } catch (NoSuchFieldException e) {
                return null;
            } catch (SecurityException e) {
                return null;
            } catch (IllegalArgumentException e) {
                return null;
            } catch (IllegalAccessException e) {
                return null;
            }
        }
        return null;
    }

    private void deleteFile(String srcFullPathName) throws FileDataAccessException {
        String dstFullPathName = srcFullPathName + ".deleted";
        File srcFile = new File(srcFullPathName);
        File dstFile = new File(dstFullPathName);

        for (int i = 0; i < maxRetryCount; i++) {
            try {
                synchronized (srcFullPathName) {
                    Files.move(srcFile.toPath(), dstFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                // 処理成功すれば、その場で復帰する。
                return;
            } catch (IOException e) {
                logger.debug("Failed to delete file: " + srcFullPathName);
                try {
                    Thread.sleep(retryInterval);
                } catch (InterruptedException e2) {
                    logger.debug("Thread interrupted.");
                }
            }
        }
        throw new FileDataAccessException("Failed to delete file: " + srcFullPathName);
    }
}
