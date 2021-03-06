package com.moebuff.discord.utils.io;

import com.moebuff.discord.utils.UnhandledException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.Validate;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;

/**
 * File Checksum Integrity Verifier
 *
 * @author muto
 */
public class FCIV {
    /**
     * 计算MD5摘要，需要注意的是 {@link DigestUtils#md5Hex(InputStream)} 在Android中无法使用。
     * <p>
     * 原因是 {@link Hex#encodeHexString(byte[])} 方法在Desktop中可以正常使用，但在Android中会提示找不到，
     * 由于Android内部有一个包名一样的工程，而且类名也相同，关键是没有该方法！所以导致包名冲突，JVM只会引入第一个包，
     * 第二个包在classloader加载类时判断重复而被忽略。为保证在Android中可正以常使用该方法，因此这里将其重新实现。
     *
     * @param f 需要计算摘要的文件
     */
    public static String md5Hex(File f) {
        return md5Hex(FileKit.openInputStream(f));
    }

    /**
     * Calculates the MD5 digest and returns the value as a 32 character hex string.
     *
     * @param data Data to digest
     * @return MD5 digest as a hex string
     * @throws UnhandledException On error reading from the stream
     */
    public static String md5Hex(final InputStream data) {
        try {
            return encodeHexString(DigestUtils.md5(data));
        } catch (IOException e) {
            throw new UnhandledException(e);
        }
    }

    /**
     * Converts an array of bytes into a String representing the hexadecimal values of each byte in order.
     * The returned String will be double the length of the passed array, as it takes two characters to
     * represent any given byte.
     *
     * @param data a byte[] to convert to Hex characters
     * @return A String containing hexadecimal characters
     */
    public static String encodeHexString(final byte[] data) {
        return new String(Hex.encodeHex(data));
    }

    /**
     * Returns a CRC of the remaining bytes in the stream.
     */
    public static String crc(InputStream input) {
        return Long.toString(crc16(input), 16);
    }

    public static String crc(File f) {
        return crc(FileKit.openInputStream(f));
    }

    /**
     * 计算给定输入流的 CRC 校验码。
     *
     * @param input 输入流，不能为null
     * @return 16进制的 {@link Long 长整形} CRC-32
     * @throws UnhandledException 如果不是因为流位于文件末尾而无法读取第一个字节；
     *                            如果输入流已关闭；如果发生其他 I/O 错误。
     */
    public static long crc16(InputStream input) {
        Validate.isTrue(input != null, "输入流不能为null");
        CRC32 crc = new CRC32();
        byte[] buffer = new byte[4096];
        try {
            while (true) {
                int length = input.read(buffer);
                if (length == -1) break;
                crc.update(buffer, 0, length);
            }
        } catch (IOException e) {
            throw new UnhandledException(e);
        }
        return crc.getValue();
    }

    public static long crc16(File f) {
        return crc16(FileKit.openInputStream(f));
    }
}
