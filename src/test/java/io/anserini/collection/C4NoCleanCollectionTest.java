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

import org.junit.Before;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class C4NoCleanCollectionTest extends DocumentCollectionTest<C4Collection.Document> {
  @Before
  public void setUp() throws Exception {
    super.setUp();
    collectionPath = Paths.get("src/test/resources/sample_docs/c4_noclean");
    collection = new C4NoCleanCollection(collectionPath);
    Path segment = Paths.get("src/test/resources/sample_docs/c4_noclean/c4-train.00001-of-01024.json.gz");
    Path segment2 = Paths.get("src/test/resources/sample_docs/c4_noclean/c4-train.00002-of-01024.json.gz");

    segmentPaths.add(segment);
    segmentPaths.add(segment2);

    segmentDocCounts.put(segment, 2);
    segmentDocCounts.put(segment2, 2);

    totalSegments = 2;
    totalDocs = 4;
    expected.put("en.noclean.c4-train.00001-of-01024.0", Map.of("id", "en.noclean.c4-train.00001-of-01024.0", "text", "test text", "timestamp", "1556008007", "url", "http://www.test.com"));
    expected.put("en.noclean.c4-train.00001-of-01024.1", Map.of("id", "en.noclean.c4-train.00001-of-01024.1", "text", "test text2", "timestamp", "1587630407", "url", "http://www.test2.com"));
    expected.put("en.noclean.c4-train.00002-of-01024.0", Map.of("id", "en.noclean.c4-train.00002-of-01024.0", "text", "test text-2", "timestamp", "1556008007", "url", "http://www.test-2.com"));
    expected.put("en.noclean.c4-train.00002-of-01024.1", Map.of("id", "en.noclean.c4-train.00002-of-01024.1", "text", "test text2-2", "timestamp", "1587630407", "url", "http://www.test2-2.com"));
  }

  @Override
  void checkDocument(SourceDocument doc, Map<String, String> expected) {
    assertTrue(doc.indexable());
    assertEquals(expected.get("id"), doc.id());
    assertEquals(expected.get("text"), doc.contents());
    assertEquals((long) Long.valueOf(expected.get("timestamp")), ((C4Collection.Document) doc).getTimestamp());
    assertEquals(expected.get("url"), ((C4Collection.Document) doc).getUrl());
  }
}