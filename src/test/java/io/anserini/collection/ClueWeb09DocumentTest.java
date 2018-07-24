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

import java.util.HashMap;
import java.util.NoSuchElementException;


public class ClueWeb09DocumentTest extends DocumentTest {

  @Before
  public void setUp() throws Exception {
    super.setUp();

    // WARC-Type: warcinfo is not indexable
    rawDocs.add(
        "WARC/0.18\n" +
        "WARC-Type: warcinfo\n" +
        "WARC-Date: 2009-03-65T08:43:19-0800\n" +
        "WARC-Record-ID: <urn:uuid:11111111-2222-3333-4444-555555555555>\n" +
        "Content-Type: application/warc-fields\n" +
        "Content-Length: 219\n" +
        "\n" +
        "software: Nutch 1.0-dev (modified for clueweb09)\n" +
        "isPartOf: clueweb09-en\n" +
        "description: clueweb09 crawl with WARC output\n" +
        "format: WARC file version 0.18\n" +
        "conformsTo: http://www.archive.org/documents/WarcFileFormat-0.18.html\n");

    rawDocs.add(
        "WARC/0.18\n" +
        "WARC-Type: response\n" +
        "WARC-Target-URI: http://clueweb09.test.com/\n" +
        "WARC-Warcinfo-ID: 993d3969-9643-4934-b1c6-68d4dbe55b83\n" +
        "WARC-Date: 2009-03-65T08:43:19-0800\n" +
        "WARC-Record-ID: <urn:uuid:6f12f095-18a8-4415-8f04-ec2477be81d5>\n" +
        "WARC-TREC-ID: clueweb09-az0000-00-00000\n" +
        "Content-Type: application/http;msgtype=response\n" +
        "WARC-Identified-Payload-Type: \n" +
        "Content-Length: 345\n" + // The Content-Length MUST match the length of the record!!!
        "\n" +
        "HTTP/1.1 200 OK\n" +
        "Content-Type: text/html\n" +
        "Date: Tue, 13 Jan 2009 18:05:10 GMT\n" +
        "Pragma: no-cache\n" +
        "Cache-Control: no-cache, must-revalidate\n" +
        "X-Powered-By: PHP/4.4.8\n" +
        "Server: WebServerX\n" +
        "Connection: close\n" +
        "Last-Modified: Tue, 13 Jan 2009 18:05:10 GMT\n" +
        "Expires: Mon, 20 Dec 1998 01:00:00 GMT\n" +
        "Content-Length: 49\n" +
        "\n" +
        "<html>\n" +
        "whatever here will be included\n" +
        "</html>\n");

    HashMap<String, String> doc3 = new HashMap<>();
    doc3.put("id", null);
    doc3.put("content", "software: Nutch 1.0-dev (modified for clueweb09)\n" +
        "isPartOf: clueweb09-en\n" +
        "description: clueweb09 crawl with WARC output\n" +
        "format: WARC file version 0.18\n" +
        "conformsTo: http://www.archive.org/documents/WarcFileFormat-0.18.html");
    expected.add(doc3);

    HashMap<String, String> doc4 = new HashMap<>();
    doc4.put("id", "clueweb09-az0000-00-00000");
    doc4.put("content", "\n<html>\n" +
        "whatever here will be included\n" +
        "</html>");
    expected.add(doc4);
  }

  @Test
  public void test() {
    ClueWeb09Collection collection = new ClueWeb09Collection();
    for (int i = 0; i < rawDocs.size(); i++) {
      AbstractFileSegment<ClueWeb09Collection.Document> iter = collection.createFileSegment(rawDocs.get(i));
      while (true) {
        try {
          ClueWeb09Collection.Document parsed = iter.next();
          assertEquals(parsed.id(), expected.get(i).get("id"));
          assertEquals(parsed.content(), expected.get(i).get("content"));
        } catch (NoSuchElementException e) {
          break;
        }
      }
    }
  }
}
