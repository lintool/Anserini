/**
 * Anserini: An information retrieval toolkit built on Lucene
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

import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.HashMap;
import java.util.NoSuchElementException;

public class JsonDocumentObjectTest extends DocumentTest<JsonCollection.Document> {

  @Before
  public void setUp() throws Exception {
    super.setUp();

    String doc =
      "{\n " +
      "  \"id\": \"doc\",\n" +
      "  \"contents\": \"this is the contents.\"\n" +
      "}";

    rawFiles.add(createFile(doc));

    HashMap<String, String> doc1 = new HashMap<>();
    doc1.put("id", "doc");
    doc1.put("content", "this is the contents.");
    expected.add(doc1);
  }

  @Test
  public void test() throws IOException {
    JsonCollection collection = new JsonCollection();
    for (int i = 0; i < rawFiles.size(); i++) {
      AbstractFileSegment<JsonCollection.Document> iter = collection.createFileSegment(rawFiles.get(i));
      while (true) {
        try {
          JsonCollection.Document parsed = iter.next();
          assertEquals(parsed.id(), expected.get(i).get("id"));
          assertEquals(parsed.content(), expected.get(i).get("content"));
        } catch (NoSuchElementException e) {
          break;
        }
      }
    }
  }
}
