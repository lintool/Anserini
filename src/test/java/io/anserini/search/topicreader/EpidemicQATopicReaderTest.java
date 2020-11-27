package io.anserini.search.topicreader;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class EpidemicQATopicReaderTest {
  @Test
  public void test() throws IOException {
    TopicReader<Integer> consumerReader =
        new EpidemicQATopicReader(
            Paths.get("src/main/resources/topics-and-qrels/topics.epidemic-qa.consumer.prelim.json"));

    Map<Integer, Map<String, String>> consumerTopics = consumerReader.read();
    Integer[] consumerKeys = (Integer[]) consumerTopics.keySet().toArray();
    Integer consumerFirstKey = consumerKeys[0];
    Integer consumerLastKey = consumerKeys[consumerKeys.length - 1];

    // No consumer questions from CQ035 to CQ037
    assertEquals(42, consumerTopics.keySet().size());
    assertEquals(1, (int) consumerFirstKey);
    assertEquals("what is the origin of COVID-19",
                 consumerTopics.get(consumerFirstKey).get("question"));
    assertEquals("CQ001", consumerTopics.get(consumerFirstKey).get("question_id"));
    assertEquals("coronavirus origin", consumerTopics.get(consumerFirstKey).get("query"));
    assertEquals("seeking information about whether the virus was designed in a lab or occured " +
                 "naturally in animals and how it got to humans",
                 consumerTopics.get(consumerFirstKey).get("background"));

    assertEquals(45, (int) consumerLastKey);
    assertEquals("how has the COVID-19 pandemic impacted mental health?",
                 consumerTopics.get(consumerLastKey).get("question"));
    assertEquals("CQ045", consumerTopics.get(consumerLastKey).get("question_id"));
    assertEquals("coronavirus mental health impact",
                 consumerTopics.get(consumerLastKey).get("query"));
    assertEquals("seeking information about psychological effects of COVID-19 and "+
                 "COVID-19 effect on mental health and pre-existing conditions",
                 consumerTopics.get(consumerLastKey).get("background"));

    TopicReader<Integer> expertReader =
        new EpidemicQATopicReader(
            Paths.get("src/main/resources/topics-and-qrels/topics.epidemic-qa.expert.prelim.json"));

    Map<Integer, Map<String, String>> expertTopics = expertReader.read();
    Integer[] expertKeys = (Integer[]) expertTopics.keySet().toArray();
    Integer expertFirstKey = expertKeys[0];
    Integer expertLastKey = expertKeys[expertKeys.length - 1];

    assertEquals(45, expertTopics.keySet().size());

    assertEquals(1, (int) expertFirstKey);
    assertEquals("what is the origin of COVID-19",
                 expertTopics.get(expertFirstKey).get("question"));
    assertEquals("EQ001", expertTopics.get(expertFirstKey).get("question_id"));
    assertEquals("coronavirus origin", expertTopics.get(expertFirstKey).get("query"));
    assertEquals("seeking range of information about the SARS-CoV-2 virus's origin, " +
                 "including its evolution, animal source, and first transmission into humans",
                 expertTopics.get(expertFirstKey).get("background"));

    assertEquals(45, (int) expertLastKey);
    assertEquals("How has the COVID-19 pandemic impacted mental health?",
                 expertTopics.get(expertLastKey).get("question"));
    assertEquals("EQ045", expertTopics.get(expertLastKey).get("question_id"));
    assertEquals("coronavirus mental health impact",
                 expertTopics.get(expertLastKey).get("query"));
    assertEquals("Includes increasing/decreasing rates of depression, anxiety, panic disorder, " +
                 "and other psychiatric and mental health conditions.",
                 expertTopics.get(expertLastKey).get("background"));
  }
}
