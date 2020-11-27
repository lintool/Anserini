/*
 * Anserini: A Lucene toolkit for replicable information retrieval research
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

package io.anserini.search.topicreader;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class WebxmlTopicReaderTest {

  @Test
  public void test() throws IOException {
    TopicReader<Integer> reader = new WebxmlTopicReader(
        Paths.get("src/main/resources/topics-and-qrels/topics.web.1-50.txt"));

    Map<Integer, Map<String, String>> topics = reader.read();
    Integer[] keys = (Integer[]) topics.keySet().toArray();
    Integer firstKey = keys[0];
    Integer lastKey = keys[keys.length - 1];
    
    assertEquals(50, topics.keySet().size());

    assertEquals(1, (int) firstKey);
    assertEquals("obama family tree", topics.get(firstKey).get("title").trim());

    assertEquals(50, (int) lastKey);
    assertEquals("dog heat", topics.get(lastKey).get("title").trim());
  }
}
