package io.jenkins.plugins.octanesuitegatebyembiti.utils;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;

public class UtilTest {
  @Test
  public void splitsIdListsOnCommasAndWhitespace() {
    assertEquals(List.of("1196", "1200", "1201"), Util.splitIdList("1196, 1200\n1201 1200"));
  }
}
