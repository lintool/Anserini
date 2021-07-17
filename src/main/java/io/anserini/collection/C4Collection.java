/*
 * Anserini: A Lucene toolkit for reproducible information retrieval research
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.anserini.collection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;


public class C4Collection extends DocumentCollection<C4Collection.Document> {
  public C4Collection(Path path) {
    this.path = path;
  }

  @Override
  public FileSegment<C4Collection.Document> createFileSegment(Path p) throws IOException {
    return new Segment(p);
  }

  // removes control characters
  static class CtrlFilterStream extends FilterInputStream {
    public CtrlFilterStream(InputStream in) {
      super(in);
    }

    @Override
    public int read() throws IOException {
      int character = super.read();
      if (character == 127 || character < 32)
        return 0;
      return character;
    }
  }

  private int getFileNumber(String fileName) {
    try {
      int fileNumStart = fileName.indexOf("c4-train.") + 9;
      return Integer.parseInt(fileName.substring(fileNumStart, fileNumStart + 5));
    } catch (final NumberFormatException e) {
      return fileName.hashCode();
    }
  }

  @Override
  public List<Path> getSegmentPaths(int shardCount, int currShard) {
    List<Path> segments = super.getSegmentPaths();
    return segments.stream().filter(x -> getFileNumber(x.toString()) % shardCount == currShard).collect(Collectors.toList());
  }

  public static class Segment extends FileSegment<C4Collection.Document>{
    protected MappingIterator<JsonNode> iterator; // iterator for JSON line objects
    protected JsonNode node = null;
    protected String filePath;
    protected String fileName;
    protected int count = 0;

    public Segment(Path path) throws IOException {
      super(path);
      filePath = path.toString();
      int fileNumStart = filePath.indexOf("c4-train.") + 9;  
      fileName = filePath.substring(fileNumStart + 1, fileNumStart + 5);
      if (filePath.endsWith(".gz")) { //.gz
        InputStream stream = new GZIPInputStream(
                Files.newInputStream(path, StandardOpenOption.READ), BUFFER_SIZE);
        CtrlFilterStream filteredStream = new CtrlFilterStream(stream);
        bufferedReader = new BufferedReader(new InputStreamReader(filteredStream, StandardCharsets.UTF_8));
      } else { // plain text file
        InputStream stream = new FileInputStream(filePath);
        CtrlFilterStream filteredStream = new CtrlFilterStream(stream);
        bufferedReader = new BufferedReader(new InputStreamReader(filteredStream, StandardCharsets.UTF_8));
      }
      // reading as a json file
      ObjectMapper mapper = new ObjectMapper();
      iterator = mapper.readerFor(JsonNode.class).readValues(bufferedReader);
      node = iterator.next();
    }

    @Override
    public void readNext() throws NoSuchElementException {
      if (node == null) {
        throw new NoSuchElementException("JsonNode is empty");
      } else {
        bufferedRecord = new C4Collection.Document(node, fileName, count);
        if (iterator.hasNext()) { // if bufferedReader contains JSON line objects, we parse the next JSON into node
          node = iterator.next();
          count++;
        } else {
          atEOF = true; // there is no more JSON object in the bufferedReader
        }
      }
    }
  }

  public static class Document implements SourceDocument {
    protected String id;
    private String contents;
    private String raw;
    private String url;
    private long timestamp;

    public Document(JsonNode json, String filename, int jsonLoc) {
      this.raw = json.toPrettyString();
      this.contents = json.get("text").asText();

      this.id = String.format("c4-%s-%06d", filename, jsonLoc);
      this.url = json.get("url").asText();

      String dateTime = json.get("timestamp").asText();
      Instant i = Instant.parse(dateTime);
      this.timestamp = i.getEpochSecond();
    }

    public String getUrl() {
      return url;
    }

    public long getTimestamp() {
      return timestamp;
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public String contents() {
      if (contents == null) {
        throw new RuntimeException("JSON document has no \"contents\" field");
      }
      return contents;
    }

    @Override
    public String raw() {
      return raw;
    }

    @Override
    public boolean indexable() {
      return true;
    }
  }
}