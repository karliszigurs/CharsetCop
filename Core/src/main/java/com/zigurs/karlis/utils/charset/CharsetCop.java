/*
 *                                     //
 * Copyright 2017 Karlis Zigurs (http://zigurs.com)
 *                                   //
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
package com.zigurs.karlis.utils.charset;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntConsumer;

public class CharsetCop {

    private static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

    public static boolean isFileValidForCharset(Path filePath, Charset charset) throws IOException {
        return isFileValidForCharset(filePath, charset, null);
    }

    public static boolean isFileValidForCharset(Path filePath,
                                                Charset charset,
                                                IntConsumer readCharsCallback) throws IOException {
        return isValidFileForCharsetImpl(filePath, charset, readCharsCallback);
    }

    public static Optional<Charset> findFirstValidCharset(Path filePath,
                                                          Charset... candidateCharsets) throws IOException {
        for (Charset charset : candidateCharsets)
            if (isFileValidForCharset(filePath, charset))
                return Optional.of(charset);

        return Optional.empty();
    }

    private static boolean isValidFileForCharsetImpl(Path filePath,
                                                     Charset charset,
                                                     IntConsumer readCharsCallback) throws IOException {
        Objects.requireNonNull(filePath);
        Objects.requireNonNull(charset);

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath))
            throw new IOException(String.format("File '%s' is not a valid (present, regular) file", filePath));

        try (BufferedReader reader = Files.newBufferedReader(filePath, charset)) {
            char[] readBuf = new char[DEFAULT_BUFFER_SIZE];

            int readChars;
            int totalChars = 0;

            while ((readChars = reader.read(readBuf)) > -1)
                totalChars += readChars;

            if (readCharsCallback != null)
                readCharsCallback.accept(totalChars);

            return true;
        } catch (MalformedInputException mfi) {
            return false;
        }
    }
}