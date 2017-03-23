package org.searsia;

import org.junit.Assert;
import org.junit.Test;

import org.searsia.Main;

public class MainTest {

	@Test
	public void test() {
		String[] args = {"--path=target/index-test/", 
				         "--mother=http://searsia.org/searsia/wiki-informat-.json", 
				         "--log=4", "--test=json", "--quiet"}; 
		Main.main(args);
		Assert.assertTrue(true); // happy if we get here!
	}
	
}
