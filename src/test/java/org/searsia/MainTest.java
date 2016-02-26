package org.searsia;

import org.junit.Assert;
import org.junit.Test;

import org.searsia.Main;

public class MainTest {

	@Test
	public void test() {
		String[] args = {"--path=target/index-test/", "--log=4", "--exit", "--quiet"}; 
		Main.main(args);
		Assert.assertTrue(true); // happy if we get here!
	}
	
}
