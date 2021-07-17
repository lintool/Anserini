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
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;


public class C4NoCleanCollection extends C4Collection {
  public C4NoCleanCollection(Path path) {
    super(path);
  }

  @Override
  public FileSegment<C4Collection.Document> createFileSegment(Path p) throws IOException {
    return new Segment(p);
  }

  public static class Segment extends C4Collection.Segment{

    public Segment(Path path) throws IOException {
      super(path);
      Pattern pattern = Pattern.compile("c4-train.\\d{5}-of-\\d{5}");
      Matcher matcher = pattern.matcher(filePath);
      if(matcher.find()){
        fileName = matcher.group();
      }
    }

    @Override
    public void readNext() throws NoSuchElementException {
      if (node == null) {
        throw new NoSuchElementException("JsonNode is empty");
      } else {
        bufferedRecord = new C4NoCleanCollection.Document(node, fileName, count);
        if (iterator.hasNext()) { // if bufferedReader contains JSON line objects, we parse the next JSON into node
          node = iterator.next();
          count++;
        } else {
          atEOF = true; // there is no more JSON object in the bufferedReader
        }
      }
    }
  }

  public static class Document extends C4Collection.Document {

    public Document(JsonNode json, String filename, int jsonLoc) {
      super(json, filename, jsonLoc);
      
      try {
        this.id = json.get("docno").asText();
      } catch(Exception e) { 
        this.id = String.format("en.noclean.%s.%d", filename, jsonLoc);
      }
    }
  }
}
