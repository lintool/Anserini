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

import java.util.HashMap;
import java.util.NoSuchElementException;

import org.junit.Before;
import org.junit.Test;


public class WashingtonPostDocumentTest extends DocumentTest {

  @Before
  public void setUp() throws Exception {
    super.setUp();

    String doc = "{\"id\": \"5f992bbc-4b9f-11e2-a6a6-aabac85e8036\", " +

                "\"article_url\": " +
                "\"https://www.washingtonpost.com/national/controlled-exposure-to" +
                "-light-can-ease-jet-lags-effects-before-and-after-a-trip/2012/12/24/" +
                "5f992bbc-4b9f-11e2-a6a6-aabac85e8036_story.html\", " +

                "\"title\": " +
                "\"Controlled exposure to light can ease jet lag’s effects before and after a trip\", " +

                "\"author\": \"\", " +

                "\"published_date\": 1356999181000, " +

                "\"contents\": " +
                    "[{\"content\": \"National\", \"mime\": \"text/plain\", \"type\": \"kicker\"}, " +
                    "{\"content\": \"Controlled exposure to light can ease jet lag’s effects before and after a trip\", " +
                        "\"mime\": \"text/plain\", \"type\": \"title\"}, " +
                    "{\"content\": \"Using light to help reset your body clock\", \"subtype\": \"subhead\", \"type\": \"sanitized_html\", \"mime\": \"text/plain\"}, " +
                    "{\"content\": \"When traveling east:\", \"subtype\": \"paragraph\", \"type\": \"sanitized_html\", \"mime\": \"text/plain\"}, " +
                    "{\"content\": \"A few days before you leave, start exposing yourself to bright light in the morning.\", " +
                        "\"subtype\": \"paragraph\", \"type\": \"sanitized_html\", \"mime\": \"text/plain\"}, " +
                    "{\"content\": \"When traveling west:\", \"subtype\": \"paragraph\", \"type\": \"sanitized_html\", \"mime\": \"text/plain\"}, " +
                    "{\"content\": \"When you arrive, expose yourself to light during the evening hours.\", \"subtype\": \"paragraph\", \"type\": \"tweet\", \"mime\": \"text/plain\"}], " +

                "\"type\": \"article\", \"source\": \"The Washington Post\"}";

    rawFiles.add(createFile(doc));

    HashMap<String, String> doc1 = new HashMap<>();
    doc1.put("id", "5f992bbc-4b9f-11e2-a6a6-aabac85e8036");
    // Only "sanitized_html" and "tweet" of <type> subtag in <content> tag will be included
    doc1.put("content", "Controlled exposure to light can ease jet lag’s effects before and after a trip\n" +
             "Using light to help reset your body clock\n" +
             "When traveling east:\n" +
             "A few days before you leave, start exposing yourself to bright light in the morning.\n" +
             "When traveling west:\n" +
             "When you arrive, expose yourself to light during the evening hours.\n");
    doc1.put("published_date", "1356999181000");

    expected.add(doc1);
  }

  @Test
  public void test() throws Exception {
    WashingtonPostCollection collection = new WashingtonPostCollection();
    for (int i = 0; i < rawFiles.size(); i++) {
      BaseFileSegment<WashingtonPostCollection.Document> iter = collection.createFileSegment(rawFiles.get(i));
      while (true) {
        boolean hasNext;
        try {
          hasNext = iter.hasNext();
        } catch (NoSuchElementException e) {
          break;
        }

        if (!hasNext) {
          break;
        }

        WashingtonPostCollection.Document parsed = iter.next();
        assertEquals(parsed.id(), expected.get(i).get("id"));
        assertEquals(parsed.content(), expected.get(i).get("content"));
        //assertEquals(parsed.getPublishedDate(), Long.parseLong(expected.get(i).get("published_date")));
      }
    }
  }
}
