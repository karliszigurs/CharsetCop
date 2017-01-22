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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.ValueConverter;

public class CharsetCopCLI {

    // Core configuration
    private final List<Path> checkPaths;
    private final String filesFilter;
    private final Charset checkForCharset;

    // "runtime" configuration
    private boolean isDebugMode = false;
    private boolean isSkipSymLinks = false;

    // Track working stats
    private final AtomicLong charsRead = new AtomicLong();
    private final AtomicInteger processed = new AtomicInteger();

    // Track various exception
    private final List<Path> encodingErrors = Collections.synchronizedList(new ArrayList<>());
    private final List<Path> genericErrors = Collections.synchronizedList(new ArrayList<>());
    private final List<Path> unprocessed = Collections.synchronizedList(new ArrayList<>());
    private final List<Path> skipped = Collections.synchronizedList(new ArrayList<>());

    private static final Charset[] DEFAULT_CANDIDATE_CHARSETS = new Charset[]{
            StandardCharsets.US_ASCII,
            StandardCharsets.UTF_8,
            StandardCharsets.ISO_8859_1
    };

    private CharsetCopCLI(List<Path> rootPathsToCheck, String filter, Charset charset) {
        checkPaths = rootPathsToCheck;
        filesFilter = filter;
        checkForCharset = charset;
    }

    private CharsetCopCLI withDebugMode(boolean debugMode) {
        isDebugMode = debugMode;
        return this;
    }

    /*
     * Summary function
     */

    private void printResults(long startTimestamp) {
        //
        // Summary
        //
        System.out.println(
                String.format("Total of %,d '%s' chars read in %s",
                              charsRead.get(),
                              checkForCharset.displayName(),
                              checkPaths.size() == 1
                                      ? String.format("'%s'", checkPaths.get(0).toAbsolutePath())
                                      : String.format("%d paths", checkPaths.size())
                )
        );

        // Successful reads
        System.out.println(String.format("%d %s read as '%s'",
                                         processed.get(),
                                         processed.get() == 1 ? "file" : "files",
                                         checkForCharset.displayName()));

        // Encoding errors
        System.out.println(String.format("%d %s failed encoding check",
                                         encodingErrors.size(),
                                         encodingErrors.size() == 1 ? "file" : "files"));
        printFirstElements(encodingErrors);

        // Ignored paths
        System.out.println(String.format("%d %s ignored",
                                         unprocessed.size(),
                                         unprocessed.size() == 1 ? "path" : "paths"));
        printFirstElements(unprocessed);

        if (isSkipSymLinks) {
            System.out.println(String.format("%d symlinks skipped", skipped.size()));
            printFirstElements(skipped);
        }

        System.out.println(String.format("%d errors encountered", genericErrors.size()));
        printFirstElements(genericErrors);

        System.out.println(String.format("done in %,dms", ((System.nanoTime() - startTimestamp) / 1000_000)));
    }

    private <T> void printFirstElements(List<T> pathList) {
        if (pathList.isEmpty())
            return;

        // Trim to first 10
        for (int i = 0; i < Math.min(pathList.size(), 10); i++)
            System.out.println("\t" + pathList.get(i));

        if (pathList.size() > 10)
            System.out.println(String.format("\t... and %d more", pathList.size() - 10));
    }

    /*
     * Implementation
     */

    private void process() throws IOException {
        long startTimestamp = System.nanoTime();

        for (Path path : checkPaths) {
            if (Files.isDirectory(path)) {

                PathMatcher matcher = path.getFileSystem().getPathMatcher("glob:" + filesFilter);
                DirectoryStream.Filter<Path> filter = entry ->
                        Files.isDirectory(entry) || matcher.matches(entry.getFileName());

                processDirectory(path, filter);
            } else if (Files.isRegularFile(path)) {
                processFile(path);
            } else {
                throw new IOException(String.format("Don't know how to process path '%s', "
                                                            + "not a directory or regular file", path));
            }
        }

        printResults(startTimestamp);
    }

    private void processDirectory(Path directoryPath, DirectoryStream.Filter<Path> filter) {
        if (isDebugMode)
            System.out.println(
                    String.format("Started processing directory '%s' with glob '%s'", directoryPath, filesFilter));

        List<Path> directories = new ArrayList<>();
        List<Path> files = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directoryPath, filter)) {
            stream.forEach(path -> {
                if (Files.isSymbolicLink(path) && isSkipSymLinks) {
                    skipped.add(path);
                } else if (Files.isDirectory(path)) {
                    directories.add(path);
                } else if (Files.isRegularFile(path)) {
                    files.add(path);
                } else {
                    unprocessed.add(path);
                }
            });
        } catch (IOException e) {
            registerError(directoryPath, e);
        }

        files.parallelStream().forEach(this::processFile);
        directories.parallelStream().forEach(dir -> processDirectory(dir, filter));
    }

    private void processFile(Path filePath) {
        if (isDebugMode)
            System.out.println(
                    String.format("Starting processing file '%s' with charset '%s'", filePath, checkForCharset));

        try {
            if (CharsetCop.isFileValidForCharset(filePath, checkForCharset, charsRead::addAndGet))
                handleValidFile(filePath);
            else
                handleInvalidFile(filePath);
        } catch (IOException e) {
            registerError(filePath, e);
        }
    }

    private void handleValidFile(Path filePath) {
        processed.incrementAndGet();

        if (isDebugMode) {
            System.out.println(String.format("Processed '%s' as '%s'", filePath, checkForCharset));
        }
    }

    private void handleInvalidFile(Path filePath) throws IOException {
        encodingErrors.add(filePath);
    }

    private void registerError(Path filePath, IOException e) {
        genericErrors.add(filePath);

        if (isDebugMode) // Print here and now
            new IOException(String.format("Error processing file '%s", filePath), e).printStackTrace();
    }

    public static void main(String[] args) throws IOException {
        OptionParser parser = new OptionParser();

        OptionSpec<Path> pathsParam = parser.nonOptions("one or more paths (file or directory) to check")
                .withValuesConvertedBy(new PathValueConverter());

        OptionSpec<Charset> encodingParam = parser.accepts("e", "encoding to check for")
                .withRequiredArg()
                .withValuesConvertedBy(new CharsetValueConverter())
                .defaultsTo(StandardCharsets.UTF_8);

        OptionSpec<String> extensionsParam = parser
                .accepts("t", "file pattern to apply to directories (e.g. \"-t **/*.{java,html}\")")
                .withRequiredArg();

        parser.accepts("d", "enable debug output");

        parser.acceptsAll(Arrays.asList("h", "?"), "show help").forHelp();

        OptionSet options = parser.parse(args);

        //
        // Paths are required
        //

        List<Path> specifiedPaths = null;
        if (options.has(pathsParam) && !pathsParam.values(options).isEmpty()) {
            specifiedPaths = pathsParam.values(options);
        } else {
            parser.printHelpOn(System.out);
            System.exit(0);
        }

        //
        // Start parsing options
        //

        // Encoding
        Charset specifiedEncoding;
        if (options.has(encodingParam)) {
            specifiedEncoding = encodingParam.value(options);
        } else {
            specifiedEncoding = StandardCharsets.UTF_8;
        }

        // Extensions
        String fileFilter;
        if (options.has(extensionsParam)) {
            fileFilter = extensionsParam.value(options);
        } else {
            fileFilter = "*";
        }

        // Debug mode
        boolean specifiedDebug = options.has("d");

        try {
            new CharsetCopCLI(specifiedPaths, fileFilter, specifiedEncoding)
                    .withDebugMode(specifiedDebug)
                    .process();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }

    }

    private static class PathValueConverter implements ValueConverter<Path> {

        @Override
        public Path convert(String s) {
            return Paths.get(s);
        }

        @Override
        public Class<? extends Path> valueType() {
            return Path.class;
        }

        @Override
        public String valuePattern() {
            return null;
        }
    }

    private static class CharsetValueConverter implements ValueConverter<Charset> {

        @Override
        public Charset convert(String value) {
            return Charset.forName(value);
        }

        @Override
        public Class<? extends Charset> valueType() {
            return Charset.class;
        }

        @Override
        public String valuePattern() {
            return null;
        }
    }
}
