package com.zigurs.karlis.utils.charset;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CharsetCopTest {

    @Test
    public void canReadLatin1File() throws IOException {
        assertTrue(readFileAndValidateLength("iso-8859-1.txt", StandardCharsets.ISO_8859_1, 4));
    }

    @Test
    public void canReadUtf8File() throws IOException {
        assertTrue(readFileAndValidateLength("utf-8.txt", StandardCharsets.UTF_8, 4));
    }

    @Test
    public void canReadUtf8BOMFile() throws IOException {
        assertTrue(readFileAndValidateLength("utf-8-bom.txt", StandardCharsets.UTF_8, 5));
    }

    @Test
    public void canReadUtf16BEFile() throws IOException {
        assertTrue(readFileAndValidateLength("utf-16-be.txt", StandardCharsets.UTF_16BE, 4));
    }

    @Test
    public void canReadUtf16BEBOMFile() throws IOException {
        assertTrue(readFileAndValidateLength("utf-16-be-bom.txt", StandardCharsets.UTF_16BE, 5));
    }

    @Test
    public void canReadUtf16LEFile() throws IOException {
        assertTrue(readFileAndValidateLength("utf-16-le.txt", StandardCharsets.UTF_16LE, 4));
    }

    @Test
    public void canReadUtf16LEBOMFile() throws IOException {
        assertTrue(readFileAndValidateLength("utf-16-le-bom.txt", StandardCharsets.UTF_16LE, 5));
    }

    @Test(expected = IOException.class)
    public void cannotReadMissingFile() throws IOException {
        assertTrue(readFileAndValidateLength("foo.bar", StandardCharsets.US_ASCII, 4));
    }

    @Test
    public void cannotReadUtf8AFilesLatin1() throws IOException {
        assertTrue(readFileAndValidateLength("utf-8.txt", StandardCharsets.ISO_8859_1, 6));
    }

    @Test
    public void cannotReadLatin1AsUtf8() throws IOException {
        assertFalse(readFileAndValidateLength("iso-8859-1.txt", StandardCharsets.UTF_8, 1));
    }

    @Test
    public void shouldFindLatin1AsValidEncoding() throws IOException {
        assertEquals(
                StandardCharsets.ISO_8859_1,
                CharsetCop.findFirstValidCharset(
                        getTestFilePath("iso-8859-1.txt"),
                        StandardCharsets.US_ASCII, // should skip, contains > 128
                        StandardCharsets.UTF_8, // should skip, contains invalid range
                        StandardCharsets.ISO_8859_1).orElse(null)
        );
    }

    @Test
    public void shouldFindUTF8AsValidEncoding() throws IOException {
        assertEquals(
                StandardCharsets.UTF_8,
                CharsetCop.findFirstValidCharset(
                        getTestFilePath("utf-8.txt"),
                        StandardCharsets.US_ASCII, // should skip, contains > 128
                        StandardCharsets.UTF_8).orElse(null)
        );
    }

    @Test
    public void shouldFindUTF8BOMAsValidEncoding() throws IOException {
        assertEquals(
                StandardCharsets.UTF_8,
                CharsetCop.findFirstValidCharset(
                        getTestFilePath("utf-8-bom.txt"),
                        StandardCharsets.US_ASCII, // should skip, contains > 128
                        StandardCharsets.UTF_8).orElse(null)
        );
    }

    private boolean readFileAndValidateLength(String fileName, Charset charset, int expectedChars) throws IOException {
        return CharsetCop.isFileValidForCharset(
                getTestFilePath(fileName),
                charset,
                i -> assertEquals(expectedChars, i));
    }

    private Path getTestFilePath(String file) {
        return Paths.get(String.format("src/test/resources/%s", file));
    }
}