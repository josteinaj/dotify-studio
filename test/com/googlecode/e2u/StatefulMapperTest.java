package com.googlecode.e2u;

import org.junit.Test;
import static org.junit.Assert.*;

public class StatefulMapperTest {

	@Test
	public void testMapper_01() {
		String actual = StatefulMapper.translate("bar", "abc", "ABC");
		assertEquals("BAr", actual);
	}
	
	@Test
	public void testMapper_02() {
		String actual = StatefulMapper.translate("--aaa--","abc-","ABC");
		assertEquals("AAA", actual);
	}

}
